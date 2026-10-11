package com.grading.server.judge;

import com.grading.server.model.Submission;
import com.grading.server.model.TestCase;
import com.grading.server.network.ConnectionRegistry;
import com.grading.server.repository.ProblemRepository;
import com.grading.server.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JudgeWorker — Thread lấy Submission từ JudgeQueue và chấm bài.
 *
 * Mỗi JudgeWorker là 1 Runnable chạy trong thread riêng (do JudgeWorkerPool quản lý).
 * Vòng lặp chính:
 *   1. Poll queue (với timeout 2s để có thể check shutdown signal)
 *   2. Đánh dấu submission là JUDGING trong DB
 *   3. Lấy test cases từ DB
 *   4. Gọi SandboxRunner để chấm từng test case
 *   5. Tính điểm, cập nhật DB
 *   6. Push JUDGE_NOTIFICATION về client qua ConnectionRegistry
 *
 * Metrics được thu thập để phục vụ Experiment:
 *   - Queue waiting time
 *   - Judge time per submission
 *   - Worker utilization
 */
public class JudgeWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(JudgeWorker.class);

    private final int workerId;
    private final JudgeQueue judgeQueue;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SandboxRunner sandboxRunner;
    private final OutputComparator outputComparator;
    private final ConnectionRegistry connectionRegistry;

    /** Signal để dừng worker khi server shutdown */
    private final AtomicBoolean running;

    /** Callback standalone (chỉ dùng khi không có DB — JudgeEngine demo mode) */
    private OnJudgeCompletedListener _standaloneListener;

    // ─── Metrics ──────────────────────────────────────────────────────────────
    private final AtomicLong totalJudged = new AtomicLong(0);
    private final AtomicLong totalBusyTimeMs = new AtomicLong(0);

    public JudgeWorker(int workerId,
                       JudgeQueue judgeQueue,
                       SubmissionRepository submissionRepository,
                       ProblemRepository problemRepository,
                       ConnectionRegistry connectionRegistry,
                       AtomicBoolean running) {
        this.workerId = workerId;
        this.judgeQueue = judgeQueue;
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.sandboxRunner = new SandboxRunner();
        this.outputComparator = new OutputComparator();
        this.connectionRegistry = connectionRegistry;
        this.running = running;
    }

    /**
     * Constructor standalone cho JudgeEngine (demo/test, không dùng DB).
     * Dùng bởi JudgeEngine của hoangdev.
     */
    public JudgeWorker(int workerId,
                       JudgeQueue judgeQueue,
                       SandboxRunner sandboxRunner,
                       OnJudgeCompletedListener listener) {
        this.workerId = workerId;
        this.judgeQueue = judgeQueue;
        this.submissionRepository = null;
        this.problemRepository = null;
        this.sandboxRunner = sandboxRunner != null ? sandboxRunner : new SandboxRunner();
        this.outputComparator = new OutputComparator();
        this.connectionRegistry = null;
        this.running = new AtomicBoolean(true);
        this._standaloneListener = listener;
    }

    @Override
    public void run() {
        logger.info("JudgeWorker-{} started", workerId);

        while (running.get()) {
            try {
                // Poll với timeout 2s → có thể check running flag định kỳ
                Submission submission = judgeQueue.poll(2, TimeUnit.SECONDS);

                if (submission == null) {
                    continue; // timeout, check running flag lại
                }

                // Đo thời gian worker busy
                long startMs = System.currentTimeMillis();
                judgeSubmission(submission);
                long elapsedMs = System.currentTimeMillis() - startMs;

                totalJudged.incrementAndGet();
                totalBusyTimeMs.addAndGet(elapsedMs);

                logger.debug("Worker-{} judged submission id={} in {}ms",
                        workerId, submission.getId(), elapsedMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("JudgeWorker-{} interrupted, stopping", workerId);
                break;
            } catch (Exception e) {
                logger.error("JudgeWorker-{} unexpected error", workerId, e);
                // Không break — tiếp tục xử lý các submission khác
            }
        }

        logger.info("JudgeWorker-{} stopped. Total judged: {}, Total busy time: {}ms",
                workerId, totalJudged.get(), totalBusyTimeMs.get());
    }

    // ─── Core judging logic ───────────────────────────────────────────────────

    private void judgeSubmission(Submission submission) {
        long queueWaitMs = submission.getQueueWaitMs();
        boolean isDocker = com.grading.server.judge.docker.DockerSandboxRunner.isDockerAvailable();
        String sandboxModeStr = isDocker ? "DOCKER_CONTAINER" : "LOCAL_PROCESS_SANDBOX";
        logger.info("Worker-{} start judging submission id={} (lang={}) [Sandbox: {}] (queued {}ms ago)",
                workerId, submission.getId(), submission.getLanguage(), sandboxModeStr, queueWaitMs);

        try {
            // 1. Cập nhật trạng thái JUDGING trong DB
            submissionRepository.markAsJudging(submission.getId());
            submission.setVerdict(Submission.VERDICT_JUDGING);

            // 1.5. Quét bảo mật tĩnh (SecurityScanner - Tầng 1)
            com.grading.server.judge.security.SecurityScanner.SecurityResult secResult =
                    com.grading.server.judge.security.SecurityScanner.scan(
                            submission.getSourceCode(),
                            submission.getLanguage()
                    );
            if (!secResult.isSafe()) {
                logger.warn("Worker-{} rejected submission id={} due to security violation: {}",
                        workerId, submission.getId(), secResult.reason());
                submission.setVerdict(Submission.VERDICT_SV);
                submission.setScore(0);
                submission.setPassedTests(0);
                submission.setCompileOutput(secResult.reason());
                finalizeSubmission(submission);
                return;
            }

            // 2. Lấy tất cả test cases
            List<TestCase> testCases = problemRepository.findAllTestCases(submission.getProblemId());
            if (testCases.isEmpty()) {
                // Không có test case — trả AC (edge case)
                submission.setVerdict(Submission.VERDICT_AC);
                submission.setScore(100);
                submission.setPassedTests(0);
                submission.setTotalTests(0);
                finalizeSubmission(submission);
                return;
            }

            submission.setTotalTests(testCases.size());

            // 3. Compile code
            SandboxRunner.CompileResult compileResult = sandboxRunner.compile(
                    submission.getId(),
                    submission.getSourceCode(),
                    submission.getLanguage()
            );

            if (!compileResult.isSuccess()) {
                // Compile Error
                submission.setVerdict(Submission.VERDICT_CE);
                submission.setScore(0);
                submission.setPassedTests(0);
                submission.setCompileOutput(truncate(compileResult.getErrorOutput(), 5000));
                finalizeSubmission(submission);
                return;
            }

            // 4. Chạy từng test case
            int passedTests = 0;
            int maxExecTimeMs = 0;
            int maxMemoryKb = 0;
            String finalVerdict = Submission.VERDICT_AC;

            int timeLimitMs = 2000;
            if (problemRepository != null) {
                try {
                    java.util.Optional<com.grading.server.model.Problem> probOpt = problemRepository.findById(submission.getProblemId());
                    if (probOpt.isPresent() && probOpt.get().getTimeLimitMs() > 0) {
                        timeLimitMs = probOpt.get().getTimeLimitMs();
                    }
                } catch (Exception ignored) {}
            }

            for (TestCase tc : testCases) {
                SandboxRunner.RunResult runResult = sandboxRunner.run(
                        submission.getId(),
                        submission.getLanguage(),
                        tc.getInputData(),
                        timeLimitMs
                );

                maxExecTimeMs = Math.max(maxExecTimeMs, runResult.getExecTimeMs());
                maxMemoryKb = Math.max(maxMemoryKb, runResult.getMemoryKb());

                if (runResult.getVerdict() != null) {
                    // TLE hoặc RE
                    finalVerdict = runResult.getVerdict();
                    break;
                }

                // So sánh output
                boolean correct = outputComparator.compare(
                        runResult.getStdout(),
                        tc.getExpectedOutput()
                );

                if (correct) {
                    passedTests++;
                } else {
                    finalVerdict = Submission.VERDICT_WA;
                    break; // Dừng khi gặp test case sai đầu tiên
                }
            }

            // 5. Tính điểm
            int score = (int) Math.round((double) passedTests / testCases.size() * 100);

            submission.setVerdict(finalVerdict);
            submission.setScore(score);
            submission.setPassedTests(passedTests);
            submission.setExecTimeMs(maxExecTimeMs);
            submission.setMemoryUsedKb(maxMemoryKb);

        } catch (Exception e) {
            logger.error("Error during judging submission id={}", submission.getId(), e);
            submission.setVerdict(Submission.VERDICT_RE);
            submission.setScore(0);
            submission.setCompileOutput("Internal error: " + e.getMessage());
        } finally {
            // 6. Cleanup sandbox files
            sandboxRunner.cleanup(submission.getId());
        }

        finalizeSubmission(submission);
    }

    private void finalizeSubmission(Submission submission) {
        try {
            // Cập nhật DB
            submissionRepository.updateVerdict(submission);

            // Push JUDGE_NOTIFICATION về client (nếu vẫn online)
            connectionRegistry.sendJudgeNotification(submission);

            logger.info("Submission id={} judged: verdict={}, score={}, passed={}/{}",
                    submission.getId(), submission.getVerdict(), submission.getScore(),
                    submission.getPassedTests(), submission.getTotalTests());

        } catch (SQLException e) {
            logger.error("Failed to update verdict for submission id={}", submission.getId(), e);
        }
    }

    /** Truncate chuỗi để không lưu quá dài vào DB */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...[truncated]";
    }

    // ─── Metrics getters ──────────────────────────────────────────────────────

    public int getWorkerId() { return workerId; }
    public long getTotalJudged() { return totalJudged.get(); }
    public long getTotalBusyTimeMs() { return totalBusyTimeMs.get(); }

    /**
     * Dừng worker an toàn — dùng bởi JudgeEngine.shutdown().
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Kiểm tra worker có đang chấm bài không — dùng bởi JudgeEngine.getActiveWorkerCount().
     */
    public boolean isBusy() {
        return totalJudged.get() > 0 && running.get();
    }

    /**
     * Tính worker utilization (%) — thời gian busy / tổng thời gian running.
     * @param totalUptimeMs tổng thời gian server đã chạy
     */
    public double getUtilization(long totalUptimeMs) {
        if (totalUptimeMs <= 0) return 0;
        return (double) totalBusyTimeMs.get() / totalUptimeMs * 100.0;
    }
}
