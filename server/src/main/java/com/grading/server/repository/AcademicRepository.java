package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.Course;
import com.grading.server.model.CreditClass;
import com.grading.server.model.Problem;
import com.grading.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository quản lý Môn học (Courses), Lớp tín chỉ (Classes),
 * và mối quan hệ Sinh viên - Lớp tín chỉ.
 */
public class AcademicRepository {

    private static final Logger logger = LoggerFactory.getLogger(AcademicRepository.class);

    public AcademicRepository() {
        initSchemaIfNeeded();
    }

    /**
     * Tự động tạo bảng courses, classes và cột liên kết nếu chưa tồn tại trong DB.
     */
    private void initSchemaIfNeeded() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Tạo bảng courses
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS courses (" +
                    "  id SERIAL PRIMARY KEY," +
                    "  code VARCHAR(50) UNIQUE NOT NULL," +
                    "  name VARCHAR(150) NOT NULL," +
                    "  language VARCHAR(20) DEFAULT 'cpp'," +
                    "  description TEXT," +
                    "  is_active BOOLEAN DEFAULT TRUE," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );

            // Đảm bảo cột is_active tồn tại nếu bảng đã tạo từ trước
            stmt.executeUpdate(
                    "ALTER TABLE courses ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE"
            );

            // 2. Tạo bảng classes
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS classes (" +
                    "  id SERIAL PRIMARY KEY," +
                    "  class_code VARCHAR(50) UNIQUE NOT NULL," +
                    "  name VARCHAR(150) NOT NULL," +
                    "  course_id INT REFERENCES courses(id) ON DELETE CASCADE," +
                    "  teacher_id INT REFERENCES users(id) ON DELETE SET NULL," +
                    "  allowed_languages VARCHAR(100) DEFAULT 'c,cpp,java,python'," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );
            stmt.executeUpdate(
                    "ALTER TABLE classes ADD COLUMN IF NOT EXISTS allowed_languages VARCHAR(100) DEFAULT 'c,cpp,java,python'"
            );
            // Tự động đồng bộ ngôn ngữ lớp học theo ngôn ngữ của môn học nếu lớp chưa được cấu hình riêng
            stmt.executeUpdate(
                    "UPDATE classes cl SET allowed_languages = " +
                    "  CASE " +
                    "    WHEN LOWER(TRIM(co.language)) = 'cpp' THEN 'cpp,c' " +
                    "    ELSE LOWER(TRIM(co.language)) " +
                    "  END " +
                    "FROM courses co WHERE cl.course_id = co.id AND (cl.allowed_languages IS NULL OR cl.allowed_languages = 'c,cpp,java,python')"
            );

            // 3. Đảm bảo bảng class_enrollments tồn tại
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS class_enrollments (" +
                    "class_id INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE," +
                    "student_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE," +
                    "enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (class_id, student_id))"
            );

            // 3.1. Di chuyển dữ liệu từ users.class_id (nếu còn) sang class_enrollments rồi xóa cột class_id
            try {
                stmt.executeUpdate(
                        "DO $$ BEGIN " +
                        "  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'class_id') THEN " +
                        "    INSERT INTO class_enrollments (class_id, student_id) " +
                        "    SELECT class_id, id FROM users " +
                        "    WHERE UPPER(TRIM(role::text)) = 'STUDENT' AND class_id IS NOT NULL " +
                        "    ON CONFLICT (class_id, student_id) DO NOTHING; " +
                        "    ALTER TABLE users DROP COLUMN class_id CASCADE; " +
                        "  END IF; " +
                        "END $$;"
                );
            } catch (SQLException e) {
                logger.warn("Notice migrating/dropping users.class_id: {}", e.getMessage());
            }

            // 4. Bảng quan hệ Nhiều-Nhiều class_problems
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS class_problems (" +
                    "  id SERIAL PRIMARY KEY," +
                    "  class_id INT NOT NULL REFERENCES classes(id) ON DELETE CASCADE," +
                    "  problem_id INT NOT NULL REFERENCES problems(id) ON DELETE CASCADE," +
                    "  is_open BOOLEAN NOT NULL DEFAULT TRUE," +
                    "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  UNIQUE (class_id, problem_id))"
            );

            // 4.1. Di chuyển dữ liệu từ problems.class_id (nếu còn) sang class_problems rồi xóa cột class_id
            try {
                stmt.executeUpdate(
                        "DO $$ BEGIN " +
                        "  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'problems' AND column_name = 'class_id') THEN " +
                        "    INSERT INTO class_problems (class_id, problem_id) " +
                        "    SELECT class_id, id FROM problems WHERE class_id IS NOT NULL " +
                        "    ON CONFLICT (class_id, problem_id) DO NOTHING; " +
                        "    ALTER TABLE problems DROP COLUMN class_id CASCADE; " +
                        "  END IF; " +
                        "END $$;"
                );
            } catch (SQLException e) {
                logger.warn("Notice migrating/dropping problems.class_id: {}", e.getMessage());
            }

            // 4.5. Đảm bảo luôn có sẵn tài khoản Giáo viên (Admin) mặc định
            ensureAdminUser(conn, "teacher01", "password123", "Giảng Viên Quản Trị");
            ensureAdminUser(conn, "admin", "password123", "Quản Trị Viên Hệ Thống");

            // 5. Thêm dữ liệu mẫu môn học nếu rỗng
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM courses")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                            "INSERT INTO courses (code, name, language, description, is_active) VALUES " +
                            "('INT2204', 'Lập trình C/C++', 'cpp', 'Môn học cơ sở lập trình hướng đối tượng và cấu trúc dữ liệu với C++', TRUE)," +
                            "('INT2205', 'Lập trình Java', 'java', 'Môn học lập trình ứng dụng và mạng với ngôn ngữ Java', TRUE)," +
                            "('INT2206', 'Lập trình Python', 'python', 'Môn học lập trình xử lý dữ liệu và kịch bản với Python', TRUE)"
                    );
                    logger.info("Inserted default courses into database.");
                }
            }

            // 6. Thêm dữ liệu mẫu lớp tín chỉ nếu rỗng
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM classes")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                            "INSERT INTO classes (class_code, name, course_id, teacher_id, allowed_languages) VALUES " +
                            "('CPP_01', 'Lớp C++ Nhóm 1 - Sáng T2', (SELECT id FROM courses WHERE code='INT2204' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1), 'cpp,c')," +
                            "('JAVA_01', 'Lớp Java Nhóm 2 - Chiều T4', (SELECT id FROM courses WHERE code='INT2205' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1), 'java')," +
                            "('PY_01', 'Lớp Python Nhóm 1 - Sáng T6', (SELECT id FROM courses WHERE code='INT2206' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1), 'python')"
                    );
                    logger.info("Inserted default classes into database.");
                }
            }

            // 7. Đồng bộ chuẩn hóa allowed_languages cho các lớp dựa trên môn học liên kết
            try {
                stmt.executeUpdate(
                        "UPDATE classes cl SET allowed_languages = " +
                        "  CASE " +
                        "    WHEN LOWER(co.language) = 'java' OR LOWER(co.name) LIKE '%java%' OR LOWER(co.name) LIKE '%oop%' OR LOWER(co.name) LIKE '%hướng đối tượng%' THEN 'java' " +
                        "    WHEN LOWER(co.language) = 'python' OR LOWER(co.name) LIKE '%python%' THEN 'python' " +
                        "    WHEN LOWER(co.language) IN ('cpp', 'c') OR LOWER(co.name) LIKE '%c++%' OR LOWER(co.name) LIKE '%c/c++%' THEN 'cpp,c' " +
                        "    ELSE 'c,cpp,java,python' " +
                        "  END " +
                        "FROM courses co WHERE cl.course_id = co.id " +
                        "AND (cl.allowed_languages IS NULL OR cl.allowed_languages = '' OR cl.allowed_languages = 'c,cpp,java,python')"
                );
            } catch (SQLException e) {
                logger.warn("Notice updating class allowed_languages: {}", e.getMessage());
            }

            logger.info("Academic schema verified successfully.");

        } catch (SQLException e) {
            logger.warn("Schema initialization notice: {}", e.getMessage());
        }
    }

    // ─── COURSES ─────────────────────────────────────────────────────────────

    public List<Course> findAllCourses() throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT id, code, name, language, description, is_active, created_at FROM courses ORDER BY id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Course c = new Course();
                c.setId(rs.getInt("id"));
                c.setCode(rs.getString("code"));
                c.setName(rs.getString("name"));
                c.setLanguage(rs.getString("language"));
                c.setDescription(rs.getString("description"));
                c.setActive(rs.getObject("is_active") == null || rs.getBoolean("is_active"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) c.setCreatedAt(ts.toString());
                list.add(c);
            }
        }
        return list;
    }

    public Course createCourse(Course course) throws SQLException {
        String sql = "INSERT INTO courses (code, name, language, description, is_active) VALUES (?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, course.getCode());
            stmt.setString(2, course.getName());
            stmt.setString(3, course.getLanguage() != null ? course.getLanguage() : "cpp");
            stmt.setString(4, course.getDescription());
            stmt.setBoolean(5, course.isActive());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    course.setId(rs.getInt("id"));
                    course.setCreatedAt(rs.getTimestamp("created_at").toString());
                    return course;
                }
            }
        }
        throw new SQLException("Failed to create course");
    }

    public void updateCourseActive(int courseId, boolean active) throws SQLException {
        String sql = "UPDATE courses SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setInt(2, courseId);
            stmt.executeUpdate();
        }
    }

    public void updateCourse(Course course) throws SQLException {
        String sql = "UPDATE courses SET code = ?, name = ?, language = ?, description = ?, is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, course.getCode());
            stmt.setString(2, course.getName());
            stmt.setString(3, course.getLanguage());
            stmt.setString(4, course.getDescription());
            stmt.setBoolean(5, course.isActive());
            stmt.setInt(6, course.getId());
            if (stmt.executeUpdate() == 0) throw new SQLException("Không tìm thấy môn học id=" + course.getId());
        }
    }

    /** Xóa môn học. Các lớp thuộc môn sẽ bị xóa theo FK; sinh viên/bài tập được bỏ gán lớp. */
    public void deleteCourse(int courseId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM courses WHERE id = ?")) {
            stmt.setInt(1, courseId);
            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Không tìm thấy môn học id=" + courseId);
            }
        }
    }

    // ─── CLASSES ─────────────────────────────────────────────────────────────

    private CreditClass mapCreditClass(ResultSet rs) throws SQLException {
        CreditClass cc = new CreditClass();
        cc.setId(rs.getInt("id"));
        cc.setClassCode(rs.getString("class_code"));
        cc.setName(rs.getString("name"));
        cc.setCourseId(rs.getInt("course_id"));
        cc.setCourseName(rs.getString("course_name"));
        cc.setCourseLanguage(rs.getString("course_lang"));
        cc.setTeacherId(rs.getInt("teacher_id"));
        try {
            cc.setStudentCount(rs.getInt("student_count"));
        } catch (SQLException ignored) {}
        try {
            String allowed = rs.getString("allowed_languages");
            if (allowed != null && !allowed.isBlank()) {
                cc.setAllowedLanguages(allowed);
            }
        } catch (SQLException ignored) {}
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) cc.setCreatedAt(ts.toString());
        return cc;
    }

    public List<CreditClass> findAllClasses() throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.allowed_languages, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM class_enrollments ce WHERE ce.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapCreditClass(rs));
            }
        }
        return list;
    }

    public List<CreditClass> findActiveClasses() throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.allowed_languages, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM class_enrollments ce WHERE ce.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "WHERE (co.is_active IS NULL OR co.is_active = TRUE) " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapCreditClass(rs));
            }
        }
        return list;
    }

    public List<CreditClass> findClassesByStudent(int studentId) throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.allowed_languages, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM class_enrollments ce2 WHERE ce2.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "JOIN class_enrollments ce ON ce.class_id = cl.id " +
                     "WHERE ce.student_id = ? AND (co.is_active IS NULL OR co.is_active = TRUE) " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCreditClass(rs));
                }
            }
        }
        return list;
    }

    public CreditClass findClassById(int classId) throws SQLException {
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.allowed_languages, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM class_enrollments ce WHERE ce.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "WHERE cl.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapCreditClass(rs);
                }
            }
        }
        return null;
    }

    public CreditClass createClass(CreditClass creditClass) throws SQLException {
        if (creditClass.getAllowedLanguages() == null || creditClass.getAllowedLanguages().isBlank()) {
            creditClass.setAllowedLanguages(creditClass.getEffectiveAllowedLanguages());
        }
        String sql = "INSERT INTO classes (class_code, name, course_id, teacher_id, allowed_languages) VALUES (?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, creditClass.getClassCode());
            stmt.setString(2, creditClass.getName());
            stmt.setInt(3, creditClass.getCourseId());
            if (creditClass.getTeacherId() > 0) {
                stmt.setInt(4, creditClass.getTeacherId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, creditClass.getAllowedLanguages());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    creditClass.setId(rs.getInt("id"));
                    creditClass.setCreatedAt(rs.getTimestamp("created_at").toString());
                    return creditClass;
                }
            }
        }
        throw new SQLException("Failed to create class");
    }

    public void updateClass(int id, String classCode, String name, int courseId, String allowedLanguages) throws SQLException {
        String sql = "UPDATE classes SET class_code = ?, name = ?, course_id = ?, allowed_languages = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, classCode);
            stmt.setString(2, name);
            stmt.setInt(3, courseId);
            stmt.setString(4, allowedLanguages != null && !allowedLanguages.isBlank() ? allowedLanguages : "c,cpp,java,python");
            stmt.setInt(5, id);
            stmt.executeUpdate();
        }
    }

    public void updateClass(int id, String classCode, String name, int courseId) throws SQLException {
        updateClass(id, classCode, name, courseId, "c,cpp,java,python");
    }

    // ─── CLASS - PROBLEMS (Quan hệ Nhiều - Nhiều) ───────────────────────────

    public List<Problem> findProblemsByClassId(int classId) throws SQLException {
        List<Problem> list = new ArrayList<>();
        String sql = "SELECT DISTINCT p.id, p.title, p.description, p.time_limit_ms, p.memory_limit_kb, p.difficulty, " +
                     "p.created_by, cl.id AS class_id, cl.name AS class_name, p.is_active, p.created_at, " +
                     "p.problem_type, p.oop_spec " +
                     "FROM problems p " +
                     "JOIN class_problems cp ON p.id = cp.problem_id " +
                     "JOIN classes cl ON cl.id = cp.class_id " +
                     "WHERE cp.class_id = ? " +
                     "AND (p.is_active IS NULL OR p.is_active = TRUE) " +
                     "ORDER BY p.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Problem p = new Problem();
                    p.setId(rs.getInt("id"));
                    p.setTitle(rs.getString("title"));
                    p.setDescription(rs.getString("description"));
                    p.setTimeLimitMs(rs.getInt("time_limit_ms"));
                    p.setMemoryLimitKb(rs.getInt("memory_limit_kb"));
                    p.setDifficulty(rs.getString("difficulty"));
                    p.setCreatedBy(rs.getInt("created_by"));
                    p.setClassId(classId);
                    String cName = rs.getString("class_name");
                    p.setClassName(cName != null ? cName : "");
                    p.setActive(rs.getObject("is_active") == null || rs.getBoolean("is_active"));
                    try { p.setProblemType(rs.getString("problem_type")); } catch (Exception ignored) {}
                    try { p.setOopSpec(rs.getString("oop_spec")); } catch (Exception ignored) {}
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) p.setCreatedAt(ts.toString());
                    list.add(p);
                }
            }
        }
        return list;
    }

    public void assignProblemsToClass(int classId, List<Integer> problemIds) throws SQLException {
        if (problemIds == null || problemIds.isEmpty()) return;
        String sql = "INSERT INTO class_problems (class_id, problem_id) VALUES (?, ?) ON CONFLICT (class_id, problem_id) DO NOTHING";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int pid : problemIds) {
                stmt.setInt(1, classId);
                stmt.setInt(2, pid);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void removeProblemFromClass(int classId, int problemId) throws SQLException {
        String sql = "DELETE FROM class_problems WHERE class_id = ? AND problem_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            stmt.setInt(2, problemId);
            stmt.executeUpdate();
        }
    }

    public void deleteClass(int classId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement s3 = conn.prepareStatement("DELETE FROM classes WHERE id = ?")) {
            s3.setInt(1, classId);
            s3.executeUpdate();
        }
    }

    // ─── STUDENTS ────────────────────────────────────────────────────────────

    public List<User.UserDTO> findStudentsByClass(int classId) throws SQLException {
        List<User.UserDTO> list = new ArrayList<>();
        String sql = classId > 0
                ? "SELECT u.id, u.username, u.full_name, u.role, ce.class_id, cl.name AS class_name, u.created_at " +
                  "FROM class_enrollments ce JOIN users u ON u.id = ce.student_id " +
                  "JOIN classes cl ON cl.id = ce.class_id " +
                  "WHERE UPPER(TRIM(u.role::text)) = 'STUDENT' AND ce.class_id = ? ORDER BY u.id DESC"
                : "SELECT u.id, u.username, u.full_name, u.role, MIN(ce.class_id) AS class_id, " +
                  "COALESCE(STRING_AGG(cl.name, ', ' ORDER BY cl.name), '') AS class_name, u.created_at " +
                  "FROM users u LEFT JOIN class_enrollments ce ON ce.student_id = u.id " +
                  "LEFT JOIN classes cl ON cl.id = ce.class_id " +
                  "WHERE UPPER(TRIM(u.role::text)) = 'STUDENT' " +
                  "GROUP BY u.id, u.username, u.full_name, u.role, u.created_at ORDER BY u.id DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (classId > 0) {
                stmt.setInt(1, classId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Integer cId = rs.getObject("class_id") != null ? rs.getInt("class_id") : null;
                    String cName = rs.getString("class_name");
                    Timestamp ts = rs.getTimestamp("created_at");
                    list.add(new User.UserDTO(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            cId,
                            cName,
                            ts != null ? ts.toString() : null
                    ));
                }
            }
        }
        return list;
    }

    public void assignStudentToClass(int studentId, int classId) throws SQLException {
        String sql = "INSERT INTO class_enrollments (class_id, student_id) VALUES (?, ?) " +
                "ON CONFLICT (class_id, student_id) DO NOTHING";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, classId);
            stmt.setInt(2, studentId);
            stmt.executeUpdate();
        }
    }

    public void updateStudent(int studentId, String fullName, Integer classId, String newPassword) throws SQLException {
        boolean hasNewPassword = (newPassword != null && !newPassword.isBlank());
        String sql = hasNewPassword
                ? "UPDATE users SET full_name = ?, password_hash = ? WHERE id = ? AND role = 'STUDENT'"
                : "UPDATE users SET full_name = ? WHERE id = ? AND role = 'STUDENT'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fullName);
            if (hasNewPassword) {
                byte[] saltBytes = new byte[16];
                new java.security.SecureRandom().nextBytes(saltBytes);
                String salt = java.util.Base64.getEncoder().encodeToString(saltBytes);
                try {
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                    digest.update(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    byte[] hashBytes = digest.digest(newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String passwordHash = java.util.Base64.getEncoder().encodeToString(hashBytes);
                    String storedHash = salt + ":" + passwordHash;
                    stmt.setString(2, storedHash);
                    stmt.setInt(3, studentId);
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new SQLException("SHA-256 algorithm not available", e);
                }
            } else {
                stmt.setInt(2, studentId);
            }

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Không tìm thấy sinh viên id=" + studentId + " hoặc không có quyền sửa.");
            }
        }
        if (classId != null && classId > 0) assignStudentToClass(studentId, classId);
    }

    public void removeStudentFromClass(int studentId, int classId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement remove = conn.prepareStatement(
                     "DELETE FROM class_enrollments WHERE student_id = ? AND class_id = ?")) {
            remove.setInt(1, studentId);
            remove.setInt(2, classId);
            if (remove.executeUpdate() == 0) {
                throw new SQLException("Sinh viên không thuộc lớp tín chỉ này.");
            }
        }
    }

    public void deleteStudent(int studentId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Xóa các bài nộp của sinh viên này trước
                try (PreparedStatement delSubs = conn.prepareStatement("DELETE FROM submissions WHERE user_id = ?")) {
                    delSubs.setInt(1, studentId);
                    delSubs.executeUpdate();
                }

                // 2. Xóa sinh viên
                try (PreparedStatement delUser = conn.prepareStatement("DELETE FROM users WHERE id = ? AND role = 'STUDENT'")) {
                    delUser.setInt(1, studentId);
                    int affected = delUser.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("Không tìm thấy sinh viên id=" + studentId + " hoặc không thể xóa tài khoản này.");
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void ensureAdminUser(Connection conn, String username, String password, String fullName) {
        try {
            boolean exists = false;
            try (PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                check.setString(1, username);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) exists = true;
                }
            }

            byte[] saltBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(saltBytes);
            String salt = java.util.Base64.getEncoder().encodeToString(saltBytes);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String passwordHash = java.util.Base64.getEncoder().encodeToString(hashBytes);
            String storedHash = salt + ":" + passwordHash;

            if (!exists) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)")) {
                    ins.setString(1, username);
                    ins.setString(2, storedHash);
                    ins.setString(3, fullName);
                    ins.setString(4, "TEACHER");
                    ins.executeUpdate();
                    logger.info("Auto-seeded admin account: username='{}', role='TEACHER'", username);
                }
            } else {
                // Never overwrite an existing password: doing so would undo a user's password change
                // every time the server starts.
                try (PreparedStatement updateRole = conn.prepareStatement(
                        "UPDATE users SET role = 'TEACHER' WHERE username = ? AND role <> 'TEACHER'")) {
                    updateRole.setString(1, username);
                    updateRole.executeUpdate();
                }
                logger.debug("Default admin user '{}' already exists; preserving credentials", username);
            }
        } catch (Exception ex) {
            logger.warn("Could not ensure admin user {}: {}", username, ex.getMessage());
        }
    }
}
