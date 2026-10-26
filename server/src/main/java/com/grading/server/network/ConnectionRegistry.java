package com.grading.server.network;

import com.google.gson.JsonObject;
import com.grading.server.model.Submission;
import com.grading.server.protocol.Message;
import com.grading.server.protocol.MessageSerializer;
import com.grading.server.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConnectionRegistry — Quản lý các active TCP connections và thực thi bảo mật phiên.
 *
 * Tính năng chống gian lận & Quản lý phiên:
 * 1. Single Active Session (Kick-out): Mỗi tài khoản chỉ được phép có DUY NHẤT 1 kết nối active.
 *    Nếu phát hiện user đăng nhập từ thiết bị mới, server lập tức gửi thông điệp FORCE_LOGOUT
 *    và ngắt kết nối socket cũ.
 * 2. Lưu trữ thông tin thiết bị / IP client kết nối.
 * 3. Hỗ trợ push notification: JUDGE_NOTIFICATION, FORCE_LOGOUT, PROCTOR_ALERT cho Giáo viên.
 */
public class ConnectionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionRegistry.class);

    /**
     * Đại diện cho phiên kết nối của một Client đã xác thực.
     */
    public static class ClientSession {
        private final int userId;
        private final PrintWriter writer;
        private final Socket socket;
        private final String clientAddress;
        private final String role;
        private final long loginTime;

        public ClientSession(int userId, PrintWriter writer, Socket socket, String clientAddress, String role) {
            this.userId = userId;
            this.writer = writer;
            this.socket = socket;
            this.clientAddress = clientAddress;
            this.role = role != null ? role.toUpperCase() : "STUDENT";
            this.loginTime = System.currentTimeMillis();
        }

        public int getUserId() {
            return userId;
        }

        public PrintWriter getWriter() {
            return writer;
        }

        public Socket getSocket() {
            return socket;
        }

        public String getClientAddress() {
            return clientAddress;
        }

        public String getRole() {
            return role;
        }

        public long getLoginTime() {
            return loginTime;
        }
    }

    /** Map userId → ClientSession tương ứng */
    private final ConcurrentHashMap<Integer, ClientSession> activeSessions = new ConcurrentHashMap<>();

    /** Singleton instance */
    private static final ConnectionRegistry INSTANCE = new ConnectionRegistry();

    private ConnectionRegistry() {}

    public static ConnectionRegistry getInstance() {
        return INSTANCE;
    }

    // ─── Register / Unregister ────────────────────────────────────────────────

    /**
     * Đăng ký kết nối đầy đủ cho user vừa đăng nhập.
     * Tự động kick socket cũ nếu phát hiện user đăng nhập ở máy khác.
     */
    public void register(int userId, PrintWriter writer, Socket socket, String clientAddress, String role) {
        ClientSession newSession = new ClientSession(userId, writer, socket, clientAddress, role);
        ClientSession oldSession = activeSessions.put(userId, newSession);

        if (oldSession != null && oldSession.getSocket() != socket) {
            logger.warn("⚡ [ANTI-CHEAT] User id={} logged in from new IP '{}'. Kicking previous connection from '{}'...",
                    userId, clientAddress, oldSession.getClientAddress());

            // 1. Gửi thông điệp FORCE_LOGOUT về socket cũ
            Message forceLogoutMsg = new Message(MessageType.FORCE_LOGOUT, null);
            forceLogoutMsg.setStatus("FAIL");
            forceLogoutMsg.setMessage("Tài khoản của bạn đã được đăng nhập từ một thiết bị khác (IP: "
                    + clientAddress + "). Phiên làm việc này đã bị kết thúc!");

            try {
                String json = MessageSerializer.toJson(forceLogoutMsg);
                oldSession.getWriter().println(json);
                oldSession.getWriter().flush();
            } catch (Exception e) {
                logger.error("Error sending FORCE_LOGOUT to old session for userId={}", userId, e);
            }

            // 2. Đóng socket cũ ngay lập tức
            try {
                if (oldSession.getSocket() != null && !oldSession.getSocket().isClosed()) {
                    oldSession.getSocket().close();
                }
            } catch (Exception ignored) {
            }
        }

        logger.info("Registered session for userId={}, role={}, ip={}, total active: {}",
                userId, role, clientAddress, activeSessions.size());
    }

    /**
     * Backward-compatibility overload.
     */
    public void register(int userId, PrintWriter writer) {
        register(userId, writer, null, "UNKNOWN", "STUDENT");
    }

    /**
     * Hủy đăng ký an toàn: chỉ hủy nếu session hiện tại khớp với socket truyền vào.
     */
    public void unregister(int userId, Socket socket) {
        activeSessions.computeIfPresent(userId, (k, current) -> {
            if (socket == null || current.getSocket() == socket) {
                logger.debug("Unregistered session for userId={}, remaining: {}", userId, activeSessions.size() - 1);
                return null;
            }
            logger.debug("Skip unregister for userId={}: socket has been replaced by a newer session", userId);
            return current;
        });
    }

    /**
     * Hủy đăng ký kết nối khi client disconnect.
     */
    public void unregister(int userId) {
        unregister(userId, null);
    }

    /**
     * Kiểm tra user có đang online không.
     */
    public boolean isOnline(int userId) {
        return activeSessions.containsKey(userId);
    }

    /**
     * Lấy session của user.
     */
    public ClientSession getSession(int userId) {
        return activeSessions.get(userId);
    }

    /**
     * Số kết nối đang active.
     */
    public int getActiveCount() {
        return activeSessions.size();
    }

    // ─── Push Notification ────────────────────────────────────────────────────

    /**
     * Gửi JUDGE_NOTIFICATION về client sau khi chấm xong.
     */
    public void sendJudgeNotification(Submission submission) {
        int userId = submission.getUserId();
        ClientSession session = activeSessions.get(userId);

        if (session == null || session.getWriter() == null) {
            logger.debug("User {} is offline, JUDGE_NOTIFICATION not sent for submission {}",
                    userId, submission.getId());
            return;
        }

        Message notification = new Message(MessageType.JUDGE_NOTIFICATION, null);
        notification.setStatus("SUCCESS");
        notification.setData(MessageSerializer.toJsonElement(buildNotificationData(submission)));

        sendMessage(userId, session.getWriter(), notification);
        logger.info("JUDGE_NOTIFICATION sent to userId={} for submission id={} ({})",
                userId, submission.getId(), submission.getVerdict());
    }

    /**
     * Gửi message tới user bất kỳ (generic push).
     */
    public boolean sendToUser(int userId, Message message) {
        ClientSession session = activeSessions.get(userId);
        if (session == null || session.getWriter() == null) return false;
        sendMessage(userId, session.getWriter(), message);
        return true;
    }

    /**
     * Broadcast cảnh báo giám sát tới tất cả các tài khoản Giáo viên (TEACHER) đang online.
     */
    public void broadcastToTeachers(Message message) {
        activeSessions.values().forEach(session -> {
            if ("TEACHER".equalsIgnoreCase(session.getRole())) {
                sendMessage(session.getUserId(), session.getWriter(), message);
            }
        });
    }

    /** Sends an invalidation only; student clients fetch data through their authorized endpoints. */
    public void broadcastStudentDataChanged(String scope, String changeType) {
        JsonObject data = new JsonObject();
        data.addProperty("scope", scope);
        data.addProperty("changeType", changeType);
        Message notification = Message.success(MessageType.DATA_CHANGED, null, "Dữ liệu đã thay đổi", data);
        activeSessions.values().forEach(session -> {
            if ("STUDENT".equalsIgnoreCase(session.getRole()) && session.getWriter() != null) {
                sendMessage(session.getUserId(), session.getWriter(), notification);
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private synchronized void sendMessage(int userId, PrintWriter writer, Message message) {
        try {
            String json = MessageSerializer.toJson(message);
            writer.println(json);
            if (writer.checkError()) {
                logger.warn("Failed to send to userId={}: socket error detected", userId);
                activeSessions.remove(userId);
            }
        } catch (Exception e) {
            logger.error("Error sending message to userId={}", userId, e);
            activeSessions.remove(userId);
        }
    }

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
