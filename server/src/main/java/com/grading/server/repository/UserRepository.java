package com.grading.server.repository;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

/**
 * Repository xử lý CRUD cho bảng `users`.
 *
 * Tất cả SQL dùng PreparedStatement để chống SQL Injection.
 * Mọi exception được log và re-throw (hoặc wrap) để service xử lý.
 */
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    // ─── SQL Queries ──────────────────────────────────────────────────────────
    private static final String SQL_FIND_BY_ID =
            "SELECT id, username, password_hash, full_name, role, created_at " +
            "FROM users WHERE id = ?";

    private static final String SQL_FIND_BY_USERNAME =
            "SELECT id, username, password_hash, full_name, role, created_at " +
            "FROM users WHERE username = ?";

    private static final String SQL_INSERT =
            "INSERT INTO users (username, password_hash, full_name, role) " +
            "VALUES (?, ?, ?, ?::user_role) RETURNING id, created_at";

    private static final String SQL_EXISTS_USERNAME =
            "SELECT COUNT(*) FROM users WHERE username = ?";

    // ─── Public Methods ───────────────────────────────────────────────────────

    /**
     * Tìm user theo ID.
     *
     * @return Optional.empty() nếu không tìm thấy
     */
    public Optional<User> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id={}", id, e);
            throw e;
        }
        return Optional.empty();
    }

    /**
     * Tìm user theo username.
     *
     * @return Optional.empty() nếu không tìm thấy
     */
    public Optional<User> findByUsername(String username) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USERNAME)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username='{}'", username, e);
            throw e;
        }
        return Optional.empty();
    }

    /**
     * Kiểm tra username đã tồn tại chưa.
     */
    public boolean existsByUsername(String username) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_USERNAME)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking username existence: '{}'", username, e);
            throw e;
        }
        return false;
    }

    /**
     * Tạo user mới.
     * Password phải được hash trước khi gọi method này.
     *
     * @return User đã được tạo với id và createdAt được set từ DB
     */
    public User save(User user) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getRole() != null ? user.getRole() : "STUDENT");

            try (ResultSet rs = stmt.executeQuery()) { // RETURNING clause
                if (rs.next()) {
                    user.setId(rs.getInt("id"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toString());
                    logger.info("Created user: id={}, username='{}'", user.getId(), user.getUsername());
                    return user;
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving user username='{}'", user.getUsername(), e);
            throw e;
        }
        throw new SQLException("Failed to create user, no ID returned");
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /** Map một ResultSet row thành một User object */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(rs.getString("role"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toString());
        }
        return user;
    }
}
