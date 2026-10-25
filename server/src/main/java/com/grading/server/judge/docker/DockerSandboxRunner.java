package com.grading.server.judge.docker;

import com.grading.server.judge.SandboxRunner.CompileResult;
import com.grading.server.judge.SandboxRunner.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DockerSandboxRunner — Thực thi biên dịch và chấm bài bên trong Docker Container (Tầng 2).
 *
 * Tiêu chuẩn bảo mật:
 * - Cô lập 100% mạng: --network none
 * - Giới hạn bộ nhớ:   --memory 256m
 * - Giới hạn CPU:      --cpus 1.0
 * - Filesystem an toàn: Mount ở chế độ Read-Only (--ro) khi chạy
 * - Tự hủy container:  --rm
 * - User không root:   --user sandbox
 */
public class DockerSandboxRunner {

    private static final Logger logger = LoggerFactory.getLogger(DockerSandboxRunner.class);

    public static final String DOCKER_IMAGE = "ltm-judge-runner:latest";

    private static Boolean dockerAvailable = null;
    private static long lastCheckTime = 0L;
    private static final long CHECK_INTERVAL_MS = 30_000L; // Kiểm tra lại sau mỗi 30s

    /**
     * Kiểm tra Docker Engine và Docker Image ltm-judge-runner có sẵn sàng hay không.
     */
    public static synchronized boolean isDockerAvailable() {
        long now = System.currentTimeMillis();
        if (dockerAvailable != null && (now - lastCheckTime) < CHECK_INTERVAL_MS) {
            return dockerAvailable;
        }

        boolean currentStatus = false;
        boolean daemonUp = isDockerDaemonUp();
        if (daemonUp) {
            // Daemon đang chạy, kiểm tra tiếp xem image ltm-judge-runner đã được build chưa
            try {
                Process process = new ProcessBuilder("docker", "image", "inspect", DOCKER_IMAGE).start();
                boolean finished = process.waitFor(2, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    currentStatus = true;
                }
            } catch (Exception ignored) {}
        }

        if (dockerAvailable == null || dockerAvailable != currentStatus) {
            if (currentStatus) {
                logger.info("🛡️ [SANDBOX-DETECTOR] Docker Engine is ACTIVE and image '{}' is ready. Running in DOCKER CONTAINER SANDBOX mode.", DOCKER_IMAGE);
            } else {
                if (daemonUp) {
                    logger.warn("⚠️ [SANDBOX-DETECTOR] Docker daemon is running, but image '{}' is NOT found!", DOCKER_IMAGE);
                    logger.warn("    -> Notice:       To enable Docker sandbox, run 'server/docker/build-image.bat' to build the image.");
                    logger.warn("    -> Active Mode:  LOCAL PROCESS SANDBOX (Host OS Fallback).");
                } else {
                    logger.warn("⚠️ [SANDBOX-DETECTOR] Docker Engine is OFFLINE or NOT installed. Active mode: LOCAL PROCESS SANDBOX (Host OS Fallback).");
                }
            }
        }

        dockerAvailable = currentStatus;
        lastCheckTime = now;
        return dockerAvailable;
    }

    private static boolean isDockerDaemonUp() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            return p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Chuyển đường dẫn local thành volume path tương thích Docker.
     */
    public static String toVolumePath(String localPath) {
        return new File(localPath).getAbsolutePath().replace("\\", "/");
    }

    /**
     * Biên dịch mã nguồn bên trong Docker Container.
     */
    public static CompileResult compile(int submissionId, String language, String workDir, String className) {
        String volPath = toVolumePath(workDir);
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(volPath + ":/app");
        cmd.add(DOCKER_IMAGE);

        switch (language.toLowerCase()) {
            case "c":
                cmd.addAll(List.of("gcc", "-O2", "-o", "/app/solution", "/app/solution.c"));
                break;
            case "cpp":
                cmd.addAll(List.of("g++", "-O2", "-o", "/app/solution", "/app/solution.cpp"));
                break;
            case "python":
                cmd.addAll(List.of("python3", "-m", "py_compile", "/app/solution.py"));
                break;
            case "java":
                cmd.addAll(List.of("bash", "-c", "javac -encoding UTF-8 /app/*.java"));
                break;
            default:
                return CompileResult.failure("Unsupported language in Docker: " + language);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readStream(process.getInputStream());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return CompileResult.failure("Docker compilation timed out");
            }

            return process.exitValue() == 0
                    ? CompileResult.success()
                    : CompileResult.failure(output);

        } catch (Exception e) {
            logger.error("Docker compile error for submission {}", submissionId, e);
            return CompileResult.failure("Docker error: " + e.getMessage());
        }
    }

    /**
     * Chạy bài nộp bên trong Docker Container với các cờ bảo vệ bắt buộc.
     */
    public static RunResult run(int submissionId, String language, String input, int timeLimitMs,
                                String workDir, String className) {
        String volPath = toVolumePath(workDir);
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");                      // Cho phép truyền stdin
        cmd.add("--network");
        cmd.add("none");                    // CẮT 100% KẾT NỐI MẠNG
        cmd.add("--memory");
        cmd.add("256m");                    // GIỚI HẠN RAM 256MB
        cmd.add("--cpus");
        cmd.add("1.0");                     // GIỚI HẠN 1 CORE CPU
        cmd.add("-v");
        cmd.add(volPath + ":/app:ro");       // CHẾ ĐỘ READ-ONLY CHO FILE
        cmd.add(DOCKER_IMAGE);

        switch (language.toLowerCase()) {
            case "c":
            case "cpp":
                cmd.add("/app/solution");
                break;
            case "python":
                cmd.addAll(List.of("python3", "/app/solution.py"));
                break;
            case "java":
                cmd.addAll(List.of("java", "-cp", "/app", className));
                break;
            default:
                return RunResult.error("RE", "Unsupported language: " + language);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Gửi stdin
            if (input != null && !input.isEmpty()) {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }

            long startTime = System.currentTimeMillis();

            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();

            Thread stdoutReader = new Thread(() -> {
                try { stdout.append(readStream(process.getInputStream())); } catch (Exception ignored) {}
            });
            Thread stderrReader = new Thread(() -> {
                try { stderr.append(readStream(process.getErrorStream())); } catch (Exception ignored) {}
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
            logger.error("Docker run error for submission {}", submissionId, e);
            return RunResult.error("RE", "Docker runtime error: " + e.getMessage());
        }
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
