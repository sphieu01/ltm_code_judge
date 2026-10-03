package com.grading.server;

import com.grading.server.config.DatabaseConfig;
import com.grading.server.config.ServerConfig;
import com.grading.server.judge.JudgeQueue;
import com.grading.server.judge.JudgeWorkerPool;
import com.grading.server.network.ConnectionRegistry;
import com.grading.server.network.SocketServer;
import com.grading.server.repository.ProblemRepository;
import com.grading.server.repository.SubmissionRepository;
import com.grading.server.repository.UserRepository;
import com.grading.server.service.AuthService;
import com.grading.server.service.ProblemService;
import com.grading.server.service.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * ServerApp — Entry point của Server Core.
 *
 * Khởi tạo tất cả components theo thứ tự:
 *   1. Database (HikariCP pool)
 *   2. Repositories (JDBC)
 *   3. JudgeQueue
 *   4. JudgeWorkerPool (background threads)
 *   5. Services
 *   6. ConnectionRegistry (singleton)
 *   7. SocketServer (main accept loop)
 *
 * Shutdown Hook: đảm bảo graceful shutdown khi nhận SIGTERM/Ctrl+C.
 *
 * Cách chạy:
 *   mvn package && java -jar target/code-grading-server-1.0.0.jar
 *
 * Với Neon.db, set env vars trước:
 *   set DB_URL=jdbc:postgresql://xxx.neon.tech/mydb?sslmode=require
 *   set DB_USER=myuser
 *   set DB_PASSWORD=mypassword
 */
public class ServerApp {

    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);

    public static void main(String[] args) {
        logger.info("==============================================");
        logger.info("  Code Grading Server — BTL Lập Trình Mạng  ");
        logger.info("==============================================");
        logger.info("Server port: {}", ServerConfig.SERVER_PORT);
        logger.info("Connection pool: {} threads", ServerConfig.CONNECTION_POOL_SIZE);
        logger.info("Judge workers: {}", ServerConfig.JUDGE_WORKER_COUNT);
        logger.info("Judge queue capacity: {}", ServerConfig.JUDGE_QUEUE_CAPACITY);

        // ── 1. Repositories ───────────────────────────────────────────────────
        UserRepository userRepository = new UserRepository();
        ProblemRepository problemRepository = new ProblemRepository();
        SubmissionRepository submissionRepository = new SubmissionRepository();

        // ── 2. Judge infrastructure ───────────────────────────────────────────
        JudgeQueue judgeQueue = new JudgeQueue();
        ConnectionRegistry connectionRegistry = ConnectionRegistry.getInstance();

        // ── 3. Services ───────────────────────────────────────────────────────
        AuthService authService = new AuthService(userRepository);
        ProblemService problemService = new ProblemService(problemRepository);
        SubmissionService submissionService = new SubmissionService(submissionRepository, judgeQueue);

        // ── 4. Judge Worker Pool ──────────────────────────────────────────────
        JudgeWorkerPool workerPool = new JudgeWorkerPool(
                judgeQueue,
                submissionRepository,
                problemRepository,
                connectionRegistry
        );

        // ── 5. Socket Server ──────────────────────────────────────────────────
        SocketServer socketServer = new SocketServer(
                authService,
                problemService,
                submissionService,
                connectionRegistry
        );

        // ── 6. Shutdown Hook — dọn dẹp khi nhận Ctrl+C hoặc SIGTERM ──────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, cleaning up...");

            socketServer.stop();        // Dừng accept loop + drain handler threads
            workerPool.shutdown();      // Dừng judge workers (in metrics)
            DatabaseConfig.close();     // Đóng HikariCP pool

            logger.info("Server shutdown complete. Goodbye!");
        }, "shutdown-hook"));

        // ── 7. Start! (blocking) ──────────────────────────────────────────────
        try {
            socketServer.start(); // Block cho đến khi server bị dừng
        } catch (IOException e) {
            logger.error("Failed to start server on port {}: {}", ServerConfig.SERVER_PORT, e.getMessage());
            logger.error("Possible cause: Port already in use, or insufficient permissions");
            System.exit(1);
        }
    }
}
