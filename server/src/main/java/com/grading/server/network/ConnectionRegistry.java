package com.grading.server.network;

import com.grading.server.model.Submission;
import com.grading.server.protocol.Message;
import com.grading.server.protocol.MessageSerializer;
import com.grading.server.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConnectionRegistry — Quản lý các active TCP connections.
 *
 * Lưu map: userId → PrintWriter (socket output stream)
 * Cho phép server push message về client bất kỳ lúc nào (JUDGE_NOTIFICATION).
 *
 * Thread-safety: ConcurrentHashMap — nhiều ClientHandler threads có thể
 * register/unregister/send đồng thời mà không cần synchronized.
 *
 * Đây là cơ chế cho phép server PUSH kết quả về client sau khi chấm xong,
 * thay vì client phải poll liên tục — đây là một trong các "Contributions"
 * quan trọng trong báo cáo.
 */
public class ConnectionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionRegistry.class);

    /** Map userId → PrintWriter của socket tương ứng */
    private final ConcurrentHashMap<Integer, PrintWriter> activeConnections = new ConcurrentHashMap<>();

    /** Singleton instance */
    private static final ConnectionRegistry INSTANCE = new ConnectionRegistry();

    private ConnectionRegistry() {}

    public static ConnectionRegistry getInstance() {
        return INSTANCE;
    }

    // ─── Register / Unregister ────────────────────────────────────────────────

    /**
     * Đăng ký kết nối của user vừa đăng nhập.
     * ClientHandler gọi sau khi xử lý LOGIN thành công.
     */
    public void register(int userId, PrintWriter writer) {
        PrintWriter old = activeConnections.put(userId, writer);
        if (old != null) {
            logger.warn("User {} already had an active connection — replaced (multiple login?)", userId);
        }
        logger.debug("Registered connection for userId={}, total active: {}", userId, activeConnections.size());
    }

    /**
     * Hủy đăng ký kết nối khi client disconnect.
     * ClientHandler gọi trong cleanup.
     */
    public void unregister(int userId) {
        PrintWriter removed = activeConnections.remove(userId);
        if (removed != null) {
            logger.debug("Unregistered connection for userId={}, remaining: {}", userId, activeConnections.size());
        }
    }

    /**
     * Kiểm tra user có đang online không.
     */
    public boolean isOnline(int userId) {
        return activeConnections.containsKey(userId);
    }

    /**
     * Số kết nối đang active.
     */
    public int getActiveCount() {
        return activeConnections.size();
    }

    // ─── Push Notification ────────────────────────────────────────────────────

    /**
     * Gửi JUDGE_NOTIFICATION về client sau khi chấm xong.
     * Được gọi từ JudgeWorker (thread riêng, không phải ClientHandler thread).
     *
     * @param submission Submission đã có verdict, score, passedTests, ...
     */
    public void sendJudgeNotification(Submission submission) {
        int userId = submission.getUserId();
        PrintWriter writer = activeConnections.get(userId);

        if (writer == null) {
            logger.debug("User {} is offline, JUDGE_NOTIFICATION not sent for submission {}",
                    userId, submission.getId());
            return; // Client offline — họ sẽ dùng GET_RESULT sau khi reconnect
        }

        // Build notification message
        Message notification = new Message(MessageType.JUDGE_NOTIFICATION, null);
        notification.setStatus("SUCCESS");
        notification.setData(MessageSerializer.toJsonElement(buildNotificationData(submission)));

        sendMessage(userId, writer, notification);
        logger.info("JUDGE_NOTIFICATION sent to userId={} for submission id={} ({})",
                userId, submission.getId(), submission.getVerdict());
    }

    /**
     * Gửi message tới user bất kỳ (generic push).
     *
     * @return true nếu gửi thành công, false nếu user offline
     */
    public boolean sendToUser(int userId, Message message) {
        PrintWriter writer = activeConnections.get(userId);
        if (writer == null) return false;
        sendMessage(userId, writer, message);
        return true;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Gửi message qua socket (thread-safe — synchronized trên writer).
     * println() tự thêm '\n' — đúng với protocol newline-delimited.
     */
    private synchronized void sendMessage(int userId, PrintWriter writer, Message message) {
        try {
            String json = MessageSerializer.toJson(message);
            writer.println(json);
            if (writer.checkError()) {
                // PrintWriter gặp lỗi — client đã disconnect
                logger.warn("Failed to send to userId={}: socket error detected", userId);
                activeConnections.remove(userId); // dọn dẹp
            }
        } catch (Exception e) {
            logger.error("Error sending message to userId={}", userId, e);
            activeConnections.remove(userId);
        }
    }

    /**
     * Build notification data object để serialize vào JSON.
     */
    private NotificationData buildNotificationData(Submission submission) {
        return new NotificationData(
                submission.getId(),
                submission.getProblemId(),
                submission.getVerdict(),
                submission.getScore(),
                submission.getPassedTests(),
                submission.getTotalTests(),
                submission.getExecTimeMs(),
                submission.getMemoryUsedKb(),
                submission.getJudgedAt()
        );
    }

    /** Data class cho JUDGE_NOTIFICATION payload */
    @SuppressWarnings("unused")
    private static class NotificationData {
        final int submissionId;
        final int problemId;
        final String verdict;
        final int score;
        final int passedTests;
        final int totalTests;
        final int execTimeMs;
        final int memoryUsedKb;
        final String judgedAt;

        NotificationData(int submissionId, int problemId, String verdict,
                         int score, int passedTests, int totalTests,
                         int execTimeMs, int memoryUsedKb, String judgedAt) {
            this.submissionId = submissionId;
            this.problemId = problemId;
            this.verdict = verdict;
            this.score = score;
            this.passedTests = passedTests;
            this.totalTests = totalTests;
            this.execTimeMs = execTimeMs;
            this.memoryUsedKb = memoryUsedKb;
            this.judgedAt = judgedAt;
        }
    }
}
