package com.grading.client.model;

/**
 * Model đại diện cho 1 bài tập.
 */
public class Problem {
    private int id;
    private String title;
    private String description;
    private int timeLimitMs;
    private int memoryLimitKb;
    private String difficulty; // EASY, MEDIUM, HARD
    private Integer classId;   // Lớp tín chỉ gắn với bài tập
    private String className;  // Tên lớp tín chỉ
    private boolean active = true; // Trạng thái hiển thị cho sinh viên làm bài
    private String problemType = "IO_MATCHING"; // "IO_MATCHING" | "JAVA_OOP"
    private String oopSpec;        // JSON cấu hình OOP
    private String allowedLanguages; // Danh sách ngôn ngữ cho phép nộp (nếu bài thuộc lớp/kỳ thi cụ thể)
    private int totalTestCases;
    private int sampleTestCases;
    private String status = ""; // "AC", "WA", "CE", "TLE", hoặc "" nếu chưa làm

    public Problem() {}

    public Problem(Problem other) {
        if (other != null) {
            this.id = other.id;
            this.title = other.title;
            this.description = other.description;
            this.timeLimitMs = other.timeLimitMs;
            this.memoryLimitKb = other.memoryLimitKb;
            this.difficulty = other.difficulty;
            this.classId = other.classId;
            this.className = other.className;
            this.active = other.active;
            this.problemType = other.problemType;
            this.oopSpec = other.oopSpec;
            this.allowedLanguages = other.allowedLanguages;
            this.totalTestCases = other.totalTestCases;
            this.sampleTestCases = other.sampleTestCases;
            this.status = other.status;
        }
    }

    public String getAllowedLanguages() { return allowedLanguages; }
    public void setAllowedLanguages(String allowedLanguages) { this.allowedLanguages = allowedLanguages; }


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

    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public int getTotalTestCases() { return totalTestCases; }

    public void setTotalTestCases(int totalTestCases) { this.totalTestCases = totalTestCases; }

    public int getSampleTestCases() { return sampleTestCases; }
    public void setSampleTestCases(int sampleTestCases) { this.sampleTestCases = sampleTestCases; }

    /**
     * Hiển thị time limit dạng "2.0s"
     */
    public String getTimeLimitFormatted() {
        return String.format("%.1fs", timeLimitMs / 1000.0);
    }

    public String getStatus() { return status != null ? status : ""; }
    public void setStatus(String status) { this.status = status; }

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

    /**
     * Hiển thị memory limit dạng "256 MB"
     */
    public String getMemoryLimitFormatted() {
        return String.format("%d MB", memoryLimitKb / 1024);
    }
}
