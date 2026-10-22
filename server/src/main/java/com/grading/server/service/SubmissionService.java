package com.grading.server.service;

import com.grading.server.config.ServerConfig;
import com.grading.server.judge.JudgeQueue;
import com.grading.server.model.Submission;
import com.grading.server.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý logic nghiệp vụ cho Submission.
 *
 * Flow nhận bài nộp:
 *   1. Validate input
 *   2. Lưu DB với verdict = PENDING
 *   3. Đẩy vào JudgeQueue để worker xử lý bất đồng bộ
 *   4. Trả về submission (PENDING) cho client ngay lập tức
 *
 * Kết quả chấm bài sẽ được push về client qua JUDGE_NOTIFICATION
 * sau khi JudgeWorker hoàn thành.
 */
public class SubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionService.class);

    private static final int SUPPORTED_LANGUAGES_COUNT = 4;

    private final SubmissionRepository submissionRepository;
    private final JudgeQueue judgeQueue;

    public SubmissionService(SubmissionRepository submissionRepository, JudgeQueue judgeQueue) {
        this.submissionRepository = submissionRepository;
        this.judgeQueue = judgeQueue;
    }

    /**
     * Nhận bài nộp từ client, lưu DB và đẩy vào queue chấm.
     *
     * @param userId     ID của user (từ JWT)
     * @param problemId  ID bài toán
     * @param sourceCode Code nguồn
     * @param language   Ngôn ngữ: "java" | "cpp" | "python"
     * @return Submission đã lưu DB (verdict = PENDING)
     * @throws IllegalArgumentException nếu input không hợp lệ
     * @throws IllegalStateException    nếu queue đầy
     * @throws SQLException             nếu lỗi DB
     */
    public Submission submit(int userId, int problemId, String sourceCode, String language)
            throws IllegalArgumentException, IllegalStateException, SQLException {

        // Validate
        validateLanguage(language);
        validateSourceCode(sourceCode);

        // Lưu submission vào DB
        Submission submission = new Submission(userId, problemId, sourceCode, language.toLowerCase());
        submission = submissionRepository.save(submission);

        // Đánh dấu thời điểm vào queue (dùng để tính queue waiting time)
        submission.setQueuedAt(System.currentTimeMillis());

        // Đẩy vào JudgeQueue — non-blocking, reject nếu queue đầy
        boolean enqueued = judgeQueue.offer(submission);
        if (!enqueued) {
            // Queue đầy — trả lỗi cho client nhưng submission đã được lưu DB
            logger.warn("JudgeQueue is full! Submission id={} rejected from queue", submission.getId());
            throw new IllegalStateException("Server đang bận, vui lòng thử lại sau");
        }

        logger.info("Submission id={} enqueued (userId={}, problemId={}, lang={})",
                submission.getId(), userId, problemId, language);

        return submission;
    }

    /**
     * Lấy kết quả của 1 submission cụ thể.
     * Client dùng GET_RESULT để poll (hoặc sau khi nhận JUDGE_NOTIFICATION).
     *
     * @throws IllegalArgumentException nếu không tìm thấy hoặc không có quyền
     */
    public Submission getResult(int submissionId, int requestingUserId)
            throws IllegalArgumentException, SQLException {

        Optional<Submission> opt = submissionRepository.findById(submissionId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Submission id=" + submissionId + " không tồn tại");
        }

        Submission submission = opt.get();
        // Kiểm tra quyền — chỉ owner mới xem được
        if (submission.getUserId() != requestingUserId) {
            throw new IllegalArgumentException("Bạn không có quyền xem submission này");
        }

        return submission;
    }

    /**
     * Lấy lịch sử nộp bài của user.
     * Dùng cho GET_HISTORY response.
     */
    public List<Submission> getHistory(int userId) throws SQLException {
        return submissionRepository.findByUserId(userId);
    }

    /**
     * Lấy lịch sử nộp bài của user cho 1 bài cụ thể.
     */
    public List<Submission> getHistoryForProblem(int userId, int problemId) throws SQLException {
        return submissionRepository.findByUserIdAndProblemId(userId, problemId);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private void validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Ngôn ngữ lập trình không được để trống");
        }
        if (!language.equalsIgnoreCase("java")
                && !language.equalsIgnoreCase("cpp")
                && !language.equalsIgnoreCase("c")
                && !language.equalsIgnoreCase("python")) {
            throw new IllegalArgumentException("Ngôn ngữ không được hỗ trợ: " + language
                    + ". Chỉ hỗ trợ: java, cpp, c, python");
        }
    }

    private void validateSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code không được để trống");
        }
        if (sourceCode.length() > 100_000) { // 100KB limit
            throw new IllegalArgumentException("Source code vượt quá giới hạn 100KB");
        }
    }
}
