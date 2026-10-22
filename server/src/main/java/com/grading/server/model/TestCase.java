package com.grading.server.model;

/**
 * Model đại diện cho một test case của bài toán.
 * Map 1-1 với bảng `test_cases` trong PostgreSQL.
 */
public class TestCase {

    private int id;
    private int problemId;
    private String inputData;
    private String expectedOutput;
    private boolean isSample;       // true = test case mẫu (hiển thị cho student)
    private int orderNum;           // thứ tự chạy

    public TestCase() {}

    public TestCase(int problemId, String inputData, String expectedOutput, boolean isSample, int orderNum) {
        this.problemId = problemId;
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
        this.isSample = isSample;
        this.orderNum = orderNum;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProblemId() { return problemId; }
    public void setProblemId(int problemId) { this.problemId = problemId; }

    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public boolean isSample() { return isSample; }
    public void setSample(boolean sample) { isSample = sample; }

    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }

    @Override
    public String toString() {
        return "TestCase{id=" + id + ", problemId=" + problemId + ", isSample=" + isSample + "}";
    }
}
