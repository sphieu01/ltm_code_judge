package com.grading.server.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grading.server.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence and reporting for published, timed class assessments. */
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
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS assessment_attempts (" +
                    "assessment_id INT NOT NULL REFERENCES assessments(id) ON DELETE CASCADE," +
                    "student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                    "started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "completed_at TIMESTAMP NULL, PRIMARY KEY (assessment_id, student_id))");
            stmt.executeUpdate("ALTER TABLE submissions ADD COLUMN IF NOT EXISTS assessment_id INT " +
                    "REFERENCES assessments(id) ON DELETE SET NULL");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assessments_class_window " +
                    "ON assessments(class_id, opens_at, closes_at)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_submissions_assessment " +
                    "ON submissions(assessment_id, user_id, problem_id, submitted_at)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assessment_attempts_student " +
                    "ON assessment_attempts(student_id, assessment_id)");
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
                        if (rs.next()) throw new IllegalArgumentException(
                                "Khoảng thời gian này trùng với một bài kiểm tra đã tạo.");
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

    /** Uses the application server clock so Neon/UTC cannot shift a Vietnam local schedule. */
    public JsonObject findActiveForStudent(int studentId, Integer requestedClassId) throws SQLException {
        for (var element : findForStudent(studentId)) {
            JsonObject assessment = element.getAsJsonObject();
            if (!"ACTIVE".equals(assessment.get("status").getAsString())) continue;
            if (requestedClassId != null && requestedClassId > 0
                    && assessment.get("classId").getAsInt() != requestedClassId) continue;
            return assessment;
        }
        return null;
    }

    public JsonArray findForStudent(int studentId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "WHERE ce.student_id=? AND a.is_published=TRUE ORDER BY a.opens_at DESC";
        JsonArray result = new JsonArray();
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapAssessment(conn, rs, studentId, now, true));
            }
        }
        return result;
    }

    public JsonArray findByClass(int classId, int teacherId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "WHERE a.class_id=? AND (a.created_by=? OR cl.teacher_id=?) " +
                "ORDER BY a.opens_at DESC";
        JsonArray result = new JsonArray();
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            stmt.setInt(2, teacherId);
            stmt.setInt(3, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject item = mapAssessment(conn, rs, null, now, false);
                    int assessmentId = item.get("id").getAsInt();
                    item.addProperty("studentCount", countStudents(conn, classId));
                    item.addProperty("startedCount", countStartedStudents(conn, assessmentId));
                    item.addProperty("completedCount", countCompletedStudents(conn, assessmentId, classId));
                    result.add(item);
                }
            }
        }
        return result;
    }

    public JsonObject getMonitor(int assessmentId, int teacherId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "WHERE a.id=? AND (a.created_by=? OR cl.teacher_id=?)";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            stmt.setInt(2, teacherId);
            stmt.setInt(3, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy bài kiểm tra hoặc bạn không có quyền xem.");
                int classId = rs.getInt("class_id");
                JsonObject result = mapAssessment(conn, rs, null, LocalDateTime.now(), false);
                result.add("progress", findProgress(conn, assessmentId, classId));
                result.add("submissions", findSubmissions(conn, assessmentId));
                return result;
            }
        }
    }

    public Integer findActiveAssessmentIdForProblem(int studentId, int problemId) throws SQLException {
        JsonObject active = findActiveForStudent(studentId, null);
        if (active == null) return null;
        for (var problem : active.getAsJsonArray("problems")) {
            if (problem.getAsJsonObject().get("id").getAsInt() == problemId) {
                return active.get("id").getAsInt();
            }
        }
        return null;
    }

    public boolean isProblemAllowedForStudent(int studentId, int problemId) throws SQLException {
        JsonObject active = findActiveForStudent(studentId, null);
        if (active == null) return true;
        for (var element : active.getAsJsonArray("problemIds")) {
            if (element.getAsInt() == problemId) return true;
        }
        return false;
    }

    public void start(int assessmentId, int studentId) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        String find = "SELECT a.opens_at,a.closes_at,a.duration_minutes FROM assessments a " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "WHERE a.id=? AND ce.student_id=? AND a.is_published=TRUE";
        try (Connection conn = DatabaseConfig.getConnection()) {
            LocalDateTime opensAt;
            LocalDateTime closesAt;
            int duration;
            try (PreparedStatement stmt = conn.prepareStatement(find)) {
                stmt.setInt(1, assessmentId);
                stmt.setInt(2, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy bài kiểm tra dành cho sinh viên này.");
                    opensAt = rs.getTimestamp("opens_at").toLocalDateTime();
                    closesAt = rs.getTimestamp("closes_at").toLocalDateTime();
                    duration = rs.getInt("duration_minutes");
                }
            }
            LocalDateTime effectiveClose = opensAt.plusMinutes(duration);
            if (closesAt.isBefore(effectiveClose)) effectiveClose = closesAt;
            if (now.isBefore(opensAt)) throw new IllegalArgumentException("Bài kiểm tra chưa bắt đầu.");
            if (!now.isBefore(effectiveClose)) throw new IllegalArgumentException("Bài kiểm tra đã kết thúc.");

            String insert = "INSERT INTO assessment_attempts (assessment_id,student_id,started_at) " +
                    "VALUES (?,?,?) ON CONFLICT (assessment_id,student_id) DO NOTHING";
            try (PreparedStatement stmt = conn.prepareStatement(insert)) {
                stmt.setInt(1, assessmentId);
                stmt.setInt(2, studentId);
                stmt.setTimestamp(3, Timestamp.valueOf(now));
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT completed_at FROM assessment_attempts WHERE assessment_id=? AND student_id=?")) {
                stmt.setInt(1, assessmentId);
                stmt.setInt(2, studentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getTimestamp(1) != null)
                        throw new IllegalArgumentException("Bài kiểm tra này đã được hoàn thành.");
                }
            }
        }
    }

    public void complete(int assessmentId, int studentId) throws SQLException {
        String sql = "UPDATE assessment_attempts SET completed_at=? " +
                "WHERE assessment_id=? AND student_id=? AND completed_at IS NULL";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, assessmentId);
            stmt.setInt(3, studentId);
            if (stmt.executeUpdate() != 1)
                throw new IllegalArgumentException("Bài kiểm tra chưa được bắt đầu hoặc đã hoàn thành.");
        }
    }

    private JsonObject mapAssessment(Connection conn, ResultSet rs, Integer studentId,
                                     LocalDateTime now, boolean includeProblems) throws SQLException {
        int id = rs.getInt("id");
        LocalDateTime opensAt = rs.getTimestamp("opens_at").toLocalDateTime();
        LocalDateTime scheduledClose = rs.getTimestamp("closes_at").toLocalDateTime();
        int duration = rs.getInt("duration_minutes");
        LocalDateTime effectiveClose = opensAt.plusMinutes(duration);
        if (scheduledClose.isBefore(effectiveClose)) effectiveClose = scheduledClose;

        int problemCount = countProblems(conn, id);
        int submittedCount = studentId == null ? 0 : countSubmittedProblems(conn, id, studentId);
        Timestamp[] attempt = studentId == null ? null : findAttempt(conn, id, studentId);
        boolean started = attempt != null;
        boolean completed = attempt != null && attempt[1] != null;
        String status = completed ? "COMPLETED"
                : now.isBefore(opensAt) ? "UPCOMING"
                : now.isBefore(effectiveClose) ? "ACTIVE" : "EXPIRED";

        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("classId", rs.getInt("class_id"));
        result.addProperty("classCode", rs.getString("class_code"));
        result.addProperty("className", rs.getString("class_name"));
        result.addProperty("title", rs.getString("title"));
        result.addProperty("opensAt", opensAt.toString());
        result.addProperty("closesAt", effectiveClose.toString());
        result.addProperty("scheduledClosesAt", scheduledClose.toString());
        result.addProperty("durationMinutes", duration);
        result.addProperty("status", status);
        result.addProperty("problemCount", problemCount);
        result.addProperty("submittedCount", submittedCount);
        result.addProperty("completed", completed);
        result.addProperty("started", started);
        if (attempt != null && attempt[0] != null)
            result.addProperty("startedAt", attempt[0].toLocalDateTime().toString());
        if (attempt != null && attempt[1] != null)
            result.addProperty("completedAt", attempt[1].toLocalDateTime().toString());
        JsonArray problems = findProblems(conn, id, studentId);
        JsonArray ids = new JsonArray();
        for (var problem : problems) ids.add(problem.getAsJsonObject().get("id").getAsInt());
        result.add("problemIds", ids);
        if (includeProblems) result.add("problems", problems);
        return result;
    }

    private JsonArray findProblems(Connection conn, int assessmentId, Integer studentId) throws SQLException {
        String sql = "SELECT p.id,p.title,p.description,p.difficulty,p.time_limit_ms,p.memory_limit_kb,p.class_id,ap.order_num " +
                "FROM assessment_problems ap JOIN problems p ON p.id=ap.problem_id " +
                "WHERE ap.assessment_id=? ORDER BY ap.order_num";
        JsonArray result = new JsonArray();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject problem = new JsonObject();
                    int problemId = rs.getInt("id");
                    problem.addProperty("id", problemId);
                    problem.addProperty("title", rs.getString("title"));
                    problem.addProperty("description", rs.getString("description"));
                    problem.addProperty("difficulty", rs.getString("difficulty"));
                    problem.addProperty("timeLimitMs", rs.getInt("time_limit_ms"));
                    problem.addProperty("memoryLimitKb", rs.getInt("memory_limit_kb"));
                    int classId = rs.getInt("class_id");
                    if (!rs.wasNull()) problem.addProperty("classId", classId);
                    if (studentId != null) addStudentProblemStatus(conn, problem, assessmentId, studentId, problemId);
                    result.add(problem);
                }
            }
        }
        return result;
    }

    private Timestamp[] findAttempt(Connection conn, int assessmentId, int studentId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT started_at,completed_at FROM assessment_attempts WHERE assessment_id=? AND student_id=?")) {
            stmt.setInt(1, assessmentId);
            stmt.setInt(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? new Timestamp[]{rs.getTimestamp(1), rs.getTimestamp(2)} : null;
            }
        }
    }

    private void addStudentProblemStatus(Connection conn, JsonObject problem, int assessmentId,
                                         int studentId, int problemId) throws SQLException {
        String sql = "SELECT verdict::text,score,submitted_at FROM submissions " +
                "WHERE assessment_id=? AND user_id=? AND problem_id=? ORDER BY submitted_at DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            stmt.setInt(2, studentId);
            stmt.setInt(3, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    problem.addProperty("submitted", true);
                    problem.addProperty("verdict", rs.getString(1));
                    problem.addProperty("score", rs.getInt(2));
                    problem.addProperty("lastSubmittedAt", rs.getTimestamp(3).toLocalDateTime().toString());
                } else {
                    problem.addProperty("submitted", false);
                    problem.addProperty("verdict", "");
                }
            }
        }
    }

    private JsonArray findProgress(Connection conn, int assessmentId, int classId) throws SQLException {
        int totalProblems = countProblems(conn, assessmentId);
        String sql = "SELECT u.id,u.username,u.full_name,aa.started_at,aa.completed_at," +
                "COUNT(DISTINCT s.problem_id) submitted_count," +
                "COUNT(DISTINCT CASE WHEN s.verdict='AC'::verdict_type THEN s.problem_id END) solved_count," +
                "MAX(s.submitted_at) last_submission_at " +
                "FROM class_enrollments ce JOIN users u ON u.id=ce.student_id " +
                "LEFT JOIN assessment_attempts aa ON aa.student_id=u.id AND aa.assessment_id=? " +
                "LEFT JOIN submissions s ON s.user_id=u.id AND s.assessment_id=? " +
                "WHERE ce.class_id=? GROUP BY u.id,u.username,u.full_name,aa.started_at,aa.completed_at ORDER BY u.username";
        JsonArray result = new JsonArray();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            stmt.setInt(2, assessmentId);
            stmt.setInt(3, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject row = new JsonObject();
                    int submitted = rs.getInt("submitted_count");
                    row.addProperty("studentId", rs.getInt("id"));
                    row.addProperty("username", rs.getString("username"));
                    row.addProperty("fullName", rs.getString("full_name"));
                    row.addProperty("submittedCount", submitted);
                    row.addProperty("solvedCount", rs.getInt("solved_count"));
                    row.addProperty("totalProblems", totalProblems);
                    Timestamp startedAt = rs.getTimestamp("started_at");
                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    row.addProperty("started", startedAt != null);
                    row.addProperty("completed", completedAt != null);
                    if (startedAt != null) row.addProperty("startedAt", startedAt.toLocalDateTime().toString());
                    if (completedAt != null) row.addProperty("completedAt", completedAt.toLocalDateTime().toString());
                    Timestamp last = rs.getTimestamp("last_submission_at");
                    if (last != null) row.addProperty("lastSubmissionAt", last.toLocalDateTime().toString());
                    result.add(row);
                }
            }
        }
        return result;
    }

    private JsonArray findSubmissions(Connection conn, int assessmentId) throws SQLException {
        String sql = "SELECT s.id,s.user_id,u.username,u.full_name,s.problem_id,p.title problem_title," +
                "s.language,s.verdict::text,s.score,s.passed_tests,s.total_tests,s.source_code,s.submitted_at,s.judged_at " +
                "FROM submissions s JOIN users u ON u.id=s.user_id JOIN problems p ON p.id=s.problem_id " +
                "WHERE s.assessment_id=? ORDER BY s.submitted_at DESC";
        JsonArray result = new JsonArray();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("submissionId", rs.getInt("id"));
                    row.addProperty("userId", rs.getInt("user_id"));
                    row.addProperty("username", rs.getString("username"));
                    row.addProperty("fullName", rs.getString("full_name"));
                    row.addProperty("problemId", rs.getInt("problem_id"));
                    row.addProperty("problemTitle", rs.getString("problem_title"));
                    row.addProperty("language", rs.getString("language"));
                    row.addProperty("verdict", rs.getString("verdict"));
                    row.addProperty("score", rs.getInt("score"));
                    row.addProperty("passedTests", rs.getInt("passed_tests"));
                    row.addProperty("totalTests", rs.getInt("total_tests"));
                    row.addProperty("sourceCode", rs.getString("source_code"));
                    Timestamp submitted = rs.getTimestamp("submitted_at");
                    if (submitted != null) row.addProperty("submittedAt", submitted.toLocalDateTime().toString());
                    Timestamp judged = rs.getTimestamp("judged_at");
                    if (judged != null) row.addProperty("judgedAt", judged.toLocalDateTime().toString());
                    result.add(row);
                }
            }
        }
        return result;
    }

    private int countProblems(Connection conn, int assessmentId) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM assessment_problems WHERE assessment_id=?", assessmentId);
    }

    private int countSubmittedProblems(Connection conn, int assessmentId, int studentId) throws SQLException {
        return count(conn, "SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE assessment_id=? AND user_id=?",
                assessmentId, studentId);
    }

    private int countStudents(Connection conn, int classId) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM class_enrollments WHERE class_id=?", classId);
    }

    private int countStartedStudents(Connection conn, int assessmentId) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM assessment_attempts WHERE assessment_id=?", assessmentId);
    }

    private int countCompletedStudents(Connection conn, int assessmentId, int classId) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM assessment_attempts aa " +
                "JOIN class_enrollments ce ON ce.student_id=aa.student_id " +
                "WHERE aa.assessment_id=? AND ce.class_id=? AND aa.completed_at IS NOT NULL",
                assessmentId, classId);
    }

    private int count(Connection conn, String sql, int... values) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) stmt.setInt(i + 1, values[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
