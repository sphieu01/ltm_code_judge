package com.grading.server.judge;

import com.grading.server.judge.model.CompileResult;
import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.RunResult;
import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker thread lấy bài từ JudgeQueue, độc lập biên dịch và thực thi test cases bằng SandboxRunner.
 */
public class JudgeWorker implements Runnable {

    private final int workerId;
    private final JudgeQueue queue;
    private final SandboxRunner runner;
    private final OnJudgeCompletedListener globalListener;

    private volatile boolean running = true;
    private volatile boolean busy = false;

    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicLong totalBusyTimeMs = new AtomicLong(0);

    public JudgeWorker(int workerId, JudgeQueue queue, SandboxRunner runner, OnJudgeCompletedListener globalListener) {
        this.workerId = workerId;
        this.queue = queue;
        this.runner = runner;
        this.globalListener = globalListener;
    }

    @Override
    public void run() {
        while (running) {
            SubmissionTask task;
            try {
                // Thử lấy bài nộp trong 500ms để kiểm tra cờ running định kỳ
                task = queue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
                continue;
            }

            busy = true;
            long startJudge = System.currentTimeMillis();
            try {
                JudgeResult result = judge(task);
                triggerCallbacks(task, result);
            } catch (Throwable t) {
                JudgeResult errorResult = new JudgeResult(
                        VerdictType.RE,
                        0,
                        0,
                        task.getTestCases().size(),
                        0,
                        "Lỗi nội bộ JudgeWorker: " + t.getMessage()
                );
                triggerCallbacks(task, errorResult);
            } finally {
                busy = false;
                totalBusyTimeMs.addAndGet(System.currentTimeMillis() - startJudge);
                completedCount.incrementAndGet();
            }
        }
    }

    /**
     * Quy trình chấm 1 bài nộp: Compile -> Run 100% test cases -> Tổng kết kết quả.
     */
    public JudgeResult judge(SubmissionTask task) {
        Path sandboxDir = Paths.get("temp", "sandbox", "sub_" + task.getSubmissionId() + "_w" + workerId + "_" + System.currentTimeMillis());

        try {
            String sourceCode = task.getSourceCode();
            String className = SandboxRunner.extractClassName(sourceCode);

            // Bước 1: Biên dịch mã nguồn Java
            CompileResult compileResult = runner.compile(sandboxDir, className, sourceCode);
            if (!compileResult.isSuccess()) {
                return JudgeResult.compileError(compileResult.getErrorMsg());
            }

            // Bước 2: Chạy đầy đủ 100% test cases (Chế độ 1 - Chấm hết để tính điểm phần trăm)
            int totalTests = task.getTestCases().size();
            int passedCount = 0;
            long maxTimeMs = 0;
            VerdictType finalVerdict = VerdictType.AC;

            for (TestCase tc : task.getTestCases()) {
                RunResult runResult = runner.run(sandboxDir, className, tc, task.getTimeLimitMs());
                long timeMs = runResult.getTimeMs();
                maxTimeMs = Math.max(maxTimeMs, timeMs);

                VerdictType v = runResult.getVerdict();
                if (v == VerdictType.AC) {
                    passedCount++;
                } else {
                    // Ghi nhận lỗi đầu tiên xuất hiện (WA, TLE, RE)
                    if (finalVerdict == VerdictType.AC) {
                        finalVerdict = v;
                    }
                }
            }

            int score = totalTests > 0 ? (int) Math.round((double) passedCount / totalTests * 100.0) : 0;
            return new JudgeResult(finalVerdict, score, passedCount, totalTests, maxTimeMs, null);

        } finally {
            runner.cleanup(sandboxDir);
        }
    }

    private void triggerCallbacks(SubmissionTask task, JudgeResult result) {
        // 1. Gọi listener riêng của bài nộp (nếu có)
        if (task.getCustomListener() != null) {
            try {
                task.getCustomListener().onCompleted(task, result);
            } catch (Throwable ignored) {
            }
        }

        // 2. Gọi listener toàn cục của Engine (nếu có)
        if (globalListener != null) {
            try {
                globalListener.onCompleted(task, result);
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        this.running = false;
    }

    public int getWorkerId() {
        return workerId;
    }

    public boolean isBusy() {
        return busy;
    }

    public int getCompletedCount() {
        return completedCount.get();
    }

    public long getTotalBusyTimeMs() {
        return totalBusyTimeMs.get();
    }
}
