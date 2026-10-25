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

    // Callback khi bị kick phiên (FORCE_LOGOUT)
    private Consumer<String> forceLogoutCallback;

    // Callback cho cảnh báo giám sát gửi tới Giáo viên (PROCTOR_ALERT)
    private Consumer<Response> proctorAlertCallback;

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
        // 1. Nếu là push notification hoặc sự kiện giám sát
        MessageType type = response.getMessageType();

        if (type == MessageType.FORCE_LOGOUT) {
            connected = false;
            String reason = response.getMessage() != null
                    ? response.getMessage()
                    : "Tài khoản của bạn đã được đăng nhập từ một thiết bị khác.";
            if (forceLogoutCallback != null) {
                forceLogoutCallback.accept(reason);
            }
            closeResources();
            return;
        }

        if (type == MessageType.PROCTOR_ALERT) {
            if (proctorAlertCallback != null) {
                proctorAlertCallback.accept(response);
            }
            return;
        }

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

    public void changePassword(String oldPassword, String newPassword, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("oldPassword", oldPassword);
        data.addProperty("newPassword", newPassword);
        sendRequest(MessageType.CHANGE_PASSWORD, data, callback);
    }

    /**
     * Gửi GET_PROBLEMS request.
     */
    public void getProblems(Consumer<Response> callback) {
        getProblems(null, callback);
    }

    public void getProblems(Integer classId, Consumer<Response> callback) {
        JsonObject data = null;
        if (classId != null && classId > 0) {
            data = new JsonObject();
            data.addProperty("classId", classId);
        }
        sendRequest(MessageType.GET_PROBLEMS, data, callback);
    }

    /**
     * Gửi CREATE_PROBLEM request (dành cho Giáo viên).
     */
    public void createProblem(JsonObject problemData, Consumer<Response> callback) {
        sendRequest(MessageType.CREATE_PROBLEM, problemData, callback);
    }

    public void updateProblem(JsonObject problemData, Consumer<Response> callback) {
        sendRequest(MessageType.UPDATE_PROBLEM, problemData, callback);
    }

    public void deleteProblem(int problemId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", problemId);
        sendRequest(MessageType.DELETE_PROBLEM, data, callback);
    }

    // ======================== ACADEMIC METHODS ========================

    public void getCourses(Consumer<Response> callback) {
        sendRequest(MessageType.GET_COURSES, null, callback);
    }

    public void createCourse(String code, String name, String language, String description, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("code", code);
        data.addProperty("name", name);
        data.addProperty("language", language);
        data.addProperty("description", description);
        sendRequest(MessageType.CREATE_COURSE, data, callback);
    }

    public void updateCourse(com.grading.client.model.Course course, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", course.getId());
        data.addProperty("code", course.getCode());
        data.addProperty("name", course.getName());
        data.addProperty("language", course.getLanguage());
        data.addProperty("description", course.getDescription());
        data.addProperty("active", course.isActive());
        sendRequest(MessageType.UPDATE_COURSE, data, callback);
    }

    public void deleteCourse(int courseId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", courseId);
        sendRequest(MessageType.DELETE_COURSE, data, callback);
    }

    public void getClasses(Consumer<Response> callback) {
        sendRequest(MessageType.GET_CLASSES, null, callback);
    }

    public void getMyClasses(Consumer<Response> callback) {
        sendRequest(MessageType.GET_MY_CLASSES, null, callback);
    }

    public void createClass(String classCode, String name, int courseId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classCode", classCode);
        data.addProperty("name", name);
        data.addProperty("courseId", courseId);
        sendRequest(MessageType.CREATE_CLASS, data, callback);
    }

    public void updateClass(int id, String classCode, String name, int courseId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("classCode", classCode);
        data.addProperty("name", name);
        data.addProperty("courseId", courseId);
        sendRequest(MessageType.UPDATE_CLASS, data, callback);
    }

    public void deleteClass(int classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("id", classId);
        sendRequest(MessageType.DELETE_CLASS, data, callback);
    }

    public void getStudents(int classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classId", classId);
        sendRequest(MessageType.GET_STUDENTS, data, callback);
    }

    public void getClassStudentStats(int classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classId", classId);
        sendRequest(MessageType.GET_CLASS_STUDENT_STATS, data, callback);
    }

    public void createAssessment(int classId, String title, String opensAt, String closesAt,
                                 int durationMinutes, com.google.gson.JsonArray problemIds,
                                 Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classId", classId);
        data.addProperty("title", title);
        data.addProperty("opensAt", opensAt);
        data.addProperty("closesAt", closesAt);
        data.addProperty("durationMinutes", durationMinutes);
        data.add("problemIds", problemIds);
        sendRequest(MessageType.CREATE_ASSESSMENT, data, callback);
    }

    public void getActiveAssessment(Integer classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        if (classId != null && classId > 0) data.addProperty("classId", classId);
        sendRequest(MessageType.GET_ACTIVE_ASSESSMENT, data, callback);
    }

    public void getStudentAssessments(Consumer<Response> callback) {
        sendRequest(MessageType.GET_STUDENT_ASSESSMENTS, new JsonObject(), callback);
    }

    public void getClassAssessments(int classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classId", classId);
        sendRequest(MessageType.GET_CLASS_ASSESSMENTS, data, callback);
    }

    public void getAssessmentMonitor(int assessmentId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("assessmentId", assessmentId);
        sendRequest(MessageType.GET_ASSESSMENT_MONITOR, data, callback);
    }

    public void startAssessment(int assessmentId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("assessmentId", assessmentId);
        sendRequest(MessageType.START_ASSESSMENT, data, callback);
    }

    public void completeAssessment(int assessmentId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("assessmentId", assessmentId);
        sendRequest(MessageType.COMPLETE_ASSESSMENT, data, callback);
    }

    public void importStudents(int classId, com.google.gson.JsonArray studentsArray, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("classId", classId);
        data.add("students", studentsArray);
        sendRequest(MessageType.IMPORT_STUDENTS, data, callback);
    }

    public void updateStudent(int studentId, String fullName, Integer classId, String newPassword, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("studentId", studentId);
        data.addProperty("fullName", fullName);
        if (classId != null && classId > 0) {
            data.addProperty("classId", classId);
        } else {
            data.add("classId", com.google.gson.JsonNull.INSTANCE);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            data.addProperty("newPassword", newPassword);
        }
        sendRequest(MessageType.UPDATE_STUDENT, data, callback);
    }

    public void deleteStudent(int studentId, Consumer<Response> callback) {
        deleteStudent(studentId, null, callback);
    }

    public void deleteStudent(int studentId, Integer classId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("studentId", studentId);
        if (classId != null && classId > 0) data.addProperty("classId", classId);
        sendRequest(MessageType.DELETE_STUDENT, data, callback);
    }

    /**
     * Gửi TOGGLE_COURSE_ACTIVE request — bật/tắt hiển thị môn học cho sinh viên.
     */
    public void toggleCourseActive(int courseId, boolean active, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("courseId", courseId);
        data.addProperty("active", active);
        sendRequest(MessageType.TOGGLE_COURSE_ACTIVE, data, callback);
    }

    /**
     * Gửi TOGGLE_PROBLEM_ACTIVE request — bật/tắt hiển thị từng bài tập cho sinh viên.
     */
    public void toggleProblemActive(int problemId, boolean active, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("problemId", problemId);
        data.addProperty("active", active);
        sendRequest(MessageType.TOGGLE_PROBLEM_ACTIVE, data, callback);
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
     * Gửi GET_HISTORY request cho 1 bài tập cụ thể hoặc tất cả bài nộp nếu problemId == null.
     */
    public void getHistory(Integer problemId, Consumer<Response> callback) {
        getHistory(problemId, null, callback);
    }

    public void getHistory(Integer problemId, Integer assessmentId, Consumer<Response> callback) {
        JsonObject data = null;
        if ((problemId != null && problemId > 0) || (assessmentId != null && assessmentId > 0)) {
            data = new JsonObject();
            if (problemId != null && problemId > 0) data.addProperty("problemId", problemId);
            if (assessmentId != null && assessmentId > 0) data.addProperty("assessmentId", assessmentId);
        }
        sendRequest(MessageType.GET_HISTORY, data, callback);
    }

    /**
     * Gửi GET_HISTORY request lấy toàn bộ lịch sử nộp bài của sinh viên.
     */
    public void getHistory(Consumer<Response> callback) {
        getHistory(null, callback);
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
     * Đăng ký callback khi tài khoản bị đăng nhập từ thiết bị khác (FORCE_LOGOUT).
     */
    public void setForceLogoutCallback(Consumer<String> callback) {
        this.forceLogoutCallback = callback;
    }

    /**
     * Đăng ký callback nhận cảnh báo giám sát thời gian thực (PROCTOR_ALERT - Teacher).
     */
    public void setProctorAlertCallback(Consumer<Response> callback) {
        this.proctorAlertCallback = callback;
    }

    /**
     * Đăng ký callback khi mất kết nối.
     */
    public void setOnDisconnectCallback(Runnable callback) {
        this.onDisconnectCallback = callback;
    }

    // ======================== PROCTORING METHODS ========================

    /**
     * Báo cáo sự kiện giám sát phòng thi lên server (VD: AI_TOOL_DETECTED, FOCUS_LOSS...).
     */
    public void sendProctorEvent(int problemId, String problemTitle, String eventType, String detail,
                                 Integer assessmentId, Consumer<Response> callback) {
        JsonObject data = new JsonObject();
        data.addProperty("problemId", problemId);
        data.addProperty("problemTitle", problemTitle);
        data.addProperty("eventType", eventType);
        if (detail != null) {
            data.addProperty("detail", detail);
        }
        if (assessmentId != null && assessmentId > 0) data.addProperty("assessmentId", assessmentId);
        sendRequest(MessageType.PROCTOR_EVENT, data, callback);
    }

    public void sendProctorEvent(int problemId, String problemTitle, String eventType, String detail,
                                 Consumer<Response> callback) {
        sendProctorEvent(problemId, problemTitle, eventType, detail, null, callback);
    }

    public void sendProctorEvent(int problemId, String problemTitle, String eventType, Consumer<Response> callback) {
        sendProctorEvent(problemId, problemTitle, eventType, null, callback);
    }

    /**
     * Lấy toàn bộ nhật ký giám sát (dành cho Giáo viên).
     */
    public void getProctorLogs(Consumer<Response> callback) {
        sendRequest(MessageType.GET_PROCTOR_LOGS, null, callback);
    }

    // ======================== UTILS ========================

    private String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
