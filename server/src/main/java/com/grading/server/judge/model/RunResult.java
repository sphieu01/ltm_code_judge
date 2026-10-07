package com.grading.server.judge.model;

public class RunResult {
    private final VerdictType verdict;
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final long timeMs;

    public RunResult(VerdictType verdict, String stdout, String stderr, int exitCode, long timeMs) {
        this.verdict = verdict;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
        this.exitCode = exitCode;
        this.timeMs = timeMs;
    }

    public static RunResult timeout(long timeMs) {
        return new RunResult(VerdictType.TLE, "", "Time Limit Exceeded", -1, timeMs);
    }

    public static RunResult runtimeError(String stderr, int exitCode, long timeMs) {
        return new RunResult(VerdictType.RE, "", stderr, exitCode, timeMs);
    }

    // Alias methods để tương thích với SandboxRunner overloads
    public static RunResult tle(int timeMs) {
        return timeout(timeMs);
    }

    public static RunResult re(String stderr, int timeMs) {
        return runtimeError(stderr, 1, timeMs);
    }

    public static RunResult ac(String stdout, int timeMs) {
        return new RunResult(VerdictType.AC, stdout, "", 0, timeMs);
    }

    public static RunResult wa(String stdout, int timeMs) {
        return new RunResult(VerdictType.WA, stdout, "", 0, timeMs);
    }

    public VerdictType getVerdict() {
        return verdict;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public long getTimeMs() {
        return timeMs;
    }
}
