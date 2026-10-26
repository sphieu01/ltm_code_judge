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
    private com.grading.client.model.CreditClass practiceClass;
    private volatile long assessmentClockOffsetMs;

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
        boolean wasInAssessment = isAssessmentMode();
        if (!wasInAssessment && assessmentId != null && !completed) {
            practiceClass = currentClass;
        } else if (wasInAssessment && (assessmentId == null || completed)) {
            currentClass = practiceClass;
            practiceClass = null;
        }
        this.activeAssessmentId = assessmentId;
        this.assessmentCompleted = completed;
        if (assessmentId == null || completed) {
            this.assessmentEndTimeEpochMs = null;
        }
    }

    public void synchronizeAssessmentClock(String serverTime) {
        if (serverTime == null || serverTime.isBlank()) return;
        try {
            long serverEpochMs = parseAssessmentTimestamp(serverTime);
            assessmentClockOffsetMs = serverEpochMs - System.currentTimeMillis();
        } catch (java.time.format.DateTimeParseException ignored) {
            // Keep the last valid server clock when a response lacks a valid timestamp.
        }
    }

    public long getAssessmentNowEpochMs() {
        return System.currentTimeMillis() + assessmentClockOffsetMs;
    }

    private long parseAssessmentTimestamp(String value) {
        String timestamp = value.trim().replace(" ", "T");
        try {
            return java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli();
        } catch (java.time.format.DateTimeParseException ignored) {
            return java.time.LocalDateTime.parse(timestamp)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
    }

    public void setAssessmentClosesAt(String closesAt, Long remainingSeconds) {
        if (closesAt != null && !closesAt.isBlank()) {
            try {
                this.assessmentEndTimeEpochMs = parseAssessmentTimestamp(closesAt) - assessmentClockOffsetMs;
                return;
            } catch (java.time.format.DateTimeParseException ignored) {
                // Fall back to the server's remaining duration for older responses.
            }
        }
        if (remainingSeconds != null && remainingSeconds >= 0) {
            this.assessmentEndTimeEpochMs = System.currentTimeMillis() + remainingSeconds * 1000L;
        } else {
            this.assessmentEndTimeEpochMs = null;
        }
    }

    public long getRemainingAssessmentSeconds() {
        if (assessmentEndTimeEpochMs == null || !isAssessmentMode()) return 0;
        long remainingMs = assessmentEndTimeEpochMs - System.currentTimeMillis();
        return remainingMs <= 0 ? 0 : (remainingMs + 999) / 1000L;
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
        practiceClass = null;
        assessmentClockOffsetMs = 0;
    }

    public String getToken() {
        return socketClient != null ? socketClient.getAuthToken() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && socketClient != null && socketClient.isConnected();
    }
}
