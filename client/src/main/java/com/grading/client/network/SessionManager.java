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

    private Long assessmentEndTimeEpochMs;

    public void setActiveAssessment(Integer assessmentId, boolean completed) {
        this.activeAssessmentId = assessmentId;
        this.assessmentCompleted = completed;
        if (assessmentId == null || completed) {
            this.assessmentEndTimeEpochMs = null;
        }
    }

    public void setAssessmentClosesAt(String closesAt, Long remainingSeconds) {
        if (remainingSeconds != null && remainingSeconds >= 0) {
            this.assessmentEndTimeEpochMs = System.currentTimeMillis() + remainingSeconds * 1000L;
        } else if (closesAt != null && !closesAt.isBlank()) {
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(closesAt.replace(" ", "T"));
                java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.systemDefault());
                this.assessmentEndTimeEpochMs = zdt.toInstant().toEpochMilli();
            } catch (Exception ignored) {
                this.assessmentEndTimeEpochMs = null;
            }
        } else {
            this.assessmentEndTimeEpochMs = null;
        }
    }

    public long getRemainingAssessmentSeconds() {
        if (assessmentEndTimeEpochMs == null || !isAssessmentMode()) return 0;
        long diff = (assessmentEndTimeEpochMs - System.currentTimeMillis()) / 1000L;
        return Math.max(0, diff);
    }

    public String formatRemainingAssessmentTime() {
        long sec = getRemainingAssessmentSeconds();
        if (sec <= 0) return "00:00";
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
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
        assessmentEndTimeEpochMs = null;
    }

    public String getToken() {
        return socketClient != null ? socketClient.getAuthToken() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && socketClient != null && socketClient.isConnected();
    }
}
