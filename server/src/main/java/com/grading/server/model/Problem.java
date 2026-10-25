package com.grading.server.model;

import java.util.List;

/**
 * Model đại diện cho một bài toán (problem).
 * Map 1-1 với bảng `problems` trong PostgreSQL.
 */
public class Problem {

    private int id;
    private String title;
    private String description;
    private int timeLimitMs;        // giới hạn thời gian (ms)
    private int memoryLimitKb;      // giới hạn bộ nhớ (KB)
    private String difficulty;      // "EASY" | "MEDIUM" | "HARD"
    private int createdBy;          // user_id của người tạo
    private Integer classId;        // ID lớp tín chỉ chứa bài tập (NULL = chung)
    private String className;       // Tên lớp tín chỉ
    private boolean active = true;  // Trạng thái hiển thị cho sinh viên làm bài
    private String problemType = "IO_MATCHING"; // "IO_MATCHING" | "JAVA_OOP"
    private String oopSpec;         // JSON cấu hình chấm OOP
    private String createdAt;

    // Các test cases — chỉ load khi cần (lazy)
    private List<TestCase> testCases;

    public Problem() {}

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getTimeLimitMs() { return timeLimitMs; }
    public void setTimeLimitMs(int timeLimitMs) { this.timeLimitMs = timeLimitMs; }

    public int getMemoryLimitKb() { return memoryLimitKb; }
    public void setMemoryLimitKb(int memoryLimitKb) { this.memoryLimitKb = memoryLimitKb; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getProblemType() {
        return problemType != null ? problemType : "IO_MATCHING";
    }
    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }

    public String getOopSpec() { return oopSpec; }
    public void setOopSpec(String oopSpec) { this.oopSpec = oopSpec; }

    public boolean isJavaOop() {
        return "JAVA_OOP".equalsIgnoreCase(problemType);
    }

    public List<TestCase> getTestCases() { return testCases; }
    public void setTestCases(List<TestCase> testCases) { this.testCases = testCases; }

    @Override
    public String toString() {
        return "Problem{id=" + id + ", title='" + title + "', difficulty='" + difficulty + "'}";
    }
}
