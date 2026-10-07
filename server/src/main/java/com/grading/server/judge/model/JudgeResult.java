package com.grading.server.judge.model;

public class JudgeResult {
    private final VerdictType verdict;
    private final int score;
    private final int passedTests;
    private final int totalTests;
    private final long maxTimeMs;
    private final String compileError;

    public JudgeResult(VerdictType verdict, int score, int passedTests, int totalTests, long maxTimeMs, String compileError) {
        this.verdict = verdict;
        this.score = score;
        this.passedTests = passedTests;
        this.totalTests = totalTests;
        this.maxTimeMs = maxTimeMs;
        this.compileError = compileError != null ? compileError : "";
    }

    public static JudgeResult compileError(String errorMsg) {
        return new JudgeResult(VerdictType.CE, 0, 0, 0, 0, errorMsg);
    }

    public VerdictType getVerdict() {
        return verdict;
    }

    public int getScore() {
        return score;
    }

    public int getPassedTests() {
        return passedTests;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public long getMaxTimeMs() {
        return maxTimeMs;
    }

    public String getCompileError() {
        return compileError;
    }
}
