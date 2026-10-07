package com.grading.server.judge.model;

import com.grading.server.judge.OnJudgeCompletedListener;
import com.grading.server.judge.SandboxRunner;

import java.util.Collections;
import java.util.List;

/**
 * Đại diện cho một tác vụ chấm bài nộp được đẩy vào JudgeQueue.
 */
public class SubmissionTask {

    private final long submissionId;
    private final int problemId;
    private final String sourceCode;
    private final List<TestCase> testCases;
    private final long timeLimitMs;
    private final long submittedAt;
    private final OnJudgeCompletedListener customListener;

    public SubmissionTask(long submissionId, int problemId, String sourceCode,
                          List<TestCase> testCases, long timeLimitMs,
                          long submittedAt, OnJudgeCompletedListener customListener) {
        this.submissionId = submissionId;
        this.problemId = problemId;
        this.sourceCode = sourceCode != null ? sourceCode : "";
        this.testCases = testCases != null ? Collections.unmodifiableList(testCases) : Collections.emptyList();
        this.timeLimitMs = timeLimitMs > 0 ? timeLimitMs : SandboxRunner.DEFAULT_TIME_LIMIT_MS;
        this.submittedAt = submittedAt > 0 ? submittedAt : System.currentTimeMillis();
        this.customListener = customListener;
    }

    public SubmissionTask(long submissionId, int problemId, String sourceCode,
                          List<TestCase> testCases, long timeLimitMs) {
        this(submissionId, problemId, sourceCode, testCases, timeLimitMs, System.currentTimeMillis(), null);
    }

    public SubmissionTask(long submissionId, int problemId, String sourceCode,
                          List<TestCase> testCases) {
        this(submissionId, problemId, sourceCode, testCases, SandboxRunner.DEFAULT_TIME_LIMIT_MS, System.currentTimeMillis(), null);
    }

    public long getSubmissionId() {
        return submissionId;
    }

    public int getProblemId() {
        return problemId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public long getTimeLimitMs() {
        return timeLimitMs;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public OnJudgeCompletedListener getCustomListener() {
        return customListener;
    }
}
