package com.grading.server.service;

import com.grading.server.config.ServerConfig;
import com.grading.server.model.User;
import com.grading.server.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Service xử lý Authentication và Authorization.
 *
 * Responsibilities:
 *   - Đăng ký user (hash password, lưu DB)
 *   - Đăng nhập (verify password, tạo JWT)
 *   - Verify JWT token (middleware cho mọi request cần auth)
 *
 * Password hashing: SHA-256 + salt (đơn giản, phù hợp môn học)
 * JWT: HMAC-SHA256 với secret key từ ServerConfig
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SecretKey jwtKey;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        // Derive key từ secret string — HMAC-SHA256 cần key >= 256 bit
        this.jwtKey = Keys.hmacShaKeyFor(
                ServerConfig.JWT_SECRET.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    /**
     * Đăng ký user mới.
     *
     * @throws IllegalArgumentException nếu username đã tồn tại hoặc input không hợp lệ
     * @throws SQLException nếu lỗi DB
     */
    public User register(String username, String password, String fullName, String role)
            throws IllegalArgumentException, SQLException {

        // Validate input
        validateUsername(username);
        validatePassword(password);

        // Kiểm tra duplicate
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' đã được sử dụng");
        }

        // Hash password với salt
        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);
        // Lưu salt cùng hash: "salt:hash"
        String storedHash = salt + ":" + passwordHash;

        // Tạo user
        String userRole = (role != null && role.equalsIgnoreCase("TEACHER")) ? "TEACHER" : "STUDENT";
        User user = new User(username, storedHash, fullName, userRole);

        User saved = userRepository.save(user);
        logger.info("Registered new user: username='{}', role='{}'", username, userRole);
        return saved;
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    /**
     * Đăng nhập.
     *
     * @return JWT token string nếu thành công
     * @throws IllegalArgumentException nếu username/password sai
     * @throws SQLException nếu lỗi DB
     */
    public String login(String username, String password)
            throws IllegalArgumentException, SQLException {

        Optional<User> optUser = userRepository.findByUsername(username);
        if (optUser.isEmpty()) {
            // Không phân biệt "username không tồn tại" vs "sai password" — security best practice
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        User user = optUser.get();
        if (!verifyPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        String token = generateJwt(user);
        logger.info("User '{}' (id={}) logged in", username, user.getId());
        return token;
    }

    // ─── JWT ──────────────────────────────────────────────────────────────────

    /**
     * Tạo JWT token cho user.
     * Claims: sub (userId), username, role, iat, exp
     */
    public String generateJwt(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ServerConfig.JWT_EXPIRY_MS);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtKey)
                .compact();
    }

    /**
     * Verify JWT token và trả về Claims.
     *
     * @return Claims nếu token hợp lệ
     * @throws JwtException nếu token không hợp lệ hoặc đã hết hạn
     */
    public Claims verifyToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(jwtKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Lấy userId từ JWT token.
     *
     * @return userId, hoặc -1 nếu token không hợp lệ
     */
    public int getUserIdFromToken(String token) {
        try {
            Claims claims = verifyToken(token);
            return Integer.parseInt(claims.getSubject());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Lấy role từ JWT token.
     *
     * @return "STUDENT" | "TEACHER", hoặc null nếu token không hợp lệ
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = verifyToken(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kiểm tra token có hợp lệ không (convenience method).
     */
    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            verifyToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // ─── Password Hashing ─────────────────────────────────────────────────────

    /** Tạo salt ngẫu nhiên 16 bytes */
    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /** Hash password với SHA-256 + salt */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Verify password với stored hash.
     * storedHash có format "salt:hash"
     */
    private boolean verifyPassword(String inputPassword, String storedHash) {
        try {
            String[] parts = storedHash.split(":", 2);
            if (parts.length != 2) return false;

            String salt = parts[0];
            String expectedHash = parts[1];
            String actualHash = hashPassword(inputPassword, salt);

            // Constant-time comparison để chống timing attack
            return MessageDigest.isEqual(
                    actualHash.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("Username phải từ 3–50 ký tự");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Username chỉ được chứa chữ cái, số và dấu gạch dưới");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password phải có ít nhất 6 ký tự");
        }
    }
}
