package com.grading.server.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.CreditClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence and reporting for published, timed class assessments. */
public class AssessmentRepository {
    private static final Logger logger = LoggerFactory.getLogger(AssessmentRepository.class);
    private final ConnectionProvider connections;
    private final Clock clock;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public AssessmentRepository() {
        this(DatabaseConfig::getConnection, Clock.systemDefaultZone());
        initSchemaIfNeeded();
    }

    AssessmentRepository(ConnectionProvider connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    private void initSchemaIfNeeded() {
        try (Connection conn = connections.open(); Statement stmt = conn.createStatement()) {
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
        try (Connection conn = connections.open()) {
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
                        "SELECT ?,p.id,? FROM problems p WHERE p.id=?";
                try (PreparedStatement stmt = conn.prepareStatement(addProblem)) {
                    int order = 1;
                    for (Integer problemId : problemIds) {
                        stmt.setInt(1, assessmentId);
                        stmt.setInt(2, order++);
                        stmt.setInt(3, problemId);
                        if (stmt.executeUpdate() == 0) {
                            throw new IllegalArgumentException("Bài tập #" + problemId + " không tồn tại.");
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

    public void update(int assessmentId, String title, LocalDateTime opensAt, LocalDateTime closesAt,
                       int durationMinutes, List<Integer> problemIds) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                String updateSql = "UPDATE assessments SET title=?, opens_at=?, closes_at=?, duration_minutes=? WHERE id=?";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, title);
                    stmt.setTimestamp(2, Timestamp.valueOf(opensAt));
                    stmt.setTimestamp(3, Timestamp.valueOf(closesAt));
                    stmt.setInt(4, durationMinutes);
                    stmt.setInt(5, assessmentId);
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        throw new IllegalArgumentException("Không tìm thấy bài kiểm tra.");
                    }
                }

                if (problemIds != null && !problemIds.isEmpty()) {
                    String deleteOldProblems = "DELETE FROM assessment_problems WHERE assessment_id=?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteOldProblems)) {
                        stmt.setInt(1, assessmentId);
                        stmt.executeUpdate();
                    }

                    String addProblem = "INSERT INTO assessment_problems (assessment_id,problem_id,order_num) " +
                            "SELECT ?,p.id,? FROM problems p WHERE p.id=?";
                    try (PreparedStatement stmt = conn.prepareStatement(addProblem)) {
                        int order = 1;
                        for (Integer problemId : problemIds) {
                            stmt.setInt(1, assessmentId);
                            stmt.setInt(2, order++);
                            stmt.setInt(3, problemId);
                            if (stmt.executeUpdate() == 0) {
                                throw new IllegalArgumentException("Bài tập #" + problemId + " không tồn tại.");
                            }
                        }
                    }
                }

                conn.commit();
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

    public void delete(int assessmentId) throws SQLException {
        try (Connection conn = connections.open()) {
            String sql = "DELETE FROM assessments WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, assessmentId);
                int deleted = stmt.executeUpdate();
                if (deleted == 0) {
                    throw new IllegalArgumentException("Không tìm thấy bài kiểm tra.");
                }
            }
        }
    }

    /** Uses the application server clock so Neon/UTC cannot shift a Vietnam local schedule. */
    public JsonObject findActiveForStudent(int studentId, Integer requestedClassId) throws SQLException {
        LocalDateTime now = LocalDateTime.now(clock);
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name, " +
                "cl.allowed_languages, co.language AS course_lang, co.name AS course_name,aa.started_at,aa.completed_at," +
                "(SELECT COUNT(*) FROM assessment_problems apc WHERE apc.assessment_id=a.id) AS problem_count," +
                "(SELECT COUNT(DISTINCT sc.problem_id) FROM submissions sc WHERE sc.assessment_id=a.id AND sc.user_id=ce.student_id) AS submitted_count " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "JOIN assessment_attempts aa ON aa.assessment_id=a.id AND aa.student_id=ce.student_id " +
                "WHERE ce.student_id=? AND a.is_published=TRUE AND aa.completed_at IS NULL " +
                "AND (co.is_active IS NULL OR co.is_active=TRUE) " +
                "AND a.opens_at<=? AND LEAST(a.closes_at,a.opens_at+a.duration_minutes*INTERVAL '1 minute')>? " +
                (requestedClassId != null && requestedClassId > 0 ? "AND a.class_id=? " : "") +
                "ORDER BY a.opens_at LIMIT 1";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setTimestamp(2, Timestamp.valueOf(now));
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            if (requestedClassId != null && requestedClassId > 0) stmt.setInt(4, requestedClassId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp started = rs.getTimestamp("started_at");
                Timestamp[] attempt = started == null ? null : new Timestamp[]{started, rs.getTimestamp("completed_at")};
                JsonObject assessment = mapAssessmentHeader(rs, now, rs.getInt("problem_count"),
                        rs.getInt("submitted_count"), attempt);
                if (!"ACTIVE".equals(assessment.get("status").getAsString())
                        || !assessment.get("started").getAsBoolean()) return null;
                if (requestedClassId != null && requestedClassId > 0
                        && assessment.get("classId").getAsInt() != requestedClassId) return null;
                attachProblems(conn, assessment, rs.getInt("id"), studentId, true);
                return assessment;
            }
        }
    }

    /** A polling response needs schedule/progress only, never every question and submission. */
    public JsonArray findSummariesForStudent(int studentId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name, " +
                "cl.allowed_languages,co.language AS course_lang,co.name AS course_name," +
                "COALESCE(pc.problem_count,0) AS problem_count,COALESCE(sc.submitted_count,0) AS submitted_count," +
                "aa.started_at,aa.completed_at " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "LEFT JOIN assessment_attempts aa ON aa.assessment_id=a.id AND aa.student_id=ce.student_id " +
                "LEFT JOIN (SELECT assessment_id,COUNT(*) AS problem_count FROM assessment_problems GROUP BY assessment_id) pc ON pc.assessment_id=a.id " +
                "LEFT JOIN (SELECT assessment_id,COUNT(DISTINCT problem_id) AS submitted_count FROM submissions " +
                "WHERE user_id=? AND assessment_id IS NOT NULL GROUP BY assessment_id) sc ON sc.assessment_id=a.id " +
                "WHERE ce.student_id=? AND a.is_published=TRUE AND aa.completed_at IS NULL " +
                "AND (co.is_active IS NULL OR co.is_active=TRUE) " +
                "AND LEAST(a.closes_at,a.opens_at+a.duration_minutes*INTERVAL '1 minute')>? ORDER BY a.opens_at";
        LocalDateTime now = LocalDateTime.now(clock);
        JsonArray result = new JsonArray();
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, studentId);
            stmt.setTimestamp(3, Timestamp.valueOf(now));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp started = rs.getTimestamp("started_at");
                    Timestamp completed = rs.getTimestamp("completed_at");
                    Timestamp[] attempt = started == null ? null : new Timestamp[]{started, completed};
                    JsonObject item = mapAssessmentHeader(rs, now, rs.getInt("problem_count"),
                            rs.getInt("submitted_count"), attempt);
                    if (!"COMPLETED".equals(item.get("status").getAsString())
                            && !"EXPIRED".equals(item.get("status").getAsString())) result.add(item);
                }
            }
        }
        return result;
    }

    public JsonArray findForStudent(int studentId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name, " +
                "cl.allowed_languages, co.language AS course_lang, co.name AS course_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "WHERE ce.student_id=? AND a.is_published=TRUE ORDER BY a.opens_at DESC";
        JsonArray result = new JsonArray();
        LocalDateTime now = LocalDateTime.now(clock);
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapAssessment(conn, rs, studentId, now, true));
            }
        }
        return result;
    }

    public JsonArray findByClass(int classId, int teacherId) throws SQLException {
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name, " +
                "cl.allowed_languages, co.language AS course_lang, co.name AS course_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                "WHERE a.class_id=? AND (a.created_by=? OR cl.teacher_id=?) " +
                "ORDER BY a.opens_at DESC";
        JsonArray result = new JsonArray();
        LocalDateTime now = LocalDateTime.now(clock);
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        String sql = "SELECT a.id,a.class_id,a.title,a.opens_at,a.closes_at,a.duration_minutes,cl.class_code,cl.name class_name, " +
                "cl.allowed_languages, co.language AS course_lang, co.name AS course_name " +
                "FROM assessments a JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                "WHERE a.id=? AND (a.created_by=? OR cl.teacher_id=?)";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assessmentId);
            stmt.setInt(2, teacherId);
            stmt.setInt(3, teacherId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Không tìm thấy bài kiểm tra hoặc bạn không có quyền xem.");
                int classId = rs.getInt("class_id");
                JsonObject result = mapAssessment(conn, rs, null, LocalDateTime.now(clock), false);
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
        LocalDateTime now = LocalDateTime.now(clock);
        JsonObject active = findActiveForStudent(studentId, null);
        if (active != null && active.get("id").getAsInt() != assessmentId)
            throw new IllegalArgumentException("Hãy kết thúc bài kiểm tra hiện tại trước khi bắt đầu bài khác.");
        String find = "SELECT a.opens_at,a.closes_at,a.duration_minutes FROM assessments a " +
                "JOIN class_enrollments ce ON ce.class_id=a.class_id " +
                "WHERE a.id=? AND ce.student_id=? AND a.is_published=TRUE";
        try (Connection conn = connections.open()) {
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
        String sql = "UPDATE assessment_attempts SET completed_at=COALESCE(completed_at,?) " +
                "WHERE assessment_id=? AND student_id=?";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now(clock)));
            stmt.setInt(2, assessmentId);
            stmt.setInt(3, studentId);
            if (stmt.executeUpdate() != 1)
                throw new IllegalArgumentException("Bài kiểm tra chưa được bắt đầu.");
        }
    }

    private JsonObject mapAssessment(Connection conn, ResultSet rs, Integer studentId,
                                     LocalDateTime now, boolean includeProblems) throws SQLException {
        int id = rs.getInt("id");
        JsonObject result = mapAssessmentHeader(rs, now, countProblems(conn, id),
                studentId == null ? 0 : countSubmittedProblems(conn, id, studentId),
                studentId == null ? null : findAttempt(conn, id, studentId));
        attachProblems(conn, result, id, studentId, includeProblems);
        return result;
    }

    private void attachProblems(Connection conn, JsonObject result, int id, Integer studentId,
                                boolean includeProblems) throws SQLException {
        JsonArray problems = findProblems(conn, id, studentId);
        JsonArray ids = new JsonArray();
        for (var problem : problems) ids.add(problem.getAsJsonObject().get("id").getAsInt());
        result.add("problemIds", ids);
        if (includeProblems) result.add("problems", problems);
    }

    private JsonObject mapAssessmentHeader(ResultSet rs, LocalDateTime now, int problemCount,
                                           int submittedCount, Timestamp[] attempt) throws SQLException {
        int id = rs.getInt("id");
        LocalDateTime opensAt = rs.getTimestamp("opens_at").toLocalDateTime();
        LocalDateTime scheduledClose = rs.getTimestamp("closes_at").toLocalDateTime();
        int duration = rs.getInt("duration_minutes");
        LocalDateTime effectiveClose = opensAt.plusMinutes(duration);
        if (scheduledClose.isBefore(effectiveClose)) effectiveClose = scheduledClose;
        long remainingSeconds = Math.max(0, java.time.Duration.between(now, effectiveClose).getSeconds());

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

        result.addProperty("allowedLanguages", effectiveAllowedLanguages(rs));

        result.addProperty("title", rs.getString("title"));
        result.addProperty("opensAt", opensAt.toString());
        result.addProperty("closesAt", effectiveClose.toString());
        result.addProperty("scheduledClosesAt", scheduledClose.toString());
        result.addProperty("durationMinutes", duration);
        result.addProperty("remainingSeconds", remainingSeconds);
        result.addProperty("startsInSeconds", Math.max(0, java.time.Duration.between(now, opensAt).getSeconds()));
        result.addProperty("serverTime", now.toString());
        result.addProperty("status", status);
        result.addProperty("problemCount", problemCount);
        result.addProperty("submittedCount", submittedCount);
        result.addProperty("completed", completed);
        result.addProperty("started", started);
        if (attempt != null && attempt[0] != null)
            result.addProperty("startedAt", attempt[0].toLocalDateTime().toString());
        if (attempt != null && attempt[1] != null)
            result.addProperty("completedAt", attempt[1].toLocalDateTime().toString());
        return result;
    }

    private JsonArray findProblems(Connection conn, int assessmentId, Integer studentId) throws SQLException {
        String sql = "SELECT p.id, p.title, p.description, p.difficulty, p.time_limit_ms, p.memory_limit_kb, " +
                "a.class_id, cl.name AS class_name, cl.allowed_languages, co.language AS course_lang, co.name AS course_name, " +
                "p.problem_type, p.oop_spec, ap.order_num " +
                (studentId == null ? "" : ",latest.verdict AS latest_verdict,latest.score AS latest_score,latest.submitted_at AS last_submitted_at ") +
                "FROM assessment_problems ap " +
                "JOIN problems p ON p.id=ap.problem_id " +
                "JOIN assessments a ON a.id=ap.assessment_id " +
                "LEFT JOIN classes cl ON cl.id=a.class_id " +
                "LEFT JOIN courses co ON cl.course_id=co.id " +
                (studentId == null ? "" : "LEFT JOIN LATERAL (SELECT s.verdict::text AS verdict,s.score,s.submitted_at FROM submissions s " +
                        "WHERE s.assessment_id=ap.assessment_id AND s.user_id=? AND s.problem_id=p.id " +
                        "ORDER BY s.submitted_at DESC,s.id DESC LIMIT 1) latest ON TRUE ") +
                "WHERE ap.assessment_id=? ORDER BY ap.order_num";
        JsonArray result = new JsonArray();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (studentId != null) stmt.setInt(1, studentId);
            stmt.setInt(studentId == null ? 1 : 2, assessmentId);
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
                    String className = rs.getString("class_name");
                    if (className != null) problem.addProperty("className", className);

                    problem.addProperty("allowedLanguages", effectiveAllowedLanguages(rs));

                    String pType = rs.getString("problem_type");
                    if (pType != null) problem.addProperty("problemType", pType);
                    String oopSpec = rs.getString("oop_spec");
                    if (oopSpec != null) problem.addProperty("oopSpec", oopSpec);

                    if (studentId != null) {
                        Timestamp lastSubmitted = rs.getTimestamp("last_submitted_at");
                        problem.addProperty("submitted", lastSubmitted != null);
                        problem.addProperty("verdict", lastSubmitted == null ? "" : rs.getString("latest_verdict"));
                        if (lastSubmitted != null) {
                            problem.addProperty("score", rs.getInt("latest_score"));
                            problem.addProperty("lastSubmittedAt", lastSubmitted.toLocalDateTime().toString());
                        }
                    }
                    result.add(problem);
                }
            }
        }
        return result;
    }

    private String effectiveAllowedLanguages(ResultSet rs) throws SQLException {
        CreditClass creditClass = new CreditClass();
        creditClass.setName(rs.getString("class_name"));
        creditClass.setCourseName(rs.getString("course_name"));
        creditClass.setCourseLanguage(rs.getString("course_lang"));
        creditClass.setAllowedLanguages(rs.getString("allowed_languages"));
        return creditClass.getEffectiveAllowedLanguages();
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
