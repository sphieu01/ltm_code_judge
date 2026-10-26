package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.ProctorLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Durable audit events and atomic violation counters, scoped to an exam attempt. */
public class ProctorLogRepository {
    private static final String COLUMNS = "id,user_id,username,full_name,problem_id,assessment_id,problem_title," +
            "event_type,detail,violation_count,severity,client_ip,occurred_at_ms";
    private final ConnectionProvider connections;

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public ProctorLogRepository() {
        this(DatabaseConfig::getConnection);
        initializeSchema();
    }

    /** The supplied database owns schema setup; useful for integration tests. */
    public ProctorLogRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    private void initializeSchema() {
        try (Connection conn = connections.open(); Statement stmt = conn.createStatement()) {
            // Keep identity/title snapshots even if the original user, problem or exam is deleted.
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS proctor_logs (" +
                    "id SERIAL PRIMARY KEY,user_id INT NOT NULL,username TEXT,full_name TEXT," +
                    "problem_id INT NOT NULL,assessment_id INT,problem_title TEXT,event_type VARCHAR(100) NOT NULL," +
                    "detail TEXT,violation_count INT NOT NULL,severity VARCHAR(20) NOT NULL,client_ip TEXT," +
                    "occurred_at_ms BIGINT NOT NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS proctor_violation_counters (" +
                    "user_id INT NOT NULL,problem_id INT NOT NULL,assessment_scope INT NOT NULL," +
                    "violation_count INT NOT NULL,PRIMARY KEY(user_id,problem_id,assessment_scope))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_proctor_logs_assessment_time " +
                    "ON proctor_logs(assessment_id,occurred_at_ms DESC,id DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_proctor_logs_time " +
                    "ON proctor_logs(occurred_at_ms DESC,id DESC)");
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể khởi tạo bảng lưu nhật ký giám sát.", e);
        }
    }

    /** Increment and insert share one transaction, so a failed write consumes no count. */
    public ProctorLog append(int userId, int problemId, Integer assessmentId,
                             IntFunction<ProctorLog> logFactory) throws SQLException {
        try (Connection conn = connections.open()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                int count;
                String increment = "INSERT INTO proctor_violation_counters " +
                        "(user_id,problem_id,assessment_scope,violation_count) VALUES (?,?,?,1) " +
                        "ON CONFLICT (user_id,problem_id,assessment_scope) DO UPDATE SET " +
                        "violation_count=proctor_violation_counters.violation_count+1 RETURNING violation_count";
                try (PreparedStatement stmt = conn.prepareStatement(increment)) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, problemId);
                    stmt.setInt(3, scope(assessmentId));
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không thể cập nhật số lần vi phạm.");
                        count = rs.getInt(1);
                    }
                }
                ProctorLog log = logFactory.apply(count);
                String insert = "INSERT INTO proctor_logs " +
                        "(user_id,username,full_name,problem_id,assessment_id,problem_title,event_type,detail," +
                        "violation_count,severity,client_ip,occurred_at_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id";
                try (PreparedStatement stmt = conn.prepareStatement(insert)) {
                    stmt.setInt(1, log.getUserId());
                    stmt.setString(2, log.getUsername());
                    stmt.setString(3, log.getFullName());
                    stmt.setInt(4, log.getProblemId());
                    if (log.getAssessmentId() == null) stmt.setNull(5, Types.INTEGER);
                    else stmt.setInt(5, log.getAssessmentId());
                    stmt.setString(6, log.getProblemTitle());
                    stmt.setString(7, log.getEventType());
                    stmt.setString(8, log.getDetail());
                    stmt.setInt(9, log.getViolationCount());
                    stmt.setString(10, log.getSeverity());
                    stmt.setString(11, log.getClientIp());
                    stmt.setLong(12, log.getTimestamp());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không thể lưu nhật ký giám sát.");
                        log.setId(rs.getInt(1));
                    }
                }
                conn.commit();
                return log;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public List<ProctorLog> findAll() throws SQLException {
        return find(null);
    }

    public List<ProctorLog> findByAssessment(int assessmentId) throws SQLException {
        return find(assessmentId);
    }

    private List<ProctorLog> find(Integer assessmentId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM proctor_logs" +
                (assessmentId == null ? "" : " WHERE assessment_id=?") + " ORDER BY occurred_at_ms DESC,id DESC";
        List<ProctorLog> result = new ArrayList<>();
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (assessmentId != null) stmt.setInt(1, assessmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int examId = rs.getInt("assessment_id");
                    Integer exam = rs.wasNull() ? null : examId;
                    result.add(new ProctorLog(rs.getInt("id"), rs.getInt("user_id"), rs.getString("username"),
                            rs.getString("full_name"), rs.getInt("problem_id"), exam, rs.getString("problem_title"),
                            rs.getString("event_type"), rs.getString("detail"), rs.getInt("violation_count"),
                            rs.getString("severity"), rs.getString("client_ip"), rs.getLong("occurred_at_ms")));
                }
            }
        }
        return result;
    }

    public int getViolationCount(int userId, int problemId, Integer assessmentId) throws SQLException {
        String sql = "SELECT violation_count FROM proctor_violation_counters " +
                "WHERE user_id=? AND problem_id=? AND assessment_scope=?";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, problemId);
            stmt.setInt(3, scope(assessmentId));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Reset the current counters without deleting historical audit evidence. */
    public void resetViolations(int userId, int problemId) throws SQLException {
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM proctor_violation_counters WHERE user_id=? AND problem_id=?")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, problemId);
            stmt.executeUpdate();
        }
    }

    private static int scope(Integer assessmentId) {
        return assessmentId == null ? 0 : assessmentId;
    }
}
