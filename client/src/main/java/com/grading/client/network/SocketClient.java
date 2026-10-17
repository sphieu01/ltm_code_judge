package com.grading.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grading.client.protocol.MessageType;
import com.grading.client.protocol.Request;
import com.grading.client.protocol.Response;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * SocketClient — Quản lý kết nối TCP đến Server.
 *
 * Chức năng:
 * - Kết nối / ngắt kết nối TCP socket
 * - Gửi request (serialize JSON, ghi qua PrintWriter)
 * - Nhận response (đọc JSON từ BufferedReader, thread riêng)
 * - Dispatch response đến callback tương ứng
 * - Xử lý push notification (JUDGE_NOTIFICATION)
 *
 * Thread model:
 * - Main/UI thread gọi sendRequest()
 * - Listener thread chạy liên tục đọc response từ server
 * - Callback được gọi trên listener thread → phải dùng Platform.runLater() trong controller
 */
public class SocketClient {

    private static final Gson GSON = new Gson();

    private String host;
    private int port;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private volatile boolean connected = false;

    // Map requestId → callback: khi nhận response, gọi callback tương ứng
    private final Map<String, Consumer<Response>> pendingRequests = new ConcurrentHashMap<>();

    // Callback cho push notifications (JUDGE_NOTIFICATION)
    private Consumer<Response> notificationCallback;

    // Callback khi mất kết nối
    private Runnable onDisconnectCallback;

    // Token JWT sau khi login thành công
    private String authToken;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ======================== CONNECTION ========================

    /**
     * Kết nối đến server qua TCP socket.
     * Sau khi kết nối, khởi động listener thread để nhận response.
     */
    public void connect() throws IOException {
        socket = new Socket();
        // Timeout kết nối 5 giây tránh treo vô hạn nếu server không phản hồi
        socket.connect(new java.net.InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);

        writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true // autoFlush
        );
        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        connected = true;

        // Khởi động listener thread
        listenerThread = new Thread(this::listenForResponses, "SocketClient-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Ngắt kết nối một cách an toàn và bất đồng bộ (không block UI thread).
     */
    public void disconnect() {
        if (!connected && (socket == null || socket.isClosed())) {
            return;
        }
        connected = false;
        pendingRequests.clear();
        notificationCallback = null;

        // Đẩy toàn bộ quá trình gửi DISCONNECT và đóng socket sang thread nền
        // để UI Thread không bao giờ bị đơ (freeze).
        Thread dcThread = new Thread(() -> {
            try {
                if (writer != null && socket != null && !socket.isClosed()) {
                    Request disconnectReq = new Request(
                            MessageType.DISCONNECT, generateRequestId(), authToken, null);
                    writer.println(GSON.toJson(disconnectReq));
                    writer.flush();
                }
            } catch (Exception ignored) {}

            closeResources();
        }, "SocketClient-Disconnect");
        dcThread.setDaemon(true);
        dcThread.start();
    }

    private void closeResources() {
        connected = false;
        // ĐÓNG SOCKET TRƯỚC HẾT: unblock ngay lập tức lệnh reader.readLine()
        // đang chờ trên Listener Thread, tránh deadlock lock BufferedReader!
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}

        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}

        if (listenerThread != null && listenerThread.isAlive()) {
            listenerThread.interrupt();
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    // ======================== SEND REQUEST ========================

    /**
     * Gửi request đến server và đăng ký callback nhận response.
     *
     * @param type     Loại message
     * @param data     Data payload (JsonObject)
     * @param callback Callback khi nhận được response (gọi trên listener thread)
     * @return requestId để tracking
     */
    public String sendRequest(MessageType type, JsonObject data, Consumer<Response> callback) {
        if (!isConnected()) {
            // Trả lỗi ngay nếu chưa kết nối
            Response errorResp = new Response();
            errorResp.setType("ERROR");
            errorResp.setStatus("FAIL");
            errorResp.setMessage("Not connected to server");
            if (callback != null) callback.accept(errorResp);
            return null;
        }

        String requestId = generateRequestId();
        Request request = new Request(type, requestId, authToken, data);

        // Đăng ký callback chờ response
        if (callback != null) {
            pendingRequests.put(requestId, callback);
        }

        // Serialize JSON và gửi qua socket (1 dòng + '\n')
        String json = GSON.toJson(request);
        writer.println(json);

        // Timeout: xóa callback sau 30 giây nếu không nhận response
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
            Consumer<Response> expired = pendingRequests.remove(requestId);
            if (expired != null) {
                Response timeoutResp = new Response();
                timeoutResp.setType("ERROR");
                timeoutResp.setRequestId(requestId);
                timeoutResp.setStatus("FAIL");
                timeoutResp.setMessage("Request timed out");
                expired.accept(timeoutResp);
            }
        });

        return requestId;
    }

    // ======================== LISTENER THREAD ========================

    /**
     * Thread lắng nghe response từ server.
     * Chạy liên tục cho đến khi disconnect.
     *
     * Mỗi response là 1 dòng JSON kết thúc bằng '\n'.
     * Sau khi parse, dispatch đến:
     *   - pendingRequests callback (nếu có requestId match)
     *   - notificationCallback (nếu là JUDGE_NOTIFICATION)
     */
    private void listenForResponses() {
        try {
            String line;
            while (connected && (line = reader.readLine()) != null) {
                try {
                    Response response = GSON.fromJson(line, Response.class);
                    dispatchResponse(response);
                } catch (Exception e) {
                    System.err.println("[Client] Failed to parse response: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            // Socket đóng → disconnect bình thường
            if (connected) {
                System.err.println("[Client] Connection lost: " + e.getMessage());
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[Client] IO error: " + e.getMessage());
            }
        } finally {
            connected = false;
            if (onDisconnectCallback != null) {
                onDisconnectCallback.run();
            }
        }
    }

    /**
     * Dispatch response đến đúng callback.
     */
    private void dispatchResponse(Response response) {
        // 1. Nếu là push notification → gọi notificationCallback
        MessageType type = response.getMessageType();
        if (type == MessageType.JUDGE_NOTIFICATION) {
            if (notificationCallback != null) {
                notificationCallback.accept(response);
            }
            return;
        }

        // 2. Nếu có requestId → gọi pending callback
        String requestId = response.getRequestId();
        if (requestId != null) {
            Consumer<Response> callback = pendingRequests.remove(requestId);
            if (callback != null) {
                callback.accept(response);
            }
        }
    }

    // ======================== CONVENIENCE METHODS ========================

    /**
     * Gửi LOGIN request.
     */
    public void login(String username, String password, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        sendRequest(MessageType.LOGIN, data, callback);
    }

    /**
     * Gửi REGISTER request kèm vai trò (role: STUDENT / TEACHER).
     */
    public void register(String username, String password, String fullName, String role, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        data.addProperty("fullName", fullName);
        data.addProperty("role", role);
        sendRequest(MessageType.REGISTER, data, callback);
    }

    public void register(String username, String password, String fullName, Consumer<Response> callback) {
        register(username, password, fullName, "STUDENT", callback);
    }

    /**
     * Gửi GET_PROBLEMS request.
     */
    public void getProblems(Consumer<Response> callback) {
        sendRequest(MessageType.GET_PROBLEMS, null, callback);
    }

    /**
     * Gửi GET_PROBLEM_DETAIL request.
     */
    public void getProblemDetail(int problemId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("problemId", problemId);
        sendRequest(MessageType.GET_PROBLEM_DETAIL, data, callback);
    }

    /**
     * Gửi SUBMIT request — nộp code.
     */
    public void submitCode(int problemId, String language, String sourceCode, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("problemId", problemId);
        data.addProperty("language", language);
        data.addProperty("sourceCode", sourceCode);
        sendRequest(MessageType.SUBMIT, data, callback);
    }

    /**
     * Gửi GET_RESULT request.
     */
    public void getResult(int submissionId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("submissionId", submissionId);
        sendRequest(MessageType.GET_RESULT, data, callback);
    }

    /**
     * Gửi GET_HISTORY request.
     */
    public void getHistory(Consumer<Response> callback) {
        sendRequest(MessageType.GET_HISTORY, null, callback);
    }

    // ======================== SETTERS ========================

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * Đăng ký callback nhận push notification (JUDGE_NOTIFICATION).
     */
    public void setNotificationCallback(Consumer<Response> callback) {
        this.notificationCallback = callback;
    }

    /**
     * Đăng ký callback khi mất kết nối.
     */
    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    // ======================== UTILS ========================

    private String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
