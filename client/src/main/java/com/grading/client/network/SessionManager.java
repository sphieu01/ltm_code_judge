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
    private volatile com.grading.client.model.CreditClass currentClass;
    private volatile Integer activeAssessmentId;
    private volatile boolean assessmentCompleted;

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

    public com.grading.client.model.CreditClass getCurrentClass() {
        return currentClass;
    }

    public void setCurrentClass(com.grading.client.model.CreditClass currentClass) {
        this.currentClass = currentClass;
    }

    public Integer getActiveAssessmentId() { return activeAssessmentId; }

    public void setActiveAssessment(Integer assessmentId, boolean completed) {
        this.activeAssessmentId = assessmentId;
        this.assessmentCompleted = completed;
    }

    public boolean isAssessmentMode() {
        return activeAssessmentId != null && !assessmentCompleted;
    }

    public boolean isAssessmentCompleted() { return assessmentCompleted; }

    private final java.util.Map<Integer, String> problemStatusMap = new java.util.concurrent.ConcurrentHashMap<>();

    public void recordSubmission(int problemId, String verdict) {
        if (verdict == null || verdict.trim().isEmpty()) return;
        String current = problemStatusMap.get(problemId);
        if ("Accepted".equalsIgnoreCase(current) || "AC".equalsIgnoreCase(current) || "ACCEPTED".equalsIgnoreCase(current)) {
            // Nếu đã từng AC thì giữ nguyên là Accepted
            return;
        }
        String badge = com.grading.client.model.Submission.formatVerdict(verdict);
        problemStatusMap.put(problemId, badge);
    }

    public String getProblemStatus(int problemId) {
        return problemStatusMap.getOrDefault(problemId, "");
    }

    /**
     * Xóa session khi logout.
     */
    public void clear() {
        if (socketClient != null) {
            socketClient.disconnect();
        }
        currentUser = null;
        currentClass = null;
        socketClient = null;
        problemStatusMap.clear();
        activeAssessmentId = null;
        assessmentCompleted = false;
    }

    public String getToken() {
        return socketClient != null ? socketClient.getAuthToken() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && socketClient != null && socketClient.isConnected();
    }
}
