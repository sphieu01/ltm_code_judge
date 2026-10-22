package com.grading.server;

import com.grading.server.judge.JudgeQueue;
import com.grading.server.model.Submission;
import com.grading.server.network.ConnectionRegistry;
import com.grading.server.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * MockJudgeWorker — Dùng để thành viên B test server mà không cần C viết xong.
 *
 * Thay thế JudgeWorkerPool trong môi trường development:
 *   Luôn trả AC sau 2 giây — đúng như spec trong task-division.md
 *
 * Cách dùng:
 *   Trong ServerApp, thay JudgeWorkerPool bằng MockJudgeWorker khi dev.
 */
public class MockJudgeWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MockJudgeWorker.class);

    private final JudgeQueue judgeQueue;
    private final SubmissionRepository submissionRepository;
    private final ConnectionRegistry connectionRegistry;
    private volatile boolean running = true;

    public MockJudgeWorker(JudgeQueue judgeQueue,
                           SubmissionRepository submissionRepository,
                           ConnectionRegistry connectionRegistry) {
        this.judgeQueue = judgeQueue;
        this.submissionRepository = submissionRepository;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void run() {
        logger.info("[MOCK] MockJudgeWorker started — always returns AC after 2s");

        while (running) {
            try {
                Submission submission = judgeQueue.take();

                // Simulate judging delay
                Thread.sleep(2000);

                // Mock verdict: AC, score 100
                submission.setVerdict(Submission.VERDICT_AC);
                submission.setScore(100);
                submission.setPassedTests(3);       // giả sử 3 test cases
                submission.setTotalTests(3);
                submission.setExecTimeMs(150);
                submission.setMemoryUsedKb(2048);

                // Cập nhật DB
                submissionRepository.updateVerdict(submission);

                // Push notification
                connectionRegistry.sendJudgeNotification(submission);

                logger.info("[MOCK] Submission id={} → AC (mock)", submission.getId());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (SQLException e) {
                logger.error("[MOCK] DB error", e);
            }
        }
    }

    public void stop() {
        running = false;
    }
}
