package com.grading.server.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SandboxRunner — Compile và chạy code nộp từ student.
 *
 * Dùng ProcessBuilder để:
 *   1. Compile: javac / g++ / python3 -m py_compile
 *   2. Run: java / ./a.out / python3 với stdin redirect
 *   3. Timeout: Process.waitFor(timeout) → kill → TLE
 *   4. Capture stderr: Compile Error detection
 *   5. Exit code: != 0 → Runtime Error
 *
 * Mỗi submission được chạy trong thư mục temp riêng:
 *   /tmp/judge/<submissionId>/
 *
 * Lưu ý: Đây là sandbox đơn giản không có isolation thật sự.
 * Production cần dùng Docker hoặc seccomp. Phù hợp cho môi trường học.
 *
 * Phần SandboxRunner này được thiết kế để thành viên C (Judge Engine)
 * có thể implement chi tiết hơn. Class này cung cấp interface và mock
 * để thành viên B có thể test mà không cần chờ C.
 */
public class SandboxRunner {

    private static final Logger logger = LoggerFactory.getLogger(SandboxRunner.class);

    /** Timeout mặc định cho mỗi lần chạy test case (ms) */
    public static final long DEFAULT_TIME_LIMIT_MS = 2000L;

    /** Thư mục temp để chứa code của mỗi submission */
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "judge";

    public SandboxRunner() {
        // Tạo thư mục judge temp nếu chưa có
        new File(TEMP_DIR).mkdirs();
    }

    // ─── Static utility ───────────────────────────────────────────────────────

    /**
     * Trích xuất tên class public từ source code Java.
     * Ví dụ: "public class BaiTapTong {" → "BaiTapTong"
     * Fallback về "Main" nếu không tìm thấy.
     */
    public static String extractClassName(String sourceCode) {
        if (sourceCode == null) return "Main";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("public\\s+class\\s+(\\w+)")
                .matcher(sourceCode);
        return m.find() ? m.group(1) : "Main";
    }

    // ─── Path-based overloads (dùng cho demo/test của hoangdev) ──────────────

    /**
     * Overload dùng Path thay vì int submissionId.
     * Compile Java source với tên class tự chọn.
     */
    public com.grading.server.judge.model.CompileResult compile(Path workDir, String className, String sourceCode) {
        try {
            Files.createDirectories(workDir);
            Path srcFile = workDir.resolve(className + ".java");
            Files.writeString(srcFile, sourceCode, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("javac", "-encoding", "UTF-8", srcFile.getFileName().toString());
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readStream(process.getInputStream());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) { process.destroyForcibly(); return com.grading.server.judge.model.CompileResult.fail("Compilation timed out"); }
            return process.exitValue() == 0
                    ? com.grading.server.judge.model.CompileResult.ok()
                    : com.grading.server.judge.model.CompileResult.fail(output);
        } catch (Exception e) {
            return com.grading.server.judge.model.CompileResult.fail("Internal compile error: " + e.getMessage());
        }
    }

    /**
     * Overload dùng Path + TestCase từ hoangdev.
     * Chạy class Java đã compile với test case và timeout.
     */
    public com.grading.server.judge.model.RunResult run(
            Path workDir, String className,
            com.grading.server.judge.model.TestCase tc, long timeLimitMs) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", workDir.toString(), className);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            try (OutputStream stdin = process.getOutputStream()) {
                if (tc.getInput() != null) stdin.write(tc.getInput().getBytes(StandardCharsets.UTF_8));
            }

            long startTime = System.currentTimeMillis();
            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();
            Thread t1 = new Thread(() -> { try { stdout.append(readStream(process.getInputStream())); } catch (IOException ignored) {} });
            Thread t2 = new Thread(() -> { try { stderr.append(readStream(process.getErrorStream())); } catch (IOException ignored) {} });
            t1.start(); t2.start();

            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            long execTimeMs = System.currentTimeMillis() - startTime;
            if (!finished) {
                process.destroyForcibly();
                return com.grading.server.judge.model.RunResult.tle((int) execTimeMs);
            }
            t1.join(1000); t2.join(1000);

            if (process.exitValue() != 0) {
                return com.grading.server.judge.model.RunResult.re(stderr.toString(), (int) execTimeMs);
            }

            String actual = stdout.toString().trim();
            String expected = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";
            boolean ac = actual.equals(expected);
            return ac
                ? com.grading.server.judge.model.RunResult.ac(actual, (int) execTimeMs)
                : com.grading.server.judge.model.RunResult.wa(actual, (int) execTimeMs);

        } catch (Exception e) {
            return com.grading.server.judge.model.RunResult.re(e.getMessage(), 0);
        }
    }

    /** Overload cleanup dùng Path (cho hoangdev) */
    public void cleanup(Path workDir) {
        try {
            if (Files.exists(workDir)) deleteDirectory(workDir);
        } catch (Exception e) {
            logger.warn("Failed to cleanup workdir: {}", workDir, e);
        }
    }


    // ─── Compile ──────────────────────────────────────────────────────────────

    /**
     * Compile source code.
     *
     * @param submissionId ID submission (dùng để tạo unique working dir)
     * @param sourceCode   Code nguồn
     * @param language     "java" | "cpp" | "python"
     * @return CompileResult với isSuccess và errorOutput
     */
    public CompileResult compile(int submissionId, String sourceCode, String language) {
        String workDir = getWorkDir(submissionId);
        new File(workDir).mkdirs();

        try {
            switch (language.toLowerCase()) {
                case "java":
                    return compileJava(submissionId, sourceCode, workDir);
                case "cpp":
                    return compileCpp(submissionId, sourceCode, workDir);
                case "python":
                    return compilePython(submissionId, sourceCode, workDir);
                default:
                    return CompileResult.failure("Unsupported language: " + language);
            }
        } catch (Exception e) {
            logger.error("Compile error for submission {}", submissionId, e);
            return CompileResult.failure("Internal compile error: " + e.getMessage());
        }
    }

    private CompileResult compileJava(int submissionId, String sourceCode, String workDir)
            throws IOException, InterruptedException {
        // Lưu source code
        String fileName = workDir + File.separator + "Main.java";
        writeFile(fileName, sourceCode);

        // Compile với javac
        ProcessBuilder pb = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java");
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true); // merge stderr vào stdout

        Process process = pb.start();
        String output = readStream(process.getInputStream());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return CompileResult.failure("Compilation timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return CompileResult.failure(output);
        }

        return CompileResult.success();
    }

    private CompileResult compileCpp(int submissionId, String sourceCode, String workDir)
            throws IOException, InterruptedException {
        String fileName = workDir + File.separator + "solution.cpp";
        writeFile(fileName, sourceCode);

        ProcessBuilder pb = new ProcessBuilder("g++", "-O2", "-o", "solution", "solution.cpp");
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readStream(process.getInputStream());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return CompileResult.failure("Compilation timed out");
        }

        return process.exitValue() == 0 ? CompileResult.success() : CompileResult.failure(output);
    }

    private CompileResult compilePython(int submissionId, String sourceCode, String workDir)
            throws IOException, InterruptedException {
        // Python không cần compile trước, chỉ syntax check
        String fileName = workDir + File.separator + "solution.py";
        writeFile(fileName, sourceCode);

        ProcessBuilder pb = new ProcessBuilder("python3", "-m", "py_compile", "solution.py");
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = readStream(process.getInputStream());
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return CompileResult.failure("Syntax check timed out");
        }

        return process.exitValue() == 0 ? CompileResult.success() : CompileResult.failure(output);
    }

    // ─── Run ──────────────────────────────────────────────────────────────────

    /**
     * Chạy code với input và timeout.
     *
     * @param submissionId  ID submission
     * @param language      "java" | "cpp" | "python"
     * @param input         stdin input
     * @param timeLimitMs   giới hạn thời gian (ms)
     * @return RunResult với stdout, execTimeMs, memoryKb, verdict (null = OK, "TLE", "RE")
     */
    public RunResult run(int submissionId, String language, String input, int timeLimitMs) {
        String workDir = getWorkDir(submissionId);

        try {
            List<String> command = buildRunCommand(language, workDir);
            if (command == null) {
                return RunResult.error("RE", "Unsupported language");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(false); // tách stderr riêng

            Process process = pb.start();

            // Ghi stdin
            if (input != null && !input.isEmpty()) {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }

            long startTime = System.currentTimeMillis();

            // Đọc stdout và stderr song song để tránh deadlock pipe buffer
            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();

            Thread stdoutReader = new Thread(() -> {
                try { stdout.append(readStream(process.getInputStream())); }
                catch (IOException ignored) {}
            });
            Thread stderrReader = new Thread(() -> {
                try { stderr.append(readStream(process.getErrorStream())); }
                catch (IOException ignored) {}
            });
            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            long execTimeMs = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return RunResult.error("TLE", null);
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return RunResult.error("RE", stderr.toString());
            }

            return RunResult.success(stdout.toString(), (int) execTimeMs, 0);

        } catch (Exception e) {
            logger.error("Run error for submission {}", submissionId, e);
            return RunResult.error("RE", e.getMessage());
        }
    }

    /**
     * Cleanup working directory sau khi chấm xong.
     */
    public void cleanup(int submissionId) {
        try {
            Path workDir = Paths.get(getWorkDir(submissionId));
            if (Files.exists(workDir)) {
                deleteDirectory(workDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup workdir for submission {}", submissionId, e);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String getWorkDir(int submissionId) {
        return TEMP_DIR + File.separator + submissionId;
    }

    private List<String> buildRunCommand(String language, String workDir) {
        List<String> cmd = new ArrayList<>();
        switch (language.toLowerCase()) {
            case "java":
                cmd.add("java");
                cmd.add("-cp");
                cmd.add(workDir);
                cmd.add("Main");
                break;
            case "cpp":
                cmd.add(workDir + File.separator + "solution");
                break;
            case "python":
                cmd.add("python3");
                cmd.add(workDir + File.separator + "solution.py");
                break;
            default:
                return null;
        }
        return cmd;
    }

    private void writeFile(String path, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
             .sorted(java.util.Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    // ─── Result classes ───────────────────────────────────────────────────────

    /** Kết quả compile */
    public static class CompileResult {
        private final boolean success;
        private final String errorOutput;

        private CompileResult(boolean success, String errorOutput) {
            this.success = success;
            this.errorOutput = errorOutput;
        }

        public static CompileResult success() {
            return new CompileResult(true, null);
        }

        public static CompileResult failure(String errorOutput) {
            return new CompileResult(false, errorOutput);
        }

        public boolean isSuccess() { return success; }
        public String getErrorOutput() { return errorOutput; }
    }

    /** Kết quả chạy 1 test case */
    public static class RunResult {
        private final String stdout;
        private final int execTimeMs;
        private final int memoryKb;
        /** null = OK, "TLE" = Time Limit Exceeded, "RE" = Runtime Error */
        private final String verdict;
        private final String errorOutput;

        private RunResult(String stdout, int execTimeMs, int memoryKb,
                          String verdict, String errorOutput) {
            this.stdout = stdout;
            this.execTimeMs = execTimeMs;
            this.memoryKb = memoryKb;
            this.verdict = verdict;
            this.errorOutput = errorOutput;
        }

        public static RunResult success(String stdout, int execTimeMs, int memoryKb) {
            return new RunResult(stdout, execTimeMs, memoryKb, null, null);
        }

        public static RunResult error(String verdict, String errorOutput) {
            return new RunResult("", 0, 0, verdict, errorOutput);
        }

        public String getStdout() { return stdout; }
        public int getExecTimeMs() { return execTimeMs; }
        public int getMemoryKb() { return memoryKb; }
        public String getVerdict() { return verdict; }
        public String getErrorOutput() { return errorOutput; }
        public boolean isOk() { return verdict == null; }
    }
}
