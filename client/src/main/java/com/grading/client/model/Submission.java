package com.grading.client.model;

/**
 * Model đại diện cho 1 lần nộp bài.
 */
public class Submission {
    private int id;
    private int userId;
    private int problemId;
    private Integer assessmentId;
    private String problemTitle;
    private String language;
    private String sourceCode;
    private String verdict;   // PENDING, JUDGING, AC, WA, TLE, RE, CE
    private int score;
    private int passedTests;
    private int totalTests;
    private int execTimeMs;
    private int memoryUsedKb;
    private String compileOutput;
    private String submittedAt;
    private String judgedAt;

    public Submission() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public Integer getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Integer assessmentId) { this.assessmentId = assessmentId; }

    public String getProblemTitle() { return problemTitle; }
    public void setProblemTitle(String problemTitle) { this.problemTitle = problemTitle; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getPassedTests() { return passedTests; }
    public void setPassedTests(int passedTests) { this.passedTests = passedTests; }

    public int getTotalTests() { return totalTests; }
    public void setTotalTests(int totalTests) { this.totalTests = totalTests; }

    public int getExecTimeMs() { return execTimeMs; }
    public void setExecTimeMs(int execTimeMs) { this.execTimeMs = execTimeMs; }

    public int getMemoryUsedKb() { return memoryUsedKb; }
    public void setMemoryUsedKb(int memoryUsedKb) { this.memoryUsedKb = memoryUsedKb; }

    public String getCompileOutput() { return compileOutput; }
    public void setCompileOutput(String compileOutput) { this.compileOutput = compileOutput; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

    public String getJudgedAt() { return judgedAt; }
    public void setJudgedAt(String judgedAt) { this.judgedAt = judgedAt; }

    /**
     * Hiển thị kết quả test dạng "5/10"
     */
    public String getTestResultFormatted() {
        return passedTests + "/" + totalTests;
    }

    /**
     * Kiểm tra submission đã chấm xong chưa.
     */
    public boolean isJudged() {
        return verdict != null && !verdict.equals("PENDING") && !verdict.equals("JUDGING");
    }
}
