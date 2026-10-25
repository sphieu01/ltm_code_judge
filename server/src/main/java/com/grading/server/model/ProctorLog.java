package com.grading.server.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model lưu nhật ký giám sát chống gian lận (Proctoring Log).
 * Ghi nhận các sự kiện vi phạm: mất focus cửa sổ (Alt+Tab), paste code bất thường, v.v.
 */
public class ProctorLog {

    private int id;
    private int userId;
    private String username;
    private String fullName;
    private int problemId;
    private Integer assessmentId;
    private String problemTitle;
    private String eventType;
    private String detail;
    private int violationCount;
    private String severity;
    private String clientIp;
    private long timestamp;
    private String formattedTime;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public ProctorLog() {
    }

    public ProctorLog(int id, int userId, String username, String fullName,
                      int problemId, String problemTitle, String eventType, String detail,
                      int violationCount, String severity, String clientIp, long timestamp) {
        this(id, userId, username, fullName, problemId, null, problemTitle, eventType, detail,
                violationCount, severity, clientIp, timestamp);
    }

    public ProctorLog(int id, int userId, String username, String fullName,
                      int problemId, Integer assessmentId, String problemTitle, String eventType, String detail,
                      int violationCount, String severity, String clientIp, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.problemId = problemId;
        this.assessmentId = assessmentId;
        this.problemTitle = problemTitle;
        this.eventType = eventType;
        this.detail = detail;
        this.violationCount = violationCount;
        this.severity = severity;
        this.clientIp = clientIp;
        this.timestamp = timestamp;
        this.formattedTime = LocalDateTime.now().format(FORMATTER);
    }

    public ProctorLog(int id, int userId, String username, String fullName,
                      int problemId, String problemTitle, String eventType,
                      int violationCount, String severity, String clientIp, long timestamp) {
        this(id, userId, username, fullName, problemId, problemTitle, eventType, null, violationCount, severity, clientIp, timestamp);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getProblemId() {
        return problemId;
    }

    public void setProblemId(int problemId) {
        this.problemId = problemId;
    }

    public Integer getAssessmentId() { return assessmentId; }

    public void setAssessmentId(Integer assessmentId) { this.assessmentId = assessmentId; }

    public String getProblemTitle() {
        return problemTitle;
    }

    public void setProblemTitle(String problemTitle) {
        this.problemTitle = problemTitle;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public void setViolationCount(int violationCount) {
        this.violationCount = violationCount;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    public void setFormattedTime(String formattedTime) {
        this.formattedTime = formattedTime;
    }
}
