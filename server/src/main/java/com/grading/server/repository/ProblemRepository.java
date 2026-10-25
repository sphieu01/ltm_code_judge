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

    public ProblemRepository() {
        initSchemaIfNeeded();
    }

    private void initSchemaIfNeeded() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE problems ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE problems ADD COLUMN IF NOT EXISTS problem_type VARCHAR(20) DEFAULT 'IO_MATCHING'");
            stmt.executeUpdate("ALTER TABLE problems ADD COLUMN IF NOT EXISTS oop_spec TEXT DEFAULT NULL");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS class_problems (" +
                    "class_id INT REFERENCES classes(id) ON DELETE CASCADE, " +
                    "problem_id INT REFERENCES problems(id) ON DELETE CASCADE, " +
                    "PRIMARY KEY (class_id, problem_id))");
        } catch (SQLException e) {
            logger.warn("Schema initialization notice for problems: {}", e.getMessage());
        }
    }

    // ─── SQL ──────────────────────────────────────────────────────────────────
    private static final String SQL_FIND_ALL =
            "SELECT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
            "p.created_by, p.class_id, cl.name AS class_name, p.is_active, p.created_at, p.problem_type, p.oop_spec " +
            "FROM problems p LEFT JOIN classes cl ON p.class_id = cl.id ORDER BY p.id";

    private static final String SQL_FIND_ALL_FOR_STUDENT =
            "SELECT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
            "p.created_by, p.class_id, cl.name AS class_name, p.is_active, p.created_at, p.problem_type, p.oop_spec " +
            "FROM problems p LEFT JOIN classes cl ON p.class_id = cl.id " +
            "WHERE (p.is_active IS NULL OR p.is_active = TRUE) " +
            "AND NULLIF(BTRIM(p.title), '') IS NOT NULL ORDER BY p.id";

    private static final String SQL_FIND_BY_CLASS =
            "SELECT DISTINCT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
            "p.created_by, p.class_id, cl.name AS class_name, p.is_active, p.created_at, p.problem_type, p.oop_spec " +
            "FROM problems p " +
            "LEFT JOIN classes cl ON p.class_id = cl.id " +
            "LEFT JOIN class_problems cp ON p.id = cp.problem_id " +
            "WHERE cp.class_id = ? OR p.class_id = ? ORDER BY p.id";

    private static final String SQL_FIND_BY_CLASS_FOR_STUDENT =
            "SELECT DISTINCT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
            "p.created_by, p.class_id, cl.name AS class_name, p.is_active, p.created_at, p.problem_type, p.oop_spec " +
            "FROM problems p " +
            "LEFT JOIN classes cl ON p.class_id = cl.id " +
            "LEFT JOIN class_problems cp ON p.id = cp.problem_id " +
            "WHERE (cp.class_id = ? OR p.class_id = ?) " +
            "AND (p.is_active IS NULL OR p.is_active = TRUE) " +
            "AND NULLIF(BTRIM(p.title), '') IS NOT NULL ORDER BY p.id";

    private static final String SQL_FIND_BY_ID =
            "SELECT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
            "p.created_by, p.class_id, cl.name AS class_name, p.is_active, p.created_at, p.problem_type, p.oop_spec " +
            "FROM problems p LEFT JOIN classes cl ON p.class_id = cl.id WHERE p.id = ?";

    private static final String SQL_FIND_TEST_CASES =
            "SELECT id, problem_id, input_data, expected_output, is_sample, order_num " +
            "FROM test_cases WHERE problem_id = ? ORDER BY order_num";

    private static final String SQL_FIND_SAMPLE_TEST_CASES =
            "SELECT id, problem_id, input_data, expected_output, is_sample, order_num " +
            "FROM test_cases WHERE problem_id = ? AND is_sample = TRUE ORDER BY order_num";

    private static final String SQL_INSERT_PROBLEM =
            "INSERT INTO problems (title, description, time_limit_ms, memory_limit_kb, difficulty, created_by, class_id, problem_type, oop_spec) " +
            "VALUES (?, ?, ?, ?, ?::difficulty_level, ?, ?, ?, ?) RETURNING id, created_at";

    private static final String SQL_INSERT_TEST_CASE =
            "INSERT INTO test_cases (problem_id, input_data, expected_output, is_sample, order_num) " +
            "VALUES (?, ?, ?, ?, ?) RETURNING id";

    // ─── Public Methods ───────────────────────────────────────────────────────

    /**
     * Lấy danh sách tất cả bài toán (không kèm test cases).
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
     * Lấy danh sách bài toán theo lớp tín chỉ (hoặc bài tập chung).
     */
    public List<Problem> findByClassId(int classId) throws SQLException {
        if (classId <= 0) return findAll();

        List<Problem> problems = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_CLASS)) {

            stmt.setInt(1, classId);
            stmt.setInt(2, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    problems.add(mapProblemRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching problems for classId={}", classId, e);
            throw e;
        }
        return problems;
    }

    public List<Problem> findAllForStudent() throws SQLException {
        List<Problem> problems = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_ALL_FOR_STUDENT);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                problems.add(mapProblemRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all problems for student", e);
            throw e;
        }
        return problems;
    }

    public List<Problem> findByClassIdForStudent(int classId) throws SQLException {
        if (classId <= 0) return findAllForStudent();

        List<Problem> problems = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_CLASS_FOR_STUDENT)) {

            stmt.setInt(1, classId);
            stmt.setInt(2, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    problems.add(mapProblemRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching problems for student classId={}", classId, e);
            throw e;
        }
        return problems;
    }


    /**
     * Lấy chi tiết 1 bài toán kèm sample test cases.
     * Dùng cho GET_PROBLEM_DETAIL.
     */
    public Optional<Problem> findById(int id) throws SQLException {
        return findById(id, false);
    }

    /** Giáo viên cần toàn bộ test case để có thể chỉnh sửa; sinh viên chỉ nhận test mẫu. */
    public Optional<Problem> findById(int id, boolean includeHiddenTests) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Problem problem = mapProblemRow(rs);
                    problem.setTestCases(includeHiddenTests
                            ? findAllTestCases(conn, id)
                            : findSampleTestCases(conn, id));
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

    private List<TestCase> findAllTestCases(Connection conn, int problemId) throws SQLException {
        List<TestCase> testCases = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_FIND_TEST_CASES)) {
            stmt.setInt(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) testCases.add(mapTestCaseRow(rs));
            }
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
            if (problem.getClassId() != null && problem.getClassId() > 0) {
                stmt.setInt(7, problem.getClassId());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            stmt.setString(8, problem.getProblemType());
            stmt.setString(9, problem.getOopSpec());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    problem.setId(rs.getInt("id"));
                    problem.setCreatedAt(rs.getTimestamp("created_at").toString());
                    logger.info("Created problem: id={}, title='{}', classId={}",
                            problem.getId(), problem.getTitle(), problem.getClassId());
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

    /** Thay thế đồng thời metadata và danh sách test case của một bài. */
    public Problem updateWithTestCases(Problem problem, List<TestCase> testCases) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String update = "UPDATE problems SET title=?, description=?, time_limit_ms=?, memory_limit_kb=?, difficulty=?::difficulty_level, class_id=?, problem_type=?, oop_spec=? WHERE id=?";
                try (PreparedStatement stmt = conn.prepareStatement(update)) {
                    stmt.setString(1, problem.getTitle());
                    stmt.setString(2, problem.getDescription());
                    stmt.setInt(3, problem.getTimeLimitMs());
                    stmt.setInt(4, problem.getMemoryLimitKb());
                    stmt.setString(5, problem.getDifficulty());
                    if (problem.getClassId() != null && problem.getClassId() > 0) stmt.setInt(6, problem.getClassId());
                    else stmt.setNull(6, Types.INTEGER);
                    stmt.setString(7, problem.getProblemType());
                    stmt.setString(8, problem.getOopSpec());
                    stmt.setInt(9, problem.getId());
                    if (stmt.executeUpdate() == 0) throw new SQLException("Không tìm thấy bài tập id=" + problem.getId());
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM test_cases WHERE problem_id=?")) {
                    stmt.setInt(1, problem.getId());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_TEST_CASE)) {
                    int order = 1;
                    for (TestCase tc : testCases) {
                        stmt.setInt(1, problem.getId());
                        stmt.setString(2, tc.getInputData());
                        stmt.setString(3, tc.getExpectedOutput());
                        stmt.setBoolean(4, tc.isSample());
                        stmt.setInt(5, order++);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
                return problem;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
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
        try {
            if (rs.getObject("class_id") != null) {
                p.setClassId(rs.getInt("class_id"));
            }
            p.setClassName(rs.getString("class_name"));
        } catch (SQLException ignored) {}
        try {
            if (rs.getObject("is_active") != null) {
                p.setActive(rs.getBoolean("is_active"));
            } else {
                p.setActive(true);
            }
        } catch (SQLException ignored) {
            p.setActive(true);
        }
        try {
            String pt = rs.getString("problem_type");
            if (pt != null) p.setProblemType(pt);
        } catch (SQLException ignored) {}
        try {
            p.setOopSpec(rs.getString("oop_spec"));
        } catch (SQLException ignored) {}
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toString());
        return p;
    }

    public void updateProblemActive(int problemId, boolean active) throws SQLException {
        String sql = "UPDATE problems SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setInt(2, problemId);
            stmt.executeUpdate();
            logger.info("Updated problem id={} active={}", problemId, active);
        } catch (SQLException e) {
            logger.error("Error updating problem id={} active={}", problemId, active, e);
            throw e;
        }
    }

    public void deleteProblem(int problemId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM problems WHERE id = ?")) {
            stmt.setInt(1, problemId);
            if (stmt.executeUpdate() == 0) throw new SQLException("Không tìm thấy bài tập id=" + problemId);
        }
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
