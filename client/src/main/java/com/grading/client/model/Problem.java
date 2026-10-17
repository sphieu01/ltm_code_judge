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
    private int totalTestCases;
    private int sampleTestCases;
    private String status = ""; // "AC", "WA", "CE", "TLE", hoặc "" nếu chưa làm

    public Problem() {}

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

    /**
     * Hiển thị memory limit dạng "256 MB"
     */
    public String getMemoryLimitFormatted() {
        return String.format("%d MB", memoryLimitKb / 1024);
    }
}
