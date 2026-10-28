package com.grading.server.judge;

import com.grading.server.config.ServerConfig;
import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * JudgeQueue — wrapper cho LinkedBlockingQueue<Submission>.
 *
 * Đây là điểm trung tâm của Submission Pipeline:
 *   ClientHandler → [submit] → JudgeQueue → [take] → JudgeWorker
 *
 * Thread-safety: LinkedBlockingQueue là thread-safe hoàn toàn.
 * - Nhiều ClientHandler threads có thể offer() đồng thời
 * - Nhiều JudgeWorker threads có thể take() đồng thời
 *
 * Capacity: giới hạn theo JUDGE_QUEUE_CAPACITY để tránh OOM.
 * Khi queue đầy, offer() sẽ trả false (không block) → server báo lỗi cho client.
 */
public class JudgeQueue {

    private static final Logger logger = LoggerFactory.getLogger(JudgeQueue.class);

    private final LinkedBlockingQueue<Submission> queue;

    public JudgeQueue() {
        this.queue = new LinkedBlockingQueue<>(ServerConfig.JUDGE_QUEUE_CAPACITY);
        logger.info("JudgeQueue initialized with capacity={}", ServerConfig.JUDGE_QUEUE_CAPACITY);
    }

    /**
     * Thêm submission vào queue (non-blocking).
     *
     * @return true nếu thêm thành công, false nếu queue đầy
     */
    public boolean offer(Submission submission) {
        boolean accepted = queue.offer(submission);
        if (accepted) {
            logger.debug("Submission id={} added to queue. Queue size: {}",
                    submission.getId(), queue.size());
        } else {
            logger.warn("Queue is full ({} items). Submission id={} rejected.",
                    queue.size(), submission.getId());
        }
        return accepted;
    }

    /**
     * Lấy submission từ queue (blocking).
     * Worker gọi method này, sẽ block cho đến khi có item.
     *
     * @return Submission tiếp theo cần chấm
     * @throws InterruptedException nếu thread bị interrupt (server đang shutdown)
     */
    public Submission take() throws InterruptedException {
        Submission submission = queue.take();
        logger.debug("Submission id={} taken from queue. Remaining: {}",
                submission.getId(), queue.size());
        return submission;
    }

    /**
     * Lấy submission từ queue với timeout.
     * Dùng khi muốn worker có thể check shutdown signal định kỳ.
     *
     * @param timeout  thời gian chờ
     * @param unit     đơn vị thời gian
     * @return Submission hoặc null nếu timeout
     */
    public Submission poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /** Số submission đang chờ trong queue */
    public int size() {
        return queue.size();
    }

    /** Queue có rỗng không */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Queue còn trống không (chưa đầy) */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    // ─── SubmissionTask overloads (dùng bởi JudgeEngine của hoangdev) ─────────

    /**
     * Push SubmissionTask vào queue — dùng bởi JudgeEngine demo mode.
     * @throws InterruptedException nếu bị interrupt khi queue đầy
     */
    public void push(SubmissionTask task) throws InterruptedException {
        queue.put(wrapTask(task));
        logger.debug("SubmissionTask id={} pushed to queue. Size: {}", task.getSubmissionId(), queue.size());
    }

    /** Poll SubmissionTask với timeout — dùng bởi JudgeWorker standalone mode */
    public SubmissionTask pollTask(long timeout, TimeUnit unit) throws InterruptedException {
        Submission s = queue.poll(timeout, unit);
        return s == null ? null : unwrapSubmission(s);
    }

    /** Wrap SubmissionTask thành Submission tạm để dùng chung queue */
    private Submission wrapTask(SubmissionTask task) {
        Submission s = new Submission();
        s.setId((int) task.getSubmissionId());
        s.setProblemId(task.getProblemId());
        s.setSourceCode(task.getSourceCode());
        s.setLanguage("java");
        s.setVerdict(Submission.VERDICT_PENDING);
        s.setQueuedAt(task.getSubmittedAt());
        if (task.getTestCases() != null) {
            java.util.List<com.grading.server.model.TestCase> modelCases = new java.util.ArrayList<>();
            for (com.grading.server.judge.model.TestCase tc : task.getTestCases()) {
                com.grading.server.model.TestCase mc = new com.grading.server.model.TestCase();
                mc.setId(tc.getId());
                mc.setInputData(tc.getInput());
                mc.setExpectedOutput(tc.getExpectedOutput());
                modelCases.add(mc);
            }
            s.setTestCases(modelCases);
        }
        s.setStandaloneTask(task);
        return s;
    }

    /** Unwrap Submission thành SubmissionTask (approximation) */
    private SubmissionTask unwrapSubmission(Submission s) {
        return new SubmissionTask(s.getId(), s.getProblemId(), s.getSourceCode(),
                java.util.Collections.emptyList());
    }
}
