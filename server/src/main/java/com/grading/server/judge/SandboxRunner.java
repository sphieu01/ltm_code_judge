package com.grading.server.judge;

import com.grading.server.judge.model.CompileResult;
import com.grading.server.judge.model.RunResult;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý biên dịch và thực thi code Java Core trong môi trường sandbox tạm thời.
 */
public class SandboxRunner {

    public static final long DEFAULT_TIME_LIMIT_MS = 1000; // 1 giây mặc định theo yêu cầu
    private static final String DEFAULT_MEMORY_LIMIT = "-Xmx256m";

    /**
     * Tự động nhận diện tên class chính từ mã nguồn (Phương án B).
     * Ưu tiên public class, nếu không có thì tìm class bất kỳ, mặc định là Main.
     */
    public static String extractClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "Main";
        }

        Matcher publicClassMatcher = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)").matcher(sourceCode);
        if (publicClassMatcher.find()) {
            return publicClassMatcher.group(1);
        }

        Matcher anyClassMatcher = Pattern.compile("class\\s+([A-Za-z0-9_]+)").matcher(sourceCode);
        if (anyClassMatcher.find()) {
            return anyClassMatcher.group(1);
        }

        return "Main";
    }

    /**
     * Biên dịch mã nguồn Java trong thư mục sandbox.
     */
    public CompileResult compile(Path sandboxDir, String className, String sourceCode) {
        try {
            Files.createDirectories(sandboxDir);
            Path sourceFile = sandboxDir.resolve(className + ".java");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("javac", "-encoding", "UTF-8", className + ".java");
            pb.directory(sandboxDir.toFile());

            Process process = pb.start();
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CompileResult.fail("Compilation timed out");
            }

            if (process.exitValue() != 0) {
                return CompileResult.fail(stderr);
            }

            return CompileResult.ok();
        } catch (Exception e) {
            return CompileResult.fail("Internal compile error: " + e.getMessage());
        }
    }

    /**
     * Chạy chương trình với test case và giới hạn thời gian (mặc định 1000ms).
     */
    public RunResult run(Path sandboxDir, String className, TestCase testCase, long timeLimitMs) {
        if (timeLimitMs <= 0) {
            timeLimitMs = DEFAULT_TIME_LIMIT_MS;
        }

        long start = System.currentTimeMillis();
        Process process = null;

        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", ".", DEFAULT_MEMORY_LIMIT, className);
            pb.directory(sandboxDir.toFile());
            process = pb.start();

            // Đọc stdout và stderr song song trên luồng riêng để tránh deadlock buffer
            Process finalProcess = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(finalProcess.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(finalProcess.getErrorStream()));

            // Ghi input vào stdin của process
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                if (testCase.getInput() != null) {
                    writer.write(testCase.getInput());
                    if (!testCase.getInput().endsWith("\n")) {
                        writer.write("\n");
                    }
                    writer.flush();
                }
            } catch (IOException ignored) {
                // Process có thể đã dừng sớm (ví dụ crash ngay khi khởi động)
            }

            // Chờ process chạy với thời gian giới hạn
            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
            long elapsedMs = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                return RunResult.timeout(elapsedMs);
            }

            String stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(1, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return RunResult.runtimeError(stderr, exitCode, elapsedMs);
            }

            // So khớp kết quả thực tế với đáp án kỳ vọng
            boolean isMatched = OutputComparator.compare(stdout, testCase.getExpectedOutput());
            VerdictType verdict = isMatched ? VerdictType.AC : VerdictType.WA;

            return new RunResult(verdict, stdout, stderr, 0, elapsedMs);

        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - start;
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return RunResult.runtimeError(e.getMessage(), -1, elapsedMs);
        }
    }

    /**
     * Dọn dẹp thư mục tạm sau khi chấm xong.
     */
    public void cleanup(Path sandboxDir) {
        if (sandboxDir == null || !Files.exists(sandboxDir)) {
            return;
        }
        try {
            Files.walk(sandboxDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    private String readStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }
}
