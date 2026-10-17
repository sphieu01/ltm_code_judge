package com.grading.server.network;

import com.google.gson.JsonObject;
import com.grading.server.model.Problem;
import com.grading.server.model.Submission;
import com.grading.server.model.User;
import com.grading.server.protocol.Message;
import com.grading.server.protocol.MessageSerializer;
import com.grading.server.protocol.MessageType;
import com.grading.server.service.AuthService;
import com.grading.server.service.ProblemService;
import com.grading.server.service.SubmissionService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ClientHandler — xử lý 1 TCP connection với 1 client.
 *
 * Mỗi ClientHandler chạy trong 1 thread riêng (được tạo bởi SocketServer).
 * Thread này loop đọc JSON messages từ socket, routing đến service,
 * và ghi JSON response trả về.
 *
 * Protocol: newline-delimited JSON
 *   Read:  BufferedReader.readLine() — block đến khi có '\n'
 *   Write: PrintWriter.println(json) — tự thêm '\n'
 *
 * Lifecycle:
 *   1. ClientHandler được tạo và chạy trong thread
 *   2. Loop: đọc message → route → gửi response
 *   3. Kết thúc khi: client gửi DISCONNECT, readLine() = null, socket timeout, exception
 *   4. Cleanup: unregister khỏi ConnectionRegistry
 */
public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final AuthService authService;
    private final ProblemService problemService;
    private final SubmissionService submissionService;
    private final ConnectionRegistry connectionRegistry;

    // I/O streams
    private BufferedReader reader;
    private PrintWriter writer;

    // Session state
    private int authenticatedUserId = -1;   // -1 = chưa đăng nhập
    private String authenticatedRole = null;

    public ClientHandler(Socket socket,
                         AuthService authService,
                         ProblemService problemService,
                         SubmissionService submissionService,
                         ConnectionRegistry connectionRegistry) {
        this.socket = socket;
        this.authService = authService;
        this.problemService = problemService;
        this.submissionService = submissionService;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void run() {
        String clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        logger.info("New connection from {}", clientAddress);

        try {
            // Setup I/O streams
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Main message loop
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue; // bỏ qua dòng rỗng

                handleMessage(line.trim());
            }

            // readLine() = null → client đã đóng connection
            logger.info("Client {} disconnected (EOF)", clientAddress);

        } catch (SocketTimeoutException e) {
            logger.info("Client {} timed out (no activity)", clientAddress);
        } catch (IOException e) {
            // Socket đóng bất ngờ — bình thường khi client crash hoặc mất mạng
            if (!socket.isClosed()) {
                logger.warn("I/O error with client {}: {}", clientAddress, e.getMessage());
            }
        } finally {
            cleanup(clientAddress);
        }
    }

    // ─── Message routing ──────────────────────────────────────────────────────

    /**
     * Parse JSON và route đến handler tương ứng.
     */
    private void handleMessage(String json) {
        Message request = MessageSerializer.fromJson(json);

        if (request == null || request.getType() == null) {
            sendError(null, "Invalid JSON format");
            return;
        }

        logger.debug("Received {} from userId={}", request.getType(), authenticatedUserId);

        // Route theo message type
        switch (request.getType()) {
            case LOGIN    -> handleLogin(request);
            case REGISTER -> handleRegister(request);
            case DISCONNECT -> {
                logger.info("Client userId={} sent DISCONNECT", authenticatedUserId);
                return; // sẽ thoát loop ở vòng lặp tiếp theo khi readLine() = null
            }
            case PING -> sendPong(request.getRequestId());

            // Các request sau đây yêu cầu phải đăng nhập
            default -> {
                if (!isAuthenticated(request)) return; // đã gửi UNAUTHORIZED
                switch (request.getType()) {
                    case GET_PROBLEMS       -> handleGetProblems(request);
                    case GET_PROBLEM_DETAIL -> handleGetProblemDetail(request);
                    case SUBMIT             -> handleSubmit(request);
                    case GET_RESULT         -> handleGetResult(request);
                    case GET_HISTORY        -> handleGetHistory(request);
                    default -> sendError(request.getRequestId(),
                            "Unknown message type: " + request.getType());
                }
            }
        }
    }

    // ─── Auth Handlers ────────────────────────────────────────────────────────

    private void handleLogin(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            String username = data.get("username").getAsString();
            String password = data.get("password").getAsString();

            String token = authService.login(username, password);
            int userId = authService.getUserIdFromToken(token);
            String role = authService.getRoleFromToken(token);
            String fullName = authService.getFullNameFromToken(token);

            // Update session state
            authenticatedUserId = userId;
            authenticatedRole = role;

            // Register connection để nhận push notifications
            connectionRegistry.register(userId, writer);

            // Build response
            JsonObject responseData = new JsonObject();
            responseData.addProperty("token", token);
            responseData.addProperty("userId", userId);
            responseData.addProperty("username", username);
            responseData.addProperty("role", role);
            if (fullName != null) {
                responseData.addProperty("fullName", fullName);
            }

            Message response = Message.success(
                    MessageType.LOGIN_RESPONSE,
                    request.getRequestId(),
                    "Đăng nhập thành công",
                    responseData
            );
            send(response);

            logger.info("User '{}' (id={}) logged in from {}", username, userId,
                    socket.getInetAddress().getHostAddress());

        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("Login error", e);
            sendError(request.getRequestId(), "Lỗi server, vui lòng thử lại");
        }
    }

    private void handleRegister(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            String username = data.get("username").getAsString();
            String password = data.get("password").getAsString();
            String fullName = data.has("fullName") ? data.get("fullName").getAsString() : username;
            String role = data.has("role") ? data.get("role").getAsString() : "STUDENT";

            User user = authService.register(username, password, fullName, role);

            JsonObject responseData = new JsonObject();
            responseData.addProperty("userId", user.getId());
            responseData.addProperty("username", user.getUsername());
            responseData.addProperty("fullName", user.getFullName());
            responseData.addProperty("role", user.getRole());

            Message response = Message.success(
                    MessageType.REGISTER_RESPONSE,
                    request.getRequestId(),
                    "Đăng ký thành công",
                    responseData
            );
            send(response);

        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("Register error", e);
            sendError(request.getRequestId(), "Lỗi server, vui lòng thử lại");
        }
    }

    // ─── Problem Handlers ─────────────────────────────────────────────────────

    private void handleGetProblems(Message request) {
        try {
            List<Problem> problems = problemService.getAllProblems();

            Message response = Message.success(
                    MessageType.GET_PROBLEMS_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(problems)
            );
            send(response);

        } catch (Exception e) {
            logger.error("GetProblems error", e);
            sendError(request.getRequestId(), "Không thể lấy danh sách bài");
        }
    }

    private void handleGetProblemDetail(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int problemId = data.get("problemId").getAsInt();

            Problem problem = problemService.getProblemDetail(problemId);

            Message response = Message.success(
                    MessageType.GET_PROBLEM_DETAIL_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(problem)
            );
            send(response);

        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("GetProblemDetail error", e);
            sendError(request.getRequestId(), "Không thể lấy chi tiết bài");
        }
    }

    // ─── Submission Handlers ──────────────────────────────────────────────────

    private void handleSubmit(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int problemId = data.get("problemId").getAsInt();
            String sourceCode = data.get("sourceCode").getAsString();
            String language = data.get("language").getAsString();

            Submission submission = submissionService.submit(
                    authenticatedUserId, problemId, sourceCode, language
            );

            // Trả về PENDING ngay lập tức — kết quả sẽ được push sau khi chấm xong
            JsonObject responseData = new JsonObject();
            responseData.addProperty("submissionId", submission.getId());
            responseData.addProperty("verdict", submission.getVerdict()); // "PENDING"
            responseData.addProperty("submittedAt", submission.getSubmittedAt());
            responseData.addProperty("queueSize", submissionService.getHistory(authenticatedUserId).size());

            Message response = Message.success(
                    MessageType.SUBMIT_RESPONSE,
                    request.getRequestId(),
                    "Bài nộp đã được nhận, đang chờ chấm...",
                    responseData
            );
            send(response);

        } catch (IllegalStateException e) {
            // Queue đầy
            sendError(request.getRequestId(), e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("Submit error", e);
            sendError(request.getRequestId(), "Lỗi server khi nộp bài");
        }
    }

    private void handleGetResult(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int submissionId = data.get("submissionId").getAsInt();

            Submission submission = submissionService.getResult(submissionId, authenticatedUserId);

            Message response = Message.success(
                    MessageType.GET_RESULT_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(submission)
            );
            send(response);

        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("GetResult error", e);
            sendError(request.getRequestId(), "Không thể lấy kết quả");
        }
    }

    private void handleGetHistory(Message request) {
        try {
            // Optional: lọc theo problemId nếu có
            int problemId = -1;
            if (request.getData() != null && !request.getData().isJsonNull()) {
                JsonObject data = request.getData().getAsJsonObject();
                if (data.has("problemId")) {
                    problemId = data.get("problemId").getAsInt();
                }
            }

            List<Submission> history;
            if (problemId > 0) {
                history = submissionService.getHistoryForProblem(authenticatedUserId, problemId);
            } else {
                history = submissionService.getHistory(authenticatedUserId);
            }

            Message response = Message.success(
                    MessageType.GET_HISTORY_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(history)
            );
            send(response);

        } catch (Exception e) {
            logger.error("GetHistory error", e);
            sendError(request.getRequestId(), "Không thể lấy lịch sử");
        }
    }

    // ─── Auth middleware ──────────────────────────────────────────────────────

    /**
     * Kiểm tra token trong request và verify.
     * Cập nhật session nếu hợp lệ.
     *
     * @return true nếu authenticated, false nếu không (đã gửi UNAUTHORIZED)
     */
    private boolean isAuthenticated(Message request) {
        String token = request.getToken();

        if (token == null || token.isBlank()) {
            send(Message.unauthorized(request.getRequestId()));
            return false;
        }

        try {
            int userId = authService.getUserIdFromToken(token);
            if (userId < 0) {
                send(Message.unauthorized(request.getRequestId()));
                return false;
            }

            // Đồng bộ session (nếu client reconnect với token cũ)
            if (authenticatedUserId != userId) {
                authenticatedUserId = userId;
                authenticatedRole = authService.getRoleFromToken(token);
                connectionRegistry.register(userId, writer);
            }

            return true;

        } catch (JwtException e) {
            send(Message.unauthorized(request.getRequestId()));
            return false;
        }
    }

    // ─── Send helpers ─────────────────────────────────────────────────────────

    /** Gửi Message ra socket */
    private synchronized void send(Message message) {
        try {
            String json = MessageSerializer.toJson(message);
            writer.println(json); // println tự thêm '\n'
            if (writer.checkError()) {
                logger.warn("Socket write error for userId={}", authenticatedUserId);
            }
        } catch (Exception e) {
            logger.error("Error sending response", e);
        }
    }

    /** Gửi ERROR response */
    private void sendError(String requestId, String errorMessage) {
        send(Message.error(requestId, errorMessage));
    }

    /** Gửi PONG response cho PING */
    private void sendPong(String requestId) {
        Message pong = new Message(MessageType.PONG, requestId);
        send(pong);
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanup(String clientAddress) {
        // Unregister từ ConnectionRegistry
        if (authenticatedUserId > 0) {
            connectionRegistry.unregister(authenticatedUserId);
        }

        // Đóng socket và streams
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}

        if (writer != null) writer.close();

        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        logger.info("Cleaned up connection for {} (userId={})", clientAddress, authenticatedUserId);
    }
}
