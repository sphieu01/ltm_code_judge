package com.grading.server.config;

/**
 * Cấu hình chung của Server.
 * Các hằng số có thể override bằng environment variables.
 */
public class ServerConfig {

    private ServerConfig() {}

    // ─── Network ──────────────────────────────────────────────────────────────
    /** Port server lắng nghe */
    public static final int SERVER_PORT = getEnvInt("SERVER_PORT", 9999);

    /** Kích thước thread pool cho client connections */
    public static final int CONNECTION_POOL_SIZE = getEnvInt("CONNECTION_POOL_SIZE", 20);

    /** Socket read timeout (ms) — server đóng connection nếu client không gửi gì sau thời gian này */
    public static final int SOCKET_TIMEOUT_MS = getEnvInt("SOCKET_TIMEOUT_MS", 300_000); // 5 phút

    /** Backlog — số connection chờ được accept */
    public static final int SOCKET_BACKLOG = 50;

    // ─── Judge Engine ─────────────────────────────────────────────────────────
    /** Số worker thread chấm bài song song */
    public static final int JUDGE_WORKER_COUNT = getEnvInt("JUDGE_WORKER_COUNT", 4);

    /** Dung lượng tối đa của JudgeQueue */
    public static final int JUDGE_QUEUE_CAPACITY = getEnvInt("JUDGE_QUEUE_CAPACITY", 100);

    // ─── JWT ──────────────────────────────────────────────────────────────────
    /**
     * Secret key cho JWT signing — THAY BẰNG GIÁ TRỊ NGẪU NHIÊN DÀI trong production!
     * Phải có ít nhất 256 bit (32 ký tự) cho HMAC-SHA256.
     */
    public static final String JWT_SECRET = getEnv(
            "JWT_SECRET",
            "MyVerySecretKeyForCodeGradingSystem2024!ThisIsLong"
    );

    /** JWT token expiry (milliseconds) — mặc định 24 giờ */
    public static final long JWT_EXPIRY_MS = 24L * 60 * 60 * 1000;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    private static int getEnvInt(String key, int defaultValue) {
        try {
            String v = System.getenv(key);
            return (v != null && !v.isBlank()) ? Integer.parseInt(v) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
