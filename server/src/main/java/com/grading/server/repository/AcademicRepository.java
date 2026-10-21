package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.Course;
import com.grading.server.model.CreditClass;
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
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            );

            // 3. Bổ sung cột class_id vào users nếu chưa có
            stmt.executeUpdate(
                    "ALTER TABLE users ADD COLUMN IF NOT EXISTS class_id INT REFERENCES classes(id) ON DELETE SET NULL"
            );

            // 4. Bổ sung cột class_id vào problems nếu chưa có
            stmt.executeUpdate(
                    "ALTER TABLE problems ADD COLUMN IF NOT EXISTS class_id INT REFERENCES classes(id) ON DELETE SET NULL"
            );

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
                            "INSERT INTO classes (class_code, name, course_id, teacher_id) VALUES " +
                            "('CPP_01', 'Lớp C++ Nhóm 1 - Sáng T2', (SELECT id FROM courses WHERE code='INT2204' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1))," +
                            "('JAVA_01', 'Lớp Java Nhóm 2 - Chiều T4', (SELECT id FROM courses WHERE code='INT2205' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1))," +
                            "('PY_01', 'Lớp Python Nhóm 1 - Sáng T6', (SELECT id FROM courses WHERE code='INT2206' LIMIT 1), (SELECT id FROM users WHERE role='TEACHER' LIMIT 1))"
                    );
                    logger.info("Inserted default classes into database.");
                }
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

    public List<CreditClass> findAllClasses() throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM users u WHERE u.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CreditClass cc = new CreditClass();
                cc.setId(rs.getInt("id"));
                cc.setClassCode(rs.getString("class_code"));
                cc.setName(rs.getString("name"));
                cc.setCourseId(rs.getInt("course_id"));
                cc.setCourseName(rs.getString("course_name"));
                cc.setCourseLanguage(rs.getString("course_lang"));
                cc.setTeacherId(rs.getInt("teacher_id"));
                cc.setStudentCount(rs.getInt("student_count"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) cc.setCreatedAt(ts.toString());
                list.add(cc);
            }
        }
        return list;
    }

    public List<CreditClass> findActiveClasses() throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM users u WHERE u.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "WHERE (co.is_active IS NULL OR co.is_active = TRUE) " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CreditClass cc = new CreditClass();
                cc.setId(rs.getInt("id"));
                cc.setClassCode(rs.getString("class_code"));
                cc.setName(rs.getString("name"));
                cc.setCourseId(rs.getInt("course_id"));
                cc.setCourseName(rs.getString("course_name"));
                cc.setCourseLanguage(rs.getString("course_lang"));
                cc.setTeacherId(rs.getInt("teacher_id"));
                cc.setStudentCount(rs.getInt("student_count"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) cc.setCreatedAt(ts.toString());
                list.add(cc);
            }
        }
        return list;
    }

    public List<CreditClass> findClassesByStudent(int studentId) throws SQLException {
        List<CreditClass> list = new ArrayList<>();
        String sql = "SELECT cl.id, cl.class_code, cl.name, cl.course_id, cl.teacher_id, cl.created_at, " +
                     "co.name AS course_name, co.language AS course_lang, " +
                     "(SELECT COUNT(*) FROM users u WHERE u.class_id = cl.id) AS student_count " +
                     "FROM classes cl " +
                     "LEFT JOIN courses co ON cl.course_id = co.id " +
                     "JOIN users u ON u.class_id = cl.id " +
                     "WHERE u.id = ? AND (co.is_active IS NULL OR co.is_active = TRUE) " +
                     "ORDER BY cl.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CreditClass cc = new CreditClass();
                    cc.setId(rs.getInt("id"));
                    cc.setClassCode(rs.getString("class_code"));
                    cc.setName(rs.getString("name"));
                    cc.setCourseId(rs.getInt("course_id"));
                    cc.setCourseName(rs.getString("course_name"));
                    cc.setCourseLanguage(rs.getString("course_lang"));
                    cc.setTeacherId(rs.getInt("teacher_id"));
                    cc.setStudentCount(rs.getInt("student_count"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) cc.setCreatedAt(ts.toString());
                    list.add(cc);
                }
            }
        }
        // Nếu sinh viên chưa được gán lớp nào, trả về toàn bộ danh sách lớp của các môn đang mở để sinh viên có thể chọn học
        if (list.isEmpty()) {
            return findActiveClasses();
        }
        return list;
    }

    public CreditClass createClass(CreditClass creditClass) throws SQLException {
        String sql = "INSERT INTO classes (class_code, name, course_id, teacher_id) VALUES (?, ?, ?, ?) RETURNING id, created_at";
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

    public void updateClass(int id, String classCode, String name, int courseId) throws SQLException {
        String sql = "UPDATE classes SET class_code = ?, name = ?, course_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, classCode);
            stmt.setString(2, name);
            stmt.setInt(3, courseId);
            stmt.setInt(4, id);
            stmt.executeUpdate();
        }
    }

    public void deleteClass(int classId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Gỡ sinh viên khỏi lớp bị xóa (đưa về chưa gán lớp)
                try (PreparedStatement s1 = conn.prepareStatement("UPDATE users SET class_id = NULL WHERE class_id = ?")) {
                    s1.setInt(1, classId);
                    s1.executeUpdate();
                }
                // 2. Chuyển bài tập gán cho lớp này thành bài tập chung
                try (PreparedStatement s2 = conn.prepareStatement("UPDATE problems SET class_id = NULL WHERE class_id = ?")) {
                    s2.setInt(1, classId);
                    s2.executeUpdate();
                }
                // 3. Xóa lớp tín chỉ
                try (PreparedStatement s3 = conn.prepareStatement("DELETE FROM classes WHERE id = ?")) {
                    s3.setInt(1, classId);
                    s3.executeUpdate();
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

    // ─── STUDENTS ────────────────────────────────────────────────────────────

    public List<User.UserDTO> findStudentsByClass(int classId) throws SQLException {
        List<User.UserDTO> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.full_name, u.role, u.class_id, cl.name AS class_name, u.created_at " +
                     "FROM users u " +
                     "LEFT JOIN classes cl ON u.class_id = cl.id " +
                     "WHERE u.role = 'STUDENT' " +
                     (classId > 0 ? "AND u.class_id = ? " : "") +
                     "ORDER BY u.id DESC";
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
        String sql = "UPDATE users SET class_id = ? WHERE id = ?";
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
                ? "UPDATE users SET full_name = ?, class_id = ?, password_hash = ? WHERE id = ? AND role = 'STUDENT'"
                : "UPDATE users SET full_name = ?, class_id = ? WHERE id = ? AND role = 'STUDENT'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fullName);
            if (classId != null && classId > 0) {
                stmt.setInt(2, classId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

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
                    stmt.setString(3, storedHash);
                    stmt.setInt(4, studentId);
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new SQLException("SHA-256 algorithm not available", e);
                }
            } else {
                stmt.setInt(3, studentId);
            }

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Không tìm thấy sinh viên id=" + studentId + " hoặc không có quyền sửa.");
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

            if (exists) {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE users SET password_hash = ?, role = 'TEACHER' WHERE username = ?")) {
                    upd.setString(1, storedHash);
                    upd.setString(2, username);
                    upd.executeUpdate();
                    logger.info("Updated password hash for admin user '{}'", username);
                }
            } else {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)")) {
                    ins.setString(1, username);
                    ins.setString(2, storedHash);
                    ins.setString(3, fullName);
                    ins.setString(4, "TEACHER");
                    ins.executeUpdate();
                    logger.info("Auto-seeded admin account: username='{}', role='TEACHER'", username);
                }
            }
        } catch (Exception ex) {
            logger.warn("Could not ensure admin user {}: {}", username, ex.getMessage());
        }
    }
}
