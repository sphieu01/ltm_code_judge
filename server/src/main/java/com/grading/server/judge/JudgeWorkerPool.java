package com.grading.server.judge;

import com.grading.server.config.ServerConfig;
import com.grading.server.network.ConnectionRegistry;
import com.grading.server.repository.ProblemRepository;
import com.grading.server.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JudgeWorkerPool — Quản lý pool các JudgeWorker threads.
 *
 * Khởi động N worker threads (theo JUDGE_WORKER_COUNT trong config).
 * Mỗi worker tự lấy submission từ JudgeQueue và chấm song song.
 *
 * Pattern: Thread Pool + Blocking Queue (Producer-Consumer)
 *   Producer: ClientHandler → SubmissionService → JudgeQueue
 *   Consumer: JudgeWorker × N
 *
 * Đây là phần thể hiện rõ nhất **Concurrency Design** theo yêu cầu đề bài.
 */
public class JudgeWorkerPool {

    private static final Logger logger = LoggerFactory.getLogger(JudgeWorkerPool.class);

    private final ExecutorService executor;
    private final List<JudgeWorker> workers;
    private final AtomicBoolean running;
    private final long startTime;

    public JudgeWorkerPool(JudgeQueue judgeQueue,
                           SubmissionRepository submissionRepository,
                           ProblemRepository problemRepository,
                           ConnectionRegistry connectionRegistry) {

        int workerCount = ServerConfig.JUDGE_WORKER_COUNT;
        this.executor = Executors.newFixedThreadPool(workerCount);
        this.workers = new ArrayList<>(workerCount);
        this.running = new AtomicBoolean(true);
        this.startTime = System.currentTimeMillis();

        // Khởi tạo và submit workers
        for (int i = 1; i <= workerCount; i++) {
            JudgeWorker worker = new JudgeWorker(
                    i,
                    judgeQueue,
                    submissionRepository,
                    problemRepository,
                    connectionRegistry,
                    running
            );
            workers.add(worker);
            executor.submit(worker);
        }

        logger.info("JudgeWorkerPool started with {} workers", workerCount);
    }

    /**
     * Dừng tất cả workers và pool.
     * Gọi khi server shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down JudgeWorkerPool...");
        running.set(false);
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.warn("JudgeWorkerPool forced shutdown after timeout");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        printMetrics();
        logger.info("JudgeWorkerPool shutdown complete");
    }

    /** In metrics của tất cả workers — dùng cho Experiment */
    public void printMetrics() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long totalJudged = workers.stream().mapToLong(JudgeWorker::getTotalJudged).sum();

        logger.info("=== JudgeWorkerPool Metrics ===");
        logger.info("Uptime: {}ms, Total judged: {}", uptimeMs, totalJudged);
        logger.info("Throughput: {:.2f} submissions/min",
                totalJudged > 0 ? (double) totalJudged / uptimeMs * 60_000 : 0.0);

        for (JudgeWorker w : workers) {
            logger.info("  Worker-{}: judged={}, busyTime={}ms, utilization={:.1f}%",
                    w.getWorkerId(), w.getTotalJudged(), w.getTotalBusyTimeMs(),
                    w.getUtilization(uptimeMs));
        }
        logger.info("================================");
    }

    public boolean isRunning() {
        return running.get() && !executor.isShutdown();
    }
}
