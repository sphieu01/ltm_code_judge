package com.grading.client.network;

import com.grading.client.model.User;

/**
 * Singleton lưu trữ trạng thái phiên làm việc.
 * Được chia sẻ giữa các Controller.
 *
 * Chứa:
 * - SocketClient instance (kết nối TCP)
 * - User đang đăng nhập
 */
public class SessionManager {

    private static SessionManager instance;

    private SocketClient socketClient;
    private User currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public SocketClient getSocketClient() {
        return socketClient;
    }

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    private final java.util.Map<Integer, String> problemStatusMap = new java.util.concurrent.ConcurrentHashMap<>();

    public void recordSubmission(int problemId, String verdict) {
        if (verdict == null || verdict.trim().isEmpty()) return;
        String current = problemStatusMap.get(problemId);
        if ("AC".equalsIgnoreCase(current) || "ACCEPTED".equalsIgnoreCase(current)) {
            // Nếu đã từng AC thì giữ nguyên là AC
            return;
        }
        String badge = toBadge(verdict);
        problemStatusMap.put(problemId, badge);
    }

    public String getProblemStatus(int problemId) {
        return problemStatusMap.getOrDefault(problemId, "");
    }

    private String toBadge(String v) {
        String u = v.toUpperCase();
        if (u.equals("ACCEPTED") || u.equals("AC") || u.equals("PASSED")) return "AC";
        if (u.contains("WRONG") || u.equals("WA") || u.equals("FAILED")) return "WA";
        if (u.contains("TLE") || u.contains("TIME_LIMIT")) return "TLE";
        if (u.contains("CE") || u.contains("COMPILE")) return "CE";
        if (u.contains("RUNTIME") || u.equals("RE")) return "RE";
        return u;
    }

    /**
     * Xóa session khi logout.
     */
    public void clear() {
        if (socketClient != null) {
            socketClient.disconnect();
        }
        currentUser = null;
        socketClient = null;
        problemStatusMap.clear();
    }

    public String getToken() {
        return socketClient != null ? socketClient.getAuthToken() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && socketClient != null && socketClient.isConnected();
    }
}
