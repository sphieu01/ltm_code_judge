package com.grading.server.model;

/**
 * Model đại diện cho một lần nộp bài (submission).
 * Map 1-1 với bảng `submissions` trong PostgreSQL.
 *
 * Vòng đời của một Submission:
 *   PENDING → JUDGING → (AC | WA | TLE | RE | CE)
 */
public class Submission {

    // ─── Verdict constants ────────────────────────────────────────────────────
    public static final String VERDICT_PENDING  = "PENDING";
    public static final String VERDICT_JUDGING  = "JUDGING";
    public static final String VERDICT_AC       = "AC";      // Accepted
    public static final String VERDICT_WA       = "WA";      // Wrong Answer
    public static final String VERDICT_TLE      = "TLE";     // Time Limit Exceeded
    public static final String VERDICT_RE       = "RE";      // Runtime Error
    public static final String VERDICT_CE       = "CE";      // Compile Error
    public static final String VERDICT_SV       = "SECURITY_VIOLATION"; // Vi phạm quy tắc bảo mật

    // ─── Fields ───────────────────────────────────────────────────────────────
    private int id;
    private int userId;
    private int problemId;
    private Integer assessmentId;
    private String problemTitle;
    private String sourceCode;
    private String language;        // "java" | "cpp" | "python"
    private String verdict;         // PENDING | JUDGING | AC | WA | TLE | RE | CE
    private int score;              // 0–100
    private int passedTests;
    private int totalTests;
    private int execTimeMs;         // thời gian chạy tối đa qua các test
    private int memoryUsedKb;       // bộ nhớ tối đa qua các test
    private String compileOutput;   // stdout/stderr của compiler (dùng khi CE)
    private boolean isZip;          // true nếu mã nguồn được đóng gói dạng ZIP Base64
    private String submittedAt;
    private String judgedAt;

    // ─── Transient fields (không lưu DB) ──────────────────────────────────────
    /** Thời điểm submit vào queue — dùng để đo queue waiting time */
    private transient long queuedAt;

    public Submission() {}

    public Submission(int userId, int problemId, String sourceCode, String language) {
        this.userId = userId;
        this.problemId = problemId;
        this.sourceCode = sourceCode;
        this.language = language;
        this.verdict = VERDICT_PENDING;
        this.score = 0;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

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

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

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

    public boolean isZip() { return isZip; }
    public void setZip(boolean zip) { this.isZip = zip; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

    public String getJudgedAt() { return judgedAt; }
    public void setJudgedAt(String judgedAt) { this.judgedAt = judgedAt; }

    public long getQueuedAt() { return queuedAt; }
    public void setQueuedAt(long queuedAt) { this.queuedAt = queuedAt; }

    /** Tính queue waiting time (ms) — từ lúc vào queue đến lúc được xử lý */
    public long getQueueWaitMs() {
        return queuedAt > 0 ? System.currentTimeMillis() - queuedAt : 0;
    }

    private transient java.util.List<com.grading.server.model.TestCase> testCases;
    private transient com.grading.server.judge.model.SubmissionTask standaloneTask;

    public java.util.List<com.grading.server.model.TestCase> getTestCases() { return testCases; }
    public void setTestCases(java.util.List<com.grading.server.model.TestCase> testCases) { this.testCases = testCases; }

    public com.grading.server.judge.model.SubmissionTask getStandaloneTask() { return standaloneTask; }
    public void setStandaloneTask(com.grading.server.judge.model.SubmissionTask standaloneTask) { this.standaloneTask = standaloneTask; }

    @Override
    public String toString() {
        return "Submission{id=" + id + ", userId=" + userId + ", problemId=" + problemId
                + ", verdict='" + verdict + "', score=" + score + "}";
    }
}
