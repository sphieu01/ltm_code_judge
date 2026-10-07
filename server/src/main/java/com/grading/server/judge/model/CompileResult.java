package com.grading.server.judge.model;

public class CompileResult {
    private final boolean success;
    private final String errorMsg;

    private CompileResult(boolean success, String errorMsg) {
        this.success = success;
        this.errorMsg = errorMsg;
    }

    public static CompileResult ok() {
        return new CompileResult(true, "");
    }

    public static CompileResult fail(String errorMsg) {
        return new CompileResult(false, errorMsg != null ? errorMsg : "Compile error");
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
