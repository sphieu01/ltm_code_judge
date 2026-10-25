package com.grading.server.judge.oop;

/**
 * Kết quả kiểm tra cấu trúc Lập trình Hướng đối tượng (Java OOP).
 */
public class OopCheckResult {

    private final boolean passed;
    private final String errorMessage;

    private OopCheckResult(boolean passed, String errorMessage) {
        this.passed = passed;
        this.errorMessage = errorMessage;
    }

    public static OopCheckResult pass() {
        return new OopCheckResult(true, null);
    }

    public static OopCheckResult fail(String errorMessage) {
        return new OopCheckResult(false, errorMessage);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
