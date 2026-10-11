package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository xử lý CRUD cho bảng `submissions`.
 */
public class SubmissionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionRepository.class);

    // ─── SQL ──────────────────────────────────────────────────────────────────
    private static final String SQL_INSERT =
            "INSERT INTO submissions (user_id, problem_id, source_code, language, verdict) " +
            "VALUES (?, ?, ?, ?, 'PENDING'::verdict_type) RETURNING id, submitted_at";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, user_id, problem_id, source_code, language, verdict, score, " +
            "passed_tests, total_tests, exec_time_ms, memory_used_kb, compile_output, " +
            "submitted_at, judged_at FROM submissions WHERE id = ?";

    private static final String SQL_FIND_BY_USER =
            "SELECT id, user_id, problem_id, source_code, language, verdict, score, " +
            "passed_tests, total_tests, exec_time_ms, memory_used_kb, compile_output, " +
            "submitted_at, judged_at FROM submissions WHERE user_id = ? ORDER BY submitted_at DESC";

    private static final String SQL_FIND_BY_USER_AND_PROBLEM =
            "SELECT id, user_id, problem_id, source_code, language, verdict, score, " +
            "passed_tests, total_tests, exec_time_ms, memory_used_kb, compile_output, " +
            "submitted_at, judged_at FROM submissions " +
            "WHERE user_id = ? AND problem_id = ? ORDER BY submitted_at DESC";

    private static final String SQL_UPDATE_VERDICT =
            "UPDATE submissions SET verdict = ?::verdict_type, score = ?, " +
            "passed_tests = ?, total_tests = ?, exec_time_ms = ?, memory_used_kb = ?, " +
            "compile_output = ?, judged_at = CURRENT_TIMESTAMP " +
            "WHERE id = ?";

    private static final String SQL_UPDATE_STATUS_JUDGING =
            "UPDATE submissions SET verdict = 'JUDGING'::verdict_type WHERE id = ?";

    // ─── Public Methods ───────────────────────────────────────────────────────

    /**
     * Lưu submission mới vào DB.
     * Verdict mặc định = PENDING.
     *
     * @return submission đã có id và submittedAt
     */
    public Submission save(Submission submission) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {

            stmt.setInt(1, submission.getUserId());
            stmt.setInt(2, submission.getProblemId());
            stmt.setString(3, submission.getSourceCode());
            stmt.setString(4, submission.getLanguage());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    submission.setId(rs.getInt("id"));
                    Timestamp submittedAt = rs.getTimestamp("submitted_at");
                    if (submittedAt != null) {
                        submission.setSubmittedAt(submittedAt.toString());
                    }
                    logger.info("Saved submission id={} for userId={}, problemId={}",
                            submission.getId(), submission.getUserId(), submission.getProblemId());
                    return submission;
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving submission userId={}, problemId={}",
                    submission.getUserId(), submission.getProblemId(), e);
            throw e;
        }
        throw new SQLException("Failed to save submission, no ID returned");
    }

    /**
     * Tìm submission theo ID.
     */
    public Optional<Submission> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding submission id={}", id, e);
            throw e;
        }
        return Optional.empty();
    }

    /**
     * Lấy lịch sử nộp bài của 1 user (tất cả bài).
     */
    public List<Submission> findByUserId(int userId) throws SQLException {
        List<Submission> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USER)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching submissions for userId={}", userId, e);
            throw e;
        }
        return list;
    }

    /**
     * Lấy lịch sử nộp bài của 1 user cho 1 bài cụ thể.
     */
    public List<Submission> findByUserIdAndProblemId(int userId, int problemId) throws SQLException {
        List<Submission> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USER_AND_PROBLEM)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching submissions for userId={}, problemId={}", userId, problemId, e);
            throw e;
        }
        return list;
    }

    /**
     * Cập nhật trạng thái sang JUDGING khi worker bắt đầu xử lý.
     */
    public void markAsJudging(int submissionId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS_JUDGING)) {

            stmt.setInt(1, submissionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error marking submission id={} as JUDGING", submissionId, e);
            throw e;
        }
    }

    /**
     * Cập nhật kết quả chấm bài.
     * Được gọi bởi JudgeWorker sau khi chấm xong.
     */
    public void updateVerdict(Submission submission) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VERDICT)) {

            stmt.setString(1, submission.getVerdict());
            stmt.setInt(2, submission.getScore());
            stmt.setInt(3, submission.getPassedTests());
            stmt.setInt(4, submission.getTotalTests());
            stmt.setInt(5, submission.getExecTimeMs());
            stmt.setInt(6, submission.getMemoryUsedKb());
            stmt.setString(7, submission.getCompileOutput());
            stmt.setInt(8, submission.getId());

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                logger.warn("No submission found to update verdict for id={}", submission.getId());
            } else {
                logger.info("Updated verdict for submission id={}: {} (score={})",
                        submission.getId(), submission.getVerdict(), submission.getScore());
            }
        } catch (SQLException e) {
            if (Submission.VERDICT_SV.equals(submission.getVerdict())) {
                // Nếu DB enum trên remote chưa chạy migration SECURITY_VIOLATION, lưu tạm dưới dạng RE
                try (Connection conn = DatabaseConfig.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VERDICT)) {
                    stmt.setString(1, Submission.VERDICT_RE);
                    stmt.setInt(2, 0);
                    stmt.setInt(3, 0);
                    stmt.setInt(4, submission.getTotalTests());
                    stmt.setInt(5, 0);
                    stmt.setInt(6, 0);
                    stmt.setString(7, "[SECURITY_VIOLATION] " + submission.getCompileOutput());
                    stmt.setInt(8, submission.getId());
                    stmt.executeUpdate();
                    logger.warn("Saved SECURITY_VIOLATION as RE fallback for submission id={}", submission.getId());
                    return;
                } catch (SQLException ignored) {}
            }
            logger.error("Error updating verdict for submission id={}", submission.getId(), e);
            throw e;
        }
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private Submission mapRow(ResultSet rs) throws SQLException {
        Submission s = new Submission();
        s.setId(rs.getInt("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setProblemId(rs.getInt("problem_id"));
        s.setSourceCode(rs.getString("source_code"));
        s.setLanguage(rs.getString("language"));
        s.setVerdict(rs.getString("verdict"));
        s.setScore(rs.getInt("score"));
        s.setPassedTests(rs.getInt("passed_tests"));
        s.setTotalTests(rs.getInt("total_tests"));
        s.setExecTimeMs(rs.getInt("exec_time_ms"));
        s.setMemoryUsedKb(rs.getInt("memory_used_kb"));
        s.setCompileOutput(rs.getString("compile_output"));

        Timestamp submittedAt = rs.getTimestamp("submitted_at");
        if (submittedAt != null) s.setSubmittedAt(submittedAt.toString());

        Timestamp judgedAt = rs.getTimestamp("judged_at");
        if (judgedAt != null) s.setJudgedAt(judgedAt.toString());

        return s;
    }
}
