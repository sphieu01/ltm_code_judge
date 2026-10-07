package com.grading.server.judge;

import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.judge.model.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Facade Manager quản lý hệ thống chấm code tự động:
 * Điều phối JudgeQueue, Worker Pool song song, và kích hoạt Callback thông báo kết quả.
 */
public class JudgeEngine {

    public static final int DEFAULT_WORKER_COUNT = 4;

    private final int workerCount;
    private final JudgeQueue queue;
    private final SandboxRunner runner;
    private final List<JudgeWorker> workers;
    private final List<Thread> workerThreads;

    private volatile OnJudgeCompletedListener globalListener;
    private volatile boolean started = false;

    public JudgeEngine() {
        this(DEFAULT_WORKER_COUNT, null);
    }

    public JudgeEngine(int workerCount) {
        this(workerCount, null);
    }

    public JudgeEngine(int workerCount, OnJudgeCompletedListener globalListener) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Số lượng worker phải lớn hơn 0");
        }
        this.workerCount = workerCount;
        this.globalListener = globalListener;
        this.queue = new JudgeQueue();
        this.runner = new SandboxRunner();
        this.workers = new ArrayList<>(workerCount);
        this.workerThreads = new ArrayList<>(workerCount);
    }

    /**
     * Khởi động các worker thread trong pool.
     */
    public synchronized void start() {
        if (started) {
            return;
        }

        workers.clear();
        workerThreads.clear();

        for (int i = 1; i <= workerCount; i++) {
            JudgeWorker worker = new JudgeWorker(i, queue, runner, (task, result) -> {
                if (globalListener != null) {
                    globalListener.onCompleted(task, result);
                }
            });
            Thread thread = new Thread(worker, "JudgeWorker-" + i);
            thread.setDaemon(true); // Thread daemon để không chặn tắt ứng dụng

            workers.add(worker);
            workerThreads.add(thread);
            thread.start();
        }

        started = true;
    }

    /**
     * Đẩy bài nộp vào hàng đợi chấm bài.
     */
    public void submit(SubmissionTask task) throws InterruptedException {
        if (!started) {
            throw new IllegalStateException("JudgeEngine chưa được khởi động. Hãy gọi start() trước khi submit!");
        }
        queue.push(task);
    }

    /**
     * Phương thức tiện ích để đẩy bài nộp nhanh.
     */
    public void submit(long submissionId, int problemId, String sourceCode, List<TestCase> testCases) throws InterruptedException {
        submit(new SubmissionTask(submissionId, problemId, sourceCode, testCases));
    }

    /**
     * Đăng ký hoặc thay đổi callback lắng nghe kết quả toàn cục.
     */
    public void setOnJudgeCompletedListener(OnJudgeCompletedListener listener) {
        this.globalListener = listener;
    }

    /**
     * Dừng các worker threads một cách an toàn (chờ xử lý nốt bài hiện tại).
     */
    public synchronized void shutdown() {
        if (!started) {
            return;
        }
        for (JudgeWorker worker : workers) {
            worker.stop();
        }
        for (Thread thread : workerThreads) {
            try {
                thread.join(2000);
            } catch (InterruptedException ignored) {
            }
        }
        started = false;
    }

    /**
     * Ép dừng ngay lập tức các worker threads.
     */
    public synchronized void shutdownNow() {
        if (!started) {
            return;
        }
        for (JudgeWorker worker : workers) {
            worker.stop();
        }
        for (Thread thread : workerThreads) {
            thread.interrupt();
        }
        started = false;
    }

    /**
     * Số lượng bài nộp còn đang chờ trong hàng đợi.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Số lượng worker hiện đang bận chấm bài.
     */
    public int getActiveWorkerCount() {
        int count = 0;
        for (JudgeWorker w : workers) {
            if (w.isBusy()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tổng số worker trong pool.
     */
    public int getWorkerCount() {
        return workerCount;
    }

    /**
     * Kiểm tra toàn bộ engine có đang bận xử lý hay không.
     */
    public boolean isBusy() {
        return getQueueSize() > 0 || getActiveWorkerCount() > 0;
    }

    /**
     * Danh sách worker để phục vụ trích xuất số liệu thực nghiệm.
     */
    public List<JudgeWorker> getWorkers() {
        return Collections.unmodifiableList(workers);
    }
}
