package com.grading.server.network;

import com.grading.server.config.ServerConfig;
import com.grading.server.service.AuthService;
import com.grading.server.service.ProblemService;
import com.grading.server.service.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SocketServer — Lắng nghe TCP connections và tạo ClientHandler thread cho mỗi client.
 *
 * Architecture:
 *   - 1 main thread: accept() loop — chấp nhận connection mới
 *   - N handler threads: ExecutorService.newFixedThreadPool(CONNECTION_POOL_SIZE)
 *     Mỗi connection được submit vào pool dưới dạng 1 ClientHandler Runnable
 *
 * Thread model này đảm bảo:
 *   - Nhiều client có thể kết nối đồng thời (mỗi client 1 thread)
 *   - Giới hạn tối đa số connection bằng pool size (20 mặc định)
 *   - Graceful shutdown: đóng ServerSocket → dừng accept loop → drain pool
 */
public class SocketServer {

    private static final Logger logger = LoggerFactory.getLogger(SocketServer.class);

    private final int port;
    private final AuthService authService;
    private final ProblemService problemService;
    private final SubmissionService submissionService;
    private final com.grading.server.service.AcademicService academicService;
    private final ConnectionRegistry connectionRegistry;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    // Metrics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger rejectedConnections = new AtomicInteger(0);

    public SocketServer(AuthService authService,
                        ProblemService problemService,
                        SubmissionService submissionService,
                        com.grading.server.service.AcademicService academicService,
                        ConnectionRegistry connectionRegistry) {
        this.port = ServerConfig.SERVER_PORT;
        this.authService = authService;
        this.problemService = problemService;
        this.submissionService = submissionService;
        this.academicService = academicService;
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Khởi động server — bind port và bắt đầu accept loop.
     * Method này block cho đến khi server dừng.
     *
     * @throws IOException nếu không thể bind port
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(
                port,
                ServerConfig.SOCKET_BACKLOG
        );
        serverSocket.setReuseAddress(true);

        threadPool = Executors.newFixedThreadPool(ServerConfig.CONNECTION_POOL_SIZE);
        running = true;

        logger.info("Server started on port {} (max connections: {})",
                port, ServerConfig.CONNECTION_POOL_SIZE);
        logger.info("Waiting for connections...");

        // Accept loop — chạy trong main thread của SocketServer
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept(); // block đến khi có client

                // Set socket timeout để tránh idle connection giữ thread vô hạn
                clientSocket.setSoTimeout(ServerConfig.SOCKET_TIMEOUT_MS);

                int connId = totalConnections.incrementAndGet();
                logger.info("Connection #{} accepted from {}:{}",
                        connId,
                        clientSocket.getInetAddress().getHostAddress(),
                        clientSocket.getPort());

                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        authService,
                        problemService,
                        submissionService,
                        academicService,
                        connectionRegistry
                );

                threadPool.submit(handler);


            } catch (SocketException e) {
                // Xảy ra khi serverSocket.close() được gọi từ shutdown()
                if (!running) {
                    logger.info("Server accept loop terminated (shutdown)");
                } else {
                    logger.error("Unexpected socket error", e);
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    /**
     * Dừng server gracefully.
     * Gọi từ shutdown hook trong ServerApp.
     */
    public void stop() {
        logger.info("Stopping SocketServer...");
        running = false;

        // Đóng ServerSocket để thoát accept loop
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing ServerSocket", e);
        }

        // Dừng thread pool — chờ các handler đang chạy hoàn thành
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                    logger.warn("Thread pool forced shutdown");
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("SocketServer stopped. Total connections served: {}", totalConnections.get());
    }

    public boolean isRunning() { return running; }
    public int getTotalConnections() { return totalConnections.get(); }
    public int getActiveConnections() { return connectionRegistry.getActiveCount(); }
}
