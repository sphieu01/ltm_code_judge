package com.grading.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database configuration — HikariCP connection pool cho PostgreSQL (Neon.db).
 *
 * Cách sử dụng:
 *   Connection conn = DatabaseConfig.getConnection();
 *   // ... dùng conn ...
 *   conn.close(); // trả về pool, không đóng thật
 *
 * Cấu hình kết nối Neon.db — thay các placeholder dưới đây:
 *   DB_URL      = jdbc:postgresql://<host>.neon.tech/<dbname>?sslmode=require
 *   DB_USER     = <neon-username>
 *   DB_PASSWORD = <neon-password>
 *
 * Có thể set qua environment variables hoặc sửa trực tiếp.
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    // ─── Cấu hình kết nối ─────────────────────────────────────────────────────
    // TODO: Thay bằng thông tin Neon.db thật của nhóm
    // Hoặc set environment variables: DB_URL, DB_USER, DB_PASSWORD
    private static final String DB_URL = getEnv(
            "DB_URL",
            "jdbc:postgresql://YOUR_NEON_HOST.neon.tech/YOUR_DB_NAME?sslmode=require"
    );
    private static final String DB_USER = getEnv("DB_USER", "YOUR_NEON_USERNAME");
    private static final String DB_PASSWORD = getEnv("DB_PASSWORD", "YOUR_NEON_PASSWORD");

    // ─── HikariCP pool settings ────────────────────────────────────────────────
    private static final int MAX_POOL_SIZE = 10;        // tối đa 10 connections
    private static final int MIN_IDLE = 2;              // giữ tối thiểu 2 idle
    private static final long CONNECTION_TIMEOUT = 30_000L; // 30s chờ lấy connection
    private static final long IDLE_TIMEOUT = 600_000L;     // 10 phút idle thì đóng
    private static final long MAX_LIFETIME = 1_800_000L;   // 30 phút lifetime tối đa

    private static HikariDataSource dataSource;

    // Static initializer — khởi tạo pool 1 lần khi class được load
    static {
        try {
            initPool();
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConfig() {}

    private static void initPool() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("org.postgresql.Driver");

        // Pool sizing
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setMaxLifetime(MAX_LIFETIME);

        // Pool name cho log
        config.setPoolName("CodeGradingPool");

        // Validation query — kiểm tra connection còn sống
        config.setConnectionTestQuery("SELECT 1");

        // PostgreSQL-specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        logger.info("Database connection pool initialized. URL: {}", DB_URL);
    }

    /**
     * Lấy một Connection từ pool.
     * Nhớ luôn gọi conn.close() trong finally block để trả về pool.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Đóng pool — gọi khi server shutdown.
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }

    /**
     * Kiểm tra pool có hoạt động không.
     */
    public static boolean isHealthy() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Đọc environment variable, fallback về defaultValue nếu không có */
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
