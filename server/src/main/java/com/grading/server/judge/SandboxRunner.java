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

    /** Thư mục temp để chứa code của mỗi submission (sử dụng thư mục cục bộ của ứng dụng để tránh Windows App Control policy 4551) */
    private static final String TEMP_DIR = resolveBaseTempDir();

    private static String resolveBaseTempDir() {
        String baseDir = System.getProperty("user.dir");
        File dir = new File(baseDir);
        if ("server".equalsIgnoreCase(dir.getName())) {
            File parent = dir.getParentFile();
            if (parent != null && parent.exists()) {
                baseDir = parent.getAbsolutePath();
            }
        }
        return baseDir + File.separator + "temp" + File.separator + "judge";
    }

    /** Lưu trữ tên class Java tương ứng với mỗi submissionId để khi run gọi đúng class */
    private final java.util.Map<Integer, String> mainClassBySubmission = new java.util.concurrent.ConcurrentHashMap<>();

    private static String PYTHON_CMD = null;

    private static final java.util.concurrent.atomic.AtomicBoolean BANNER_PRINTED = new java.util.concurrent.atomic.AtomicBoolean(false);

    public SandboxRunner() {
        // Tạo thư mục judge temp nếu chưa có
        new File(TEMP_DIR).mkdirs();

        // Kiểm tra và ghi log rõ ràng chế độ Sandbox khi khởi tạo (chỉ in 1 lần duy nhất cho toàn server)
        if (BANNER_PRINTED.compareAndSet(false, true)) {
            if (com.grading.server.judge.docker.DockerSandboxRunner.isDockerAvailable()) {
                logger.info("================================================================================");
                logger.info("🛡️ [SANDBOX ENGINE] ACTIVE MODE: DOCKER CONTAINER SANDBOX (Production Standard)");
                logger.info("    -> Container Image: {}", com.grading.server.judge.docker.DockerSandboxRunner.DOCKER_IMAGE);
                logger.info("    -> Security Limits:  --network none | --memory 256m | --cpus 1.0 | Read-Only Mount");
                logger.info("================================================================================");
            } else {
                logger.warn("================================================================================");
                logger.warn("⚠️ [SANDBOX ENGINE] ACTIVE MODE: LOCAL PROCESS SANDBOX (FALLBACK MODE)");
                logger.warn("    -> Notice:          Docker is offline or image 'ltm-judge-runner:latest' not found.");
                logger.warn("    -> Execution Host:  Student code runs directly on HOST OS via ProcessBuilder.");
                logger.warn("    -> Work Directory:  {}", TEMP_DIR);
                logger.warn("    -> Protection:      SecurityScanner Layer 1 (Static Regex) + Timeout Enforcement");
                logger.warn("================================================================================");
            }
        }
    }

    /**
     * Tự động phát hiện lệnh python hợp lệ trên hệ thống ("python3" hoặc "python").
     * Tránh lỗi trên Windows khi python3 trỏ tới WindowsApps alias.
     */
    public static synchronized String getPythonCommand() {
        if (PYTHON_CMD != null) return PYTHON_CMD;
        try {
            Process p = new ProcessBuilder("python3", "--version").start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                PYTHON_CMD = "python3";
                return PYTHON_CMD;
            }
        } catch (Exception ignored) {}
        try {
            Process p = new ProcessBuilder("python", "--version").start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                PYTHON_CMD = "python";
                return PYTHON_CMD;
            }
        } catch (Exception ignored) {}
        PYTHON_CMD = "python";
        return PYTHON_CMD;
    }

    // ─── Static utility ───────────────────────────────────────────────────────

    /**
     * Trích xuất tên class từ source code Java.
     * Ưu tiên public class, sau đó đến class đầu tiên khai báo.
     * Fallback về "Main" nếu không tìm thấy.
     */
    public static String extractClassName(String sourceCode) {
        if (sourceCode == null) return "Main";
        // 1. Tìm public class
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("public\\s+class\\s+(\\w+)")
                .matcher(sourceCode);
        if (m.find()) return m.group(1);

        // 2. Tìm class bình thường
        m = java.util.regex.Pattern
                .compile("(?:^|\\s)class\\s+(\\w+)")
                .matcher(sourceCode);
        if (m.find()) return m.group(1);

        return "Main";
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
        return compile(submissionId, sourceCode, language, false);
    }

    public CompileResult compile(int submissionId, String sourceCode, String language, boolean isZip) {
        String workDir = getWorkDir(submissionId);
        new File(workDir).mkdirs();

        String className;
        if (isZip) {
            try {
                com.grading.server.util.ZipUtils.extractBase64Zip(sourceCode, new File(workDir));
            } catch (IOException e) {
                return CompileResult.failure("Lỗi giải nén file zip: " + e.getMessage());
            }
            if (new File(workDir, "Main.java").exists()) {
                className = "Main";
            } else {
                File[] javaFiles = new File(workDir).listFiles((dir, name) -> name.endsWith(".java"));
                className = (javaFiles != null && javaFiles.length > 0) ? javaFiles[0].getName().replace(".java", "") : "Main";
            }
        } else {
            className = "java".equalsIgnoreCase(language) ? extractClassName(sourceCode) : "Main";
        }

        mainClassBySubmission.put(submissionId, className);
        try {
            Files.writeString(Paths.get(workDir, ".main_class"), className, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}

        boolean useDocker = com.grading.server.judge.docker.DockerSandboxRunner.isDockerAvailable();

        if (useDocker) {
            logger.info("[DOCKER_SANDBOX] Submission id={} (lang={}, isZip={}) -> Compiling inside Docker container...", submissionId, language, isZip);
            if (!isZip) {
                try {
                    writeSourceFile(workDir, sourceCode, language, className);
                } catch (IOException e) {
                    return CompileResult.failure("File write error: " + e.getMessage());
                }
            }
            return com.grading.server.judge.docker.DockerSandboxRunner.compile(submissionId, language, workDir, className);
        }

        // ⚠️ LOG RÕ KHI CHẠY LOCAL PROCESS SANDBOX THEO YÊU CẦU CỦA USER:
        logger.warn("⚠️ [LOCAL_SANDBOX] [COMPILE] Submission #{} (lang={}, isZip={}) -> Compiling locally on Host OS (workDir={})",
                submissionId, language, isZip, workDir);

        CompileResult result;
        try {
            switch (language.toLowerCase()) {
                case "java":
                    result = compileJava(submissionId, sourceCode, workDir, isZip, className);
                    break;
                case "c":
                    result = compileC(submissionId, sourceCode, workDir);
                    break;
                case "cpp":
                    result = compileCpp(submissionId, sourceCode, workDir);
                    break;
                case "python":
                    result = compilePython(submissionId, sourceCode, workDir);
                    break;
                default:
                    return CompileResult.failure("Unsupported language: " + language);
            }
        } catch (Exception e) {
            logger.error("Compile error for submission {}", submissionId, e);
            return CompileResult.failure("Internal compile error: " + e.getMessage());
        }

        if (result.isSuccess()) {
            logger.info("✅ [LOCAL_SANDBOX] [COMPILE_OK] Submission #{} (lang={}) compiled successfully on Host OS", submissionId, language);
        } else {
            logger.warn("⚠️ [LOCAL_SANDBOX] [COMPILE_FAIL] Submission #{} (lang={}) compilation failed on Host OS: {}",
                    submissionId, language, result.getErrorOutput());
        }
        return result;
    }

    private CompileResult compileC(int submissionId, String sourceCode, String workDir)
            throws IOException, InterruptedException {
        String fileName = workDir + File.separator + "solution.c";
        writeFile(fileName, sourceCode);

        ProcessBuilder pb = new ProcessBuilder("gcc", "-O2", "-o", "solution", "solution.c");
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

    private CompileResult compileJava(int submissionId, String sourceCode, String workDir, boolean isZip, String className)
            throws IOException, InterruptedException {
        if (!isZip) {
            // Lưu source code đúng theo className.java
            String fileName = workDir + File.separator + className + ".java";
            writeFile(fileName, sourceCode);
        }

        // Tìm tất cả file .java trong workDir để biên dịch
        File[] javaFiles = new File(workDir).listFiles((dir, name) -> name.endsWith(".java"));
        List<String> javacCmd = new ArrayList<>(List.of("javac", "-encoding", "UTF-8"));
        if (javaFiles != null && javaFiles.length > 0) {
            for (File jf : javaFiles) {
                javacCmd.add(jf.getName());
            }
        } else {
            javacCmd.add(className + ".java");
        }

        ProcessBuilder pb = new ProcessBuilder(javacCmd);
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

        String pyCmd = getPythonCommand();
        ProcessBuilder pb = new ProcessBuilder(pyCmd, "-m", "py_compile", "solution.py");
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
        boolean useDocker = com.grading.server.judge.docker.DockerSandboxRunner.isDockerAvailable();

        if (useDocker) {
            String className = mainClassBySubmission.getOrDefault(submissionId, "Main");
            logger.info("[DOCKER_SANDBOX] Submission id={} (lang={}) -> Running test case inside Docker container...", submissionId, language);
            return com.grading.server.judge.docker.DockerSandboxRunner.run(submissionId, language, input, timeLimitMs, workDir, className);
        }

        // ⚠️ LOG RÕ KHI CHẠY LOCAL PROCESS SANDBOX THEO YÊU CẦU CỦA USER:
        logger.warn("⚠️ [LOCAL_SANDBOX] [RUN] Submission #{} (lang={}) -> Executing on Host OS (timeout={}ms, workDir={})",
                submissionId, language, timeLimitMs, workDir);

        try {
            List<String> command = buildRunCommand(submissionId, language, workDir);
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
                logger.warn("⚠️ [LOCAL_SANDBOX] [TLE] Submission #{} timed out after {}ms on Host OS (Killed)", submissionId, timeLimitMs);
                return RunResult.error("TLE", null);
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("⚠️ [LOCAL_SANDBOX] [RE] Submission #{} exited with code {} on Host OS in {}ms: {}",
                        submissionId, exitCode, execTimeMs, stderr.toString().trim());
                return RunResult.error("RE", stderr.toString());
            }

            logger.info("✅ [LOCAL_SANDBOX] [OK] Submission #{} completed test case on Host OS in {}ms", submissionId, execTimeMs);
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
        mainClassBySubmission.remove(submissionId);
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

    private void writeSourceFile(String workDir, String sourceCode, String language, String className) throws IOException {
        String fileName = switch (language.toLowerCase()) {
            case "c" -> "solution.c";
            case "cpp" -> "solution.cpp";
            case "python" -> "solution.py";
            case "java" -> className + ".java";
            default -> "solution.txt";
        };
        writeFile(workDir + File.separator + fileName, sourceCode);
    }

    public String getWorkDir(int submissionId) {
        return TEMP_DIR + File.separator + submissionId;
    }

    private List<String> buildRunCommand(int submissionId, String language, String workDir) {
        List<String> cmd = new ArrayList<>();
        switch (language.toLowerCase()) {
            case "java":
                String className = mainClassBySubmission.get(submissionId);
                if (className == null || className.isEmpty()) {
                    File metaFile = new File(workDir, ".main_class");
                    if (metaFile.exists()) {
                        try {
                            className = Files.readString(metaFile.toPath(), StandardCharsets.UTF_8).trim();
                        } catch (Exception ignored) {}
                    }
                }
                if (className == null || className.isEmpty()) {
                    File[] classFiles = new File(workDir).listFiles((dir, name) -> name.endsWith(".class") && !name.contains("$"));
                    if (classFiles != null && classFiles.length > 0) {
                        className = classFiles[0].getName().replace(".class", "");
                    } else {
                        className = "Main";
                    }
                }
                cmd.add("java");
                cmd.add("-cp");
                cmd.add(workDir);
                cmd.add(className);
                break;
            case "c":
            case "cpp":
                File exeFile = new File(workDir, "solution.exe");
                if (exeFile.exists()) {
                    cmd.add(exeFile.getAbsolutePath());
                } else {
                    cmd.add(workDir + File.separator + "solution");
                }
                break;
            case "python":
                cmd.add(getPythonCommand());
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
