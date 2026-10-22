package com.grading.server.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grading.server.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence for published, timed class assessments. */
public class AssessmentRepository {
    private static final Logger logger = LoggerFactory.getLogger(AssessmentRepository.class);

    public AssessmentRepository() {
        initSchemaIfNeeded();
    }

    private void initSchemaIfNeeded() {
        try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS assessments (" +
                    "id SERIAL PRIMARY KEY, class_id INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE," +
                    "title VARCHAR(200) NOT NULL, description TEXT, opens_at TIMESTAMP NOT NULL," +
                    "closes_at TIMESTAMP NOT NULL, duration_minutes INT NOT NULL CHECK (duration_minutes > 0)," +
                    "is_published BOOLEAN NOT NULL DEFAULT FALSE, created_by INT REFERENCES users(id) ON DELETE SET NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CHECK (closes_at > opens_at))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS assessment_problems (" +
                    "assessment_id INT NOT NULL REFERENCES assessments(id) ON DELETE CASCADE," +
                    "problem_id INT NOT NULL REFERENCES problems(id) ON DELETE CASCADE," +
                    "order_num INT NOT NULL DEFAULT 0, PRIMARY KEY (assessment_id, problem_id))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assessments_class_window " +
                    "ON assessments(class_id, opens_at, closes_at)");
        } catch (SQLException e) {
            logger.warn("Assessment schema initialization notice: {}", e.getMessage());
        }
    }

    public int create(int classId, String title, LocalDateTime opensAt, LocalDateTime closesAt,
                      int durationMinutes, int teacherId, List<Integer> problemIds) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String overlapSql = "SELECT 1 FROM assessments WHERE class_id=? AND is_published=TRUE " +
                        "AND opens_at < ? AND closes_at > ? LIMIT 1";
                try (PreparedStatement check = conn.prepareStatement(overlapSql)) {
                    check.setInt(1, classId);
                    check.setTimestamp(2, Timestamp.valueOf(closesAt));
                    check.setTimestamp(3, Timestamp.valueOf(opensAt));
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) throw new IllegalArgumentException("Khoảng thời gian này trùng với một bài kiểm tra đã tạo.");
                    }
                }

                int assessmentId;
                String insert = "INSERT INTO assessments " +
                        "(class_id,title,opens_at,closes_at,duration_minutes,is_published,created_by) " +
                        "VALUES (?,?,?,?,?,TRUE,?) RETURNING id";
                try (PreparedStatement stmt = conn.prepareStatement(insert)) {
                    stmt.setInt(1, classId);
                    stmt.setString(2, title);
                    stmt.setTimestamp(3, Timestamp.valueOf(opensAt));
                    stmt.setTimestamp(4, Timestamp.valueOf(closesAt));
                    stmt.setInt(5, durationMinutes);
                    stmt.setInt(6, teacherId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không thể tạo bài kiểm tra");
                        assessmentId = rs.getInt(1);
                    }
                }

                String addProblem = "INSERT INTO assessment_problems (assessment_id,problem_id,order_num) " +
                        "SELECT ?,p.id,? FROM problems p WHERE p.id=? AND (p.class_id=? OR p.class_id IS NULL)";
                try (PreparedStatement stmt = conn.prepareStatement(addProblem)) {
                    int order = 1;
                    for (Integer problemId : problemIds) {
                        stmt.setInt(1, assessmentId);
                        stmt.setInt(2, order++);
                        stmt.setInt(3, problemId);
                        stmt.setInt(4, classId);
                        if (stmt.executeUpdate() == 0) {
                            throw new IllegalArgumentException("Bài tập #" + problemId + " không thuộc lớp này.");
                        }
                    }
                }
                conn.commit();
                return assessmentId;
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException sql) throw sql;
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public JsonObject findActiveForStudent(int studentId, Integer requestedClassId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code," +
                "LEAST(a.closes_at, a.opens_at + a.duration_minutes * INTERVAL '1 minute') AS effective_closes_at " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "JOIN users u ON u.id=ce.student_id " +
                "WHERE u.id=? AND u.role='STUDENT' AND a.is_published=TRUE " +
                "AND CURRENT_TIMESTAMP>=a.opens_at " +
                "AND CURRENT_TIMESTAMP<LEAST(a.closes_at, a.opens_at + a.duration_minutes * INTERVAL '1 minute') " +
                (requestedClassId != null && requestedClassId > 0 ? "AND a.class_id=? " : "") +
                "ORDER BY a.opens_at DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            if (requestedClassId != null && requestedClassId > 0) stmt.setInt(2, requestedClassId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                JsonObject result = new JsonObject();
                int assessmentId = rs.getInt("id");
                result.addProperty("id", assessmentId);
                result.addProperty("classId", rs.getInt("class_id"));
                result.addProperty("classCode", rs.getString("class_code"));
                result.addProperty("title", rs.getString("title"));
                result.addProperty("opensAt", rs.getTimestamp("opens_at").toLocalDateTime().toString());
                result.addProperty("closesAt", rs.getTimestamp("effective_closes_at").toLocalDateTime().toString());
                result.addProperty("durationMinutes", rs.getInt("duration_minutes"));
                result.add("problemIds", findProblemIds(conn, assessmentId));
                return result;
            }
        }
    }

    private JsonArray findProblemIds(Connection conn, int assessmentId) throws SQLException {
        JsonArray ids = new JsonArray();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT problem_id FROM assessment_problems WHERE assessment_id=? ORDER BY order_num")) {
            stmt.setInt(1, assessmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    public boolean isProblemAllowedForStudent(int studentId, int problemId) throws SQLException {
        JsonObject active = findActiveForStudent(studentId, null);
        if (active == null) return true;
        for (var element : active.getAsJsonArray("problemIds")) {
            if (element.getAsInt() == problemId) return true;
        }
        return false;
    }
}
