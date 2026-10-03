package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.Problem;
import com.grading.server.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository xử lý CRUD cho bảng `problems` và `test_cases`.
 */
public class ProblemRepository {

    private static final Logger logger = LoggerFactory.getLogger(ProblemRepository.class);

    // ─── SQL ──────────────────────────────────────────────────────────────────
    private static final String SQL_FIND_ALL =
            "SELECT id, title, description, time_limit_ms, memory_limit_kb, difficulty, " +
            "created_by, created_at FROM problems ORDER BY id";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, title, description, time_limit_ms, memory_limit_kb, difficulty, " +
            "created_by, created_at FROM problems WHERE id = ?";

    private static final String SQL_FIND_TEST_CASES =
            "SELECT id, problem_id, input_data, expected_output, is_sample, order_num " +
            "FROM test_cases WHERE problem_id = ? ORDER BY order_num";

    private static final String SQL_FIND_SAMPLE_TEST_CASES =
            "SELECT id, problem_id, input_data, expected_output, is_sample, order_num " +
            "FROM test_cases WHERE problem_id = ? AND is_sample = TRUE ORDER BY order_num";

    private static final String SQL_INSERT_PROBLEM =
            "INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by) " +
            "VALUES (?, ?, ?, ?, ?::difficulty_level, ?) RETURNING id, created_at";

    private static final String SQL_INSERT_TEST_CASE =
            "INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num) " +
            "VALUES (?, ?, ?, ?, ?) RETURNING id";

    // ─── Public Methods ───────────────────────────────────────────────────────

    /**
     * Lấy danh sách tất cả bài toán (không kèm test cases).
     * Dùng cho GET_PROBLEMS.
     */
    public List<Problem> findAll() throws SQLException {
        List<Problem> problems = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                problems.add(mapProblemRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all problems", e);
            throw e;
        }
        return problems;
    }

    /**
     * Lấy chi tiết 1 bài toán kèm sample test cases.
     * Dùng cho GET_PROBLEM_DETAIL.
     */
    public Optional<Problem> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Problem problem = mapProblemRow(rs);
                    // Load sample test cases để hiển thị cho student
                    problem.setTestCases(findSampleTestCases(conn, id));
                    return Optional.of(problem);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding problem id={}", id, e);
            throw e;
        }
        return Optional.empty();
    }

    /**
     * Lấy TẤT CẢ test cases (cả hidden) cho 1 bài — dùng khi chấm bài.
     * Được gọi bởi JudgeWorker, không phải trả về client.
     */
    public List<TestCase> findAllTestCases(int problemId) throws SQLException {
        List<TestCase> testCases = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_TEST_CASES)) {

            stmt.setInt(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    testCases.add(mapTestCaseRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching test cases for problem id={}", problemId, e);
            throw e;
        }
        return testCases;
    }

    /**
     * Thêm bài toán mới.
     */
    public Problem save(Problem problem) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PROBLEM)) {

            stmt.setString(1, problem.getTitle());
            stmt.setString(2, problem.getDescription());
            stmt.setInt(3, problem.getTimeLimitMs() > 0 ? problem.getTimeLimitMs() : 2000);
            stmt.setInt(4, problem.getMemoryLimitKb() > 0 ? problem.getMemoryLimitKb() : 262144);
            stmt.setString(5, problem.getDifficulty() != null ? problem.getDifficulty() : "MEDIUM");
            stmt.setInt(6, problem.getCreatedBy());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    problem.setId(rs.getInt("id"));
                    problem.setCreatedAt(rs.getTimestamp("created_at").toString());
                    logger.info("Created problem: id={}, title='{}'", problem.getId(), problem.getTitle());
                    return problem;
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving problem title='{}'", problem.getTitle(), e);
            throw e;
        }
        throw new SQLException("Failed to create problem, no ID returned");
    }

    /**
     * Thêm test case vào bài.
     */
    public TestCase saveTestCase(TestCase tc) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_TEST_CASE)) {

            stmt.setInt(1, tc.getProblemId());
            stmt.setString(2, tc.getInputData());
            stmt.setString(3, tc.getExpectedOutput());
            stmt.setBoolean(4, tc.isSample());
            stmt.setInt(5, tc.getOrderNum());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    tc.setId(rs.getInt("id"));
                    return tc;
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving test case for problemId={}", tc.getProblemId(), e);
            throw e;
        }
        throw new SQLException("Failed to create test case");
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private List<TestCase> findSampleTestCases(Connection conn, int problemId) throws SQLException {
        List<TestCase> samples = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_SAMPLE_TEST_CASES)) {
            stmt.setInt(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    samples.add(mapTestCaseRow(rs));
                }
            }
        }
        return samples;
    }

    private Problem mapProblemRow(ResultSet rs) throws SQLException {
        Problem p = new Problem();
        p.setId(rs.getInt("id"));
        p.setTitle(rs.getString("title"));
        p.setDescription(rs.getString("description"));
        p.setTimeLimitMs(rs.getInt("time_limit_ms"));
        p.setMemoryLimitKb(rs.getInt("memory_limit_kb"));
        p.setDifficulty(rs.getString("difficulty"));
        p.setCreatedBy(rs.getInt("created_by"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toString());
        return p;
    }

    private TestCase mapTestCaseRow(ResultSet rs) throws SQLException {
        TestCase tc = new TestCase();
        tc.setId(rs.getInt("id"));
        tc.setProblemId(rs.getInt("problem_id"));
        tc.setInputData(rs.getString("input_data"));
        tc.setExpectedOutput(rs.getString("expected_output"));
        tc.setSample(rs.getBoolean("is_sample"));
        tc.setOrderNum(rs.getInt("order_num"));
        return tc;
    }
}
