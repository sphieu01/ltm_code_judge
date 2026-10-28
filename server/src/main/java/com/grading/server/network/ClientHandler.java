package com.grading.server.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.grading.server.model.CreditClass;
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
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
    private final com.grading.server.service.AcademicService academicService;
    private final com.grading.server.service.ProctorService proctorService;
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
                         com.grading.server.service.AcademicService academicService,
                         com.grading.server.service.ProctorService proctorService,
                         ConnectionRegistry connectionRegistry) {
        this.socket = socket;
        this.authService = authService;
        this.problemService = problemService;
        this.submissionService = submissionService;
        this.academicService = academicService;
        this.proctorService = proctorService;
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
                    case CREATE_PROBLEM     -> handleCreateProblem(request);
                    case UPDATE_PROBLEM     -> handleUpdateProblem(request);
                    case DELETE_PROBLEM     -> handleDeleteProblem(request);
                    case SUBMIT             -> handleSubmit(request);
                    case GET_RESULT         -> handleGetResult(request);
                    case GET_HISTORY        -> handleGetHistory(request);
                    case GET_COURSES        -> handleGetCourses(request);
                    case CREATE_COURSE      -> handleCreateCourse(request);
                    case UPDATE_COURSE      -> handleUpdateCourse(request);
                    case DELETE_COURSE      -> handleDeleteCourse(request);
                    case GET_CLASSES        -> handleGetClasses(request);
                    case CREATE_CLASS       -> handleCreateClass(request);
                    case UPDATE_CLASS       -> handleUpdateClass(request);
                    case DELETE_CLASS       -> handleDeleteClass(request);
                    case GET_CLASS_PROBLEMS -> handleGetClassProblems(request);
                    case ASSIGN_CLASS_PROBLEMS -> handleAssignClassProblems(request);
                    case REMOVE_CLASS_PROBLEM -> handleRemoveClassProblem(request);
                    case GET_STUDENTS       -> handleGetStudents(request);
                    case IMPORT_STUDENTS    -> handleImportStudents(request);
                    case UPDATE_STUDENT     -> handleUpdateStudent(request);
                    case DELETE_STUDENT     -> handleDeleteStudent(request);
                    case GET_CLASS_STUDENT_STATS -> handleGetClassStudentStats(request);
                    case GET_MY_CLASSES     -> handleGetMyClasses(request);
                    case TOGGLE_COURSE_ACTIVE -> handleToggleCourseActive(request);
                    case TOGGLE_PROBLEM_ACTIVE -> handleToggleProblemActive(request);
                    case CREATE_ASSESSMENT -> handleCreateAssessment(request);
                    case UPDATE_ASSESSMENT -> handleUpdateAssessment(request);
                    case DELETE_ASSESSMENT -> handleDeleteAssessment(request);
                    case GET_ACTIVE_ASSESSMENT -> handleGetActiveAssessment(request);
                    case GET_STUDENT_ASSESSMENTS -> handleGetStudentAssessments(request);
                    case GET_CLASS_ASSESSMENTS -> handleGetClassAssessments(request);
                    case GET_ASSESSMENT_MONITOR -> handleGetAssessmentMonitor(request);
                    case START_ASSESSMENT -> handleStartAssessment(request);
                    case COMPLETE_ASSESSMENT -> handleCompleteAssessment(request);
                    case CHANGE_PASSWORD    -> handleChangePassword(request);
                    case PROCTOR_EVENT      -> handleProctorEvent(request);
                    case GET_PROCTOR_LOGS   -> handleGetProctorLogs(request);
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

            // Register connection để nhận push notifications (Single Active Session & Kick-out)
            connectionRegistry.register(userId, writer, socket, socket.getInetAddress().getHostAddress(), role);

            // Build response
            JsonObject responseData = new JsonObject();
            responseData.addProperty("token", token);
            responseData.addProperty("userId", userId);
            responseData.addProperty("username", username);
            responseData.addProperty("role", role);
            if (fullName != null) {
                responseData.addProperty("fullName", fullName);
            }
            Integer classId = authService.getClassIdFromToken(token);
            if (classId != null) {
                responseData.addProperty("classId", classId);
            }
            String className = authService.getClassNameFromToken(token);
            if (className != null) {
                responseData.addProperty("className", className);
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
            int classId = 0;
            if (request.getData() != null && request.getData().isJsonObject()) {
                JsonObject data = request.getData().getAsJsonObject();
                if (data.has("classId") && !data.get("classId").isJsonNull()) {
                    classId = data.get("classId").getAsInt();
                }
            }

            boolean isTeacher = "TEACHER".equalsIgnoreCase(authenticatedRole);
            List<Problem> problems;
            if (isTeacher) {
                problems = (classId > 0)
                        ? problemService.getProblemsByClass(classId)
                        : problemService.getAllProblems();
            } else {
                JsonObject activeAssessment = academicService.getActiveAssessment(authenticatedUserId, null);
                if (activeAssessment != null) {
                    int assessmentClassId = activeAssessment.get("classId").getAsInt();
                    java.util.Set<Integer> allowed = new java.util.HashSet<>();
                    for (var id : activeAssessment.getAsJsonArray("problemIds")) allowed.add(id.getAsInt());
                    problems = problemService.getProblemsByClassForStudent(assessmentClassId).stream()
                            .filter(problem -> allowed.contains(problem.getId())).toList();
                } else {
                    problems = (classId > 0)
                            ? problemService.getProblemsByClassForStudent(classId)
                            : problemService.getAllProblemsForStudent();
                }
            }

            JsonArray problemRows = MessageSerializer.toJsonElement(problems).getAsJsonArray();
            if (!isTeacher) {
                java.util.Map<Integer, String> statuses = submissionService.getPracticeStatuses(authenticatedUserId);
                for (var element : problemRows) {
                    JsonObject row = element.getAsJsonObject();
                    row.addProperty("status", statuses.getOrDefault(row.get("id").getAsInt(), ""));
                }
            }

            Message response = Message.success(
                    MessageType.GET_PROBLEMS_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    problemRows
            );
            send(response);

        } catch (Exception e) {
            logger.error("GetProblems error", e);
            sendError(request.getRequestId(), "Không thể lấy danh sách bài");
        }
    }

    private void handleCreateProblem(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền tạo bài tập");
            return;
        }

        try {
            JsonObject data = request.getData().getAsJsonObject();
            Problem p = new Problem();
            p.setTitle(data.get("title").getAsString());
            p.setDescription(data.get("description").getAsString());
            p.setDifficulty(data.has("difficulty") ? String.valueOf(com.grading.server.repository.ProblemRepository.parseDifficulty(data.get("difficulty").getAsString())) : "1");
            p.setTimeLimitMs(data.has("timeLimitMs") ? data.get("timeLimitMs").getAsInt() : 1000);
            p.setMemoryLimitKb(data.has("memoryLimitKb") ? data.get("memoryLimitKb").getAsInt() : 524288);
            p.setCreatedBy(authenticatedUserId);
            if (data.has("classId") && !data.get("classId").isJsonNull()) {
                p.setClassId(data.get("classId").getAsInt());
            }
            if (data.has("problemType") && !data.get("problemType").isJsonNull()) {
                p.setProblemType(data.get("problemType").getAsString());
            }
            if (data.has("oopSpec") && !data.get("oopSpec").isJsonNull()) {
                p.setOopSpec(data.get("oopSpec").getAsString());
            }

            List<com.grading.server.model.TestCase> testCases = new java.util.ArrayList<>();

            if (data.has("testCases") && data.get("testCases").isJsonArray()) {
                com.google.gson.JsonArray arr = data.getAsJsonArray("testCases");
                int order = 1;
                for (com.google.gson.JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject tcObj = el.getAsJsonObject();
                    com.grading.server.model.TestCase tc = new com.grading.server.model.TestCase();
                    tc.setInputData(tcObj.has("inputData") && !tcObj.get("inputData").isJsonNull() ? tcObj.get("inputData").getAsString() : "");
                    tc.setExpectedOutput(tcObj.has("expectedOutput") && !tcObj.get("expectedOutput").isJsonNull() ? tcObj.get("expectedOutput").getAsString() : "");
                    tc.setSample(tcObj.has("isSample") && !tcObj.get("isSample").isJsonNull() && tcObj.get("isSample").getAsBoolean());
                    tc.setOrderNum(order++);
                    testCases.add(tc);
                }
            } else {
                if (data.has("sampleInput") || data.has("sampleOutput")) {
                    com.grading.server.model.TestCase sample = new com.grading.server.model.TestCase();
                    sample.setInputData(data.has("sampleInput") ? data.get("sampleInput").getAsString() : "");
                    sample.setExpectedOutput(data.has("sampleOutput") ? data.get("sampleOutput").getAsString() : "");
                    sample.setSample(true);
                    sample.setOrderNum(1);
                    testCases.add(sample);
                }

                if (data.has("hiddenInput") || data.has("hiddenOutput")) {
                    com.grading.server.model.TestCase hidden = new com.grading.server.model.TestCase();
                    hidden.setInputData(data.has("hiddenInput") ? data.get("hiddenInput").getAsString() : "");
                    hidden.setExpectedOutput(data.has("hiddenOutput") ? data.get("hiddenOutput").getAsString() : "");
                    hidden.setSample(false);
                    hidden.setOrderNum(2);
                    testCases.add(hidden);
                }
            }

            Problem created = problemService.createProblem(p, testCases);
            if (p.getClassId() != null && p.getClassId() > 0) {
                try {
                    academicService.assignProblemsToClass(p.getClassId(), java.util.List.of(created.getId()));
                } catch (Exception ignored) {}
            }

            Message response = Message.success(
                    MessageType.CREATE_PROBLEM_RESPONSE,
                    request.getRequestId(),
                    "Tạo bài tập thành công",
                    MessageSerializer.toJsonElement(created)
            );
            send(response);

        } catch (Exception e) {
            logger.error("CreateProblem error", e);
            sendError(request.getRequestId(), "Không thể tạo bài tập: " + e.getMessage());
        }
    }

    private void handleUpdateProblem(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền cập nhật bài tập");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            if (!data.has("id") || data.get("id").getAsInt() <= 0) {
                throw new IllegalArgumentException("Thiếu mã bài tập cần cập nhật");
            }
            Problem p = new Problem();
            p.setId(data.get("id").getAsInt());
            p.setTitle(data.get("title").getAsString().trim());
            p.setDescription(data.get("description").getAsString());
            p.setDifficulty(data.has("difficulty") ? String.valueOf(com.grading.server.repository.ProblemRepository.parseDifficulty(data.get("difficulty").getAsString())) : "1");
            p.setTimeLimitMs(data.has("timeLimitMs") ? data.get("timeLimitMs").getAsInt() : 1000);
            p.setMemoryLimitKb(data.has("memoryLimitKb") ? data.get("memoryLimitKb").getAsInt() : 524288);
            if (data.has("classId") && !data.get("classId").isJsonNull()) p.setClassId(data.get("classId").getAsInt());
            if (data.has("problemType") && !data.get("problemType").isJsonNull()) {
                p.setProblemType(data.get("problemType").getAsString());
            }
            if (data.has("oopSpec") && !data.get("oopSpec").isJsonNull()) {
                p.setOopSpec(data.get("oopSpec").getAsString());
            }
            List<com.grading.server.model.TestCase> testCases = parseTestCases(data);
            Problem updated = problemService.updateProblem(p, testCases);
            if (p.getClassId() != null && p.getClassId() > 0) {
                try {
                    academicService.assignProblemsToClass(p.getClassId(), java.util.List.of(p.getId()));
                } catch (Exception ignored) {}
            }
            send(Message.success(MessageType.UPDATE_PROBLEM_RESPONSE, request.getRequestId(),
                    "Cập nhật bài tập và test case thành công", MessageSerializer.toJsonElement(updated)));
        } catch (Exception e) {
            logger.error("UpdateProblem error", e);
            sendError(request.getRequestId(), "Không thể cập nhật bài tập: " + e.getMessage());
        }
    }

    private List<com.grading.server.model.TestCase> parseTestCases(JsonObject data) {
        if (!data.has("testCases") || !data.get("testCases").isJsonArray()) {
            throw new IllegalArgumentException("Danh sách test case không hợp lệ");
        }
        List<com.grading.server.model.TestCase> testCases = new java.util.ArrayList<>();
        int order = 1;
        for (com.google.gson.JsonElement el : data.getAsJsonArray("testCases")) {
            if (!el.isJsonObject()) continue;
            com.google.gson.JsonObject tcObj = el.getAsJsonObject();
            String input = tcObj.has("inputData") && !tcObj.get("inputData").isJsonNull() ? tcObj.get("inputData").getAsString() : "";
            String output = tcObj.has("expectedOutput") && !tcObj.get("expectedOutput").isJsonNull() ? tcObj.get("expectedOutput").getAsString() : "";
            if (output.isBlank()) throw new IllegalArgumentException("Output mong đợi của test case không được để trống");
            com.grading.server.model.TestCase tc = new com.grading.server.model.TestCase();
            tc.setInputData(input);
            tc.setExpectedOutput(output);
            tc.setSample(tcObj.has("isSample") && tcObj.get("isSample").getAsBoolean());
            tc.setOrderNum(order++);
            testCases.add(tc);
        }
        if (testCases.isEmpty()) throw new IllegalArgumentException("Bài tập phải có ít nhất một test case");
        return testCases;
    }

    private void handleToggleProblemActive(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền thay đổi trạng thái bài tập");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int problemId = data.get("problemId").getAsInt();
            boolean active = data.get("active").getAsBoolean();

            problemService.setProblemActive(problemId, active);

            JsonObject res = new JsonObject();
            res.addProperty("problemId", problemId);
            res.addProperty("active", active);

            send(Message.success(
                    MessageType.TOGGLE_PROBLEM_ACTIVE_RESPONSE,
                    request.getRequestId(),
                    "Cập nhật trạng thái hiển thị bài tập thành công",
                    res
            ));
        } catch (Exception e) {
            logger.error("ToggleProblemActive error", e);
            sendError(request.getRequestId(), "Không thể cập nhật trạng thái bài tập: " + e.getMessage());
        }
    }

    private void handleChangePassword(Message request) {
        if (authenticatedUserId < 0) {
            sendError(request.getRequestId(), "Bạn chưa đăng nhập");
            return;
        }

        try {
            JsonObject data = request.getData().getAsJsonObject();
            String oldPass = data.has("oldPassword") ? data.get("oldPassword").getAsString() : "";
            String newPass = data.has("newPassword") ? data.get("newPassword").getAsString() : "";

            authService.changePassword(authenticatedUserId, oldPass, newPass);

            send(Message.success(
                    MessageType.CHANGE_PASSWORD_RESPONSE,
                    request.getRequestId(),
                    "Đổi mật khẩu thành công",
                    null
            ));
        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("ChangePassword error", e);
            sendError(request.getRequestId(), "Lỗi hệ thống khi đổi mật khẩu");
        }
    }

    // ─── Academic Handlers ───────────────────────────────────────────────────

    private void handleGetCourses(Message request) {
        try {
            var courses = academicService.getAllCourses();
            send(Message.success(
                    MessageType.GET_COURSES_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(courses)
            ));
        } catch (Exception e) {
            logger.error("GetCourses error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách môn học");
        }
    }

    private void handleCreateCourse(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền tạo môn học");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            com.grading.server.model.Course c = new com.grading.server.model.Course();
            c.setCode(data.get("code").getAsString());
            c.setName(data.get("name").getAsString());
            c.setLanguage(data.has("language") ? data.get("language").getAsString() : "cpp");
            c.setDescription(data.has("description") ? data.get("description").getAsString() : "");

            var created = academicService.createCourse(c);
            send(Message.success(
                    MessageType.CREATE_COURSE_RESPONSE,
                    request.getRequestId(),
                    "Tạo môn học thành công",
                    MessageSerializer.toJsonElement(created)
            ));
        } catch (Exception e) {
            logger.error("CreateCourse error", e);
            sendError(request.getRequestId(), "Không thể tạo môn học: " + e.getMessage());
        }
    }

    private void handleDeleteProblem(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xóa bài tập");
            return;
        }
        try {
            int id = request.getData().getAsJsonObject().get("id").getAsInt();
            problemService.deleteProblem(id);
            send(Message.success(MessageType.DELETE_PROBLEM_RESPONSE, request.getRequestId(), "Đã xóa bài tập", null));
        } catch (Exception e) {
            logger.error("DeleteProblem error", e);
            sendError(request.getRequestId(), "Không thể xóa bài tập: " + e.getMessage());
        }
    }

    private void handleUpdateCourse(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền cập nhật môn học");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            com.grading.server.model.Course course = new com.grading.server.model.Course();
            course.setId(data.get("id").getAsInt());
            course.setCode(data.get("code").getAsString());
            course.setName(data.get("name").getAsString());
            course.setLanguage(data.has("language") ? data.get("language").getAsString() : "cpp");
            course.setDescription(data.has("description") ? data.get("description").getAsString() : "");
            course.setActive(!data.has("active") || data.get("active").getAsBoolean());
            academicService.updateCourse(course);
            send(Message.success(MessageType.UPDATE_COURSE_RESPONSE, request.getRequestId(),
                    "Cập nhật môn học thành công", null));
        } catch (Exception e) {
            logger.error("UpdateCourse error", e);
            sendError(request.getRequestId(), "Không thể cập nhật môn học: " + e.getMessage());
        }
    }

    private void handleToggleCourseActive(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền thay đổi trạng thái môn học");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int courseId = data.get("courseId").getAsInt();
            boolean active = data.get("active").getAsBoolean();

            academicService.setCourseActive(courseId, active);

            JsonObject res = new JsonObject();
            res.addProperty("courseId", courseId);
            res.addProperty("active", active);

            send(Message.success(
                    MessageType.TOGGLE_COURSE_ACTIVE_RESPONSE,
                    request.getRequestId(),
                    "Cập nhật quyền hiển thị môn học thành công",
                    res
            ));
        } catch (Exception e) {
            logger.error("ToggleCourseActive error", e);
            sendError(request.getRequestId(), "Không thể cập nhật trạng thái môn học: " + e.getMessage());
        }
    }

    private void handleDeleteCourse(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xóa môn học");
            return;
        }
        try {
            int id = request.getData().getAsJsonObject().get("id").getAsInt();
            academicService.deleteCourse(id);
            send(Message.success(MessageType.DELETE_COURSE_RESPONSE, request.getRequestId(),
                    "Đã xóa môn học; các lớp thuộc môn đã được gỡ liên kết", null));
        } catch (Exception e) {
            logger.error("DeleteCourse error", e);
            sendError(request.getRequestId(), "Không thể xóa môn học: " + e.getMessage());
        }
    }

    private void handleGetClasses(Message request) {
        try {
            var classes = academicService.getAllClasses();
            send(Message.success(
                    MessageType.GET_CLASSES_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(classes)
            ));
        } catch (Exception e) {
            logger.error("GetClasses error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách lớp tín chỉ");
        }
    }

    private void handleGetMyClasses(Message request) {
        try {
            var classes = academicService.getClassesByStudent(authenticatedUserId);
            send(Message.success(
                    MessageType.GET_MY_CLASSES_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(classes)
            ));
        } catch (Exception e) {
            logger.error("GetMyClasses error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách lớp học của bạn");
        }
    }

    private void handleCreateClass(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền tạo lớp tín chỉ");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            com.grading.server.model.CreditClass cc = new com.grading.server.model.CreditClass();
            cc.setClassCode(data.get("classCode").getAsString());
            cc.setName(data.get("name").getAsString());
            cc.setCourseId(data.get("courseId").getAsInt());
            cc.setTeacherId(authenticatedUserId);
            if (data.has("allowedLanguages") && !data.get("allowedLanguages").isJsonNull()) {
                cc.setAllowedLanguages(data.get("allowedLanguages").getAsString());
            }

            var created = academicService.createClass(cc);
            send(Message.success(
                    MessageType.CREATE_CLASS_RESPONSE,
                    request.getRequestId(),
                    "Tạo lớp tín chỉ thành công",
                    MessageSerializer.toJsonElement(created)
            ));
        } catch (Exception e) {
            logger.error("CreateClass error", e);
            sendError(request.getRequestId(), "Không thể tạo lớp tín chỉ: " + e.getMessage());
        }
    }

    private void handleUpdateClass(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền cập nhật lớp tín chỉ");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int id = data.get("id").getAsInt();
            String classCode = data.get("classCode").getAsString();
            String name = data.get("name").getAsString();
            int courseId = 0;
            if (data.has("courseId") && !data.get("courseId").isJsonNull()) {
                courseId = data.get("courseId").getAsInt();
            } else {
                var current = academicService.getClassById(id);
                if (current != null) {
                    courseId = current.getCourseId();
                }
            }
            String allowedLanguages = data.has("allowedLanguages") && !data.get("allowedLanguages").isJsonNull()
                    ? data.get("allowedLanguages").getAsString()
                    : "c,cpp,java,python";

            academicService.updateClass(id, classCode, name, courseId, allowedLanguages);
            send(Message.success(
                    MessageType.UPDATE_CLASS_RESPONSE,
                    request.getRequestId(),
                    "Cập nhật lớp tín chỉ thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("UpdateClass error", e);
            sendError(request.getRequestId(), "Không thể cập nhật lớp tín chỉ: " + e.getMessage());
        }
    }

    private void handleGetClassProblems(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int classId = data.get("classId").getAsInt();
            var problems = academicService.getProblemsByClassId(classId);
            send(Message.success(
                    MessageType.GET_CLASS_PROBLEMS_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(problems)
            ));
        } catch (Exception e) {
            logger.error("GetClassProblems error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách bài tập của lớp: " + e.getMessage());
        }
    }

    private void handleAssignClassProblems(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền gán bài tập vào lớp");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int classId = data.get("classId").getAsInt();
            List<Integer> problemIds = new ArrayList<>();
            if (data.has("problemIds") && data.get("problemIds").isJsonArray()) {
                for (var el : data.getAsJsonArray("problemIds")) {
                    problemIds.add(el.getAsInt());
                }
            }
            academicService.assignProblemsToClass(classId, problemIds);
            send(Message.success(
                    MessageType.ASSIGN_CLASS_PROBLEMS_RESPONSE,
                    request.getRequestId(),
                    "Đã gán bài tập vào lớp thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("AssignClassProblems error", e);
            sendError(request.getRequestId(), "Không thể gán bài tập vào lớp: " + e.getMessage());
        }
    }

    private void handleRemoveClassProblem(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền gỡ bài tập khỏi lớp");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int classId = data.get("classId").getAsInt();
            int problemId = data.get("problemId").getAsInt();
            academicService.removeProblemFromClass(classId, problemId);
            send(Message.success(
                    MessageType.REMOVE_CLASS_PROBLEM_RESPONSE,
                    request.getRequestId(),
                    "Đã gỡ bài tập khỏi lớp thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("RemoveClassProblem error", e);
            sendError(request.getRequestId(), "Không thể gỡ bài tập khỏi lớp: " + e.getMessage());
        }
    }

    private void handleDeleteClass(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xóa lớp tín chỉ");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int id = data.get("id").getAsInt();

            academicService.deleteClass(id);
            send(Message.success(
                    MessageType.DELETE_CLASS_RESPONSE,
                    request.getRequestId(),
                    "Đã xóa lớp tín chỉ thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("DeleteClass error", e);
            sendError(request.getRequestId(), "Không thể xóa lớp tín chỉ: " + e.getMessage());
        }
    }

    private void handleGetStudents(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xem danh sách sinh viên");
            return;
        }
        try {
            int classId = 0;
            if (request.getData() != null && request.getData().isJsonObject()) {
                JsonObject data = request.getData().getAsJsonObject();
                if (data.has("classId")) {
                    classId = data.get("classId").getAsInt();
                }
            }
            var students = academicService.getStudentsByClass(classId);
            send(Message.success(
                    MessageType.GET_STUDENTS_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(students)
            ));
        } catch (Exception e) {
            logger.error("GetStudents error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách sinh viên");
        }
    }

    private void handleImportStudents(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền import sinh viên");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int classId = data.get("classId").getAsInt();
            var studentsArray = data.getAsJsonArray("students");
            boolean classExists = academicService.getAllClasses().stream().anyMatch(c -> c.getId() == classId);
            if (!classExists) throw new IllegalArgumentException("Lớp tín chỉ được chọn không tồn tại");

            int successCount = 0;
            int errorCount = 0;
            com.google.gson.JsonArray errors = new com.google.gson.JsonArray();

            int row = 0;
            for (var el : studentsArray) {
                row++;
                try {
                    JsonObject stuObj = el.getAsJsonObject();
                    String username = stuObj.has("username") ? stuObj.get("username").getAsString().trim() : "";
                    String fullName = stuObj.has("fullName") ? stuObj.get("fullName").getAsString().trim() : username;
                    if (fullName.isBlank()) throw new IllegalArgumentException("Họ tên không được để trống");

                    authService.createOrUpdateStudent(username, fullName, classId);
                    successCount++;
                } catch (Exception ex) {
                    errorCount++;
                    logger.warn("Failed to import student line: {}", ex.getMessage());
                    JsonObject error = new JsonObject();
                    error.addProperty("row", row);
                    error.addProperty("message", ex.getMessage());
                    errors.add(error);
                }
            }

            JsonObject resultData = new JsonObject();
            resultData.addProperty("successCount", successCount);
            resultData.addProperty("errorCount", errorCount);
            resultData.add("errors", errors);

            send(Message.success(
                    MessageType.IMPORT_STUDENTS_RESPONSE,
                    request.getRequestId(),
                    "Đã nạp thành công " + successCount + " sinh viên (thất bại: " + errorCount + ")",
                    resultData
            ));

        } catch (Exception e) {
            logger.error("ImportStudents error", e);
            sendError(request.getRequestId(), "Không thể import sinh viên: " + e.getMessage());
        }
    }

    private void handleUpdateStudent(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền sửa thông tin sinh viên");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int studentId = data.get("studentId").getAsInt();
            String fullName = data.get("fullName").getAsString();
            Integer classId = (data.has("classId") && !data.get("classId").isJsonNull())
                    ? data.get("classId").getAsInt() : null;
            String newPassword = (data.has("newPassword") && !data.get("newPassword").isJsonNull())
                    ? data.get("newPassword").getAsString() : null;

            academicService.updateStudent(studentId, fullName, classId, newPassword);

            send(Message.success(
                    MessageType.UPDATE_STUDENT_RESPONSE,
                    request.getRequestId(),
                    "Cập nhật thông tin sinh viên thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("UpdateStudent error", e);
            sendError(request.getRequestId(), "Không thể cập nhật sinh viên: " + e.getMessage());
        }
    }

    private void handleDeleteStudent(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xóa sinh viên");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int studentId = data.get("studentId").getAsInt();
            Integer classId = data.has("classId") && !data.get("classId").isJsonNull()
                    ? data.get("classId").getAsInt() : null;
            if (classId != null && classId > 0) {
                academicService.removeStudentFromClass(studentId, classId);
            } else {
                academicService.deleteStudent(studentId);
            }

            send(Message.success(
                    MessageType.DELETE_STUDENT_RESPONSE,
                    request.getRequestId(),
                    classId != null && classId > 0
                            ? "Đã gỡ sinh viên khỏi lớp tín chỉ"
                            : "Đã xóa tài khoản sinh viên thành công",
                    null
            ));
        } catch (Exception e) {
            logger.error("DeleteStudent error", e);
            sendError(request.getRequestId(), "Không thể xóa sinh viên: " + e.getMessage());
        }
    }

    private void handleGetProblemDetail(Message request) {
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int problemId = data.get("problemId").getAsInt();

            if (!"TEACHER".equalsIgnoreCase(authenticatedRole)
                    && !academicService.isProblemAllowedForStudent(authenticatedUserId, problemId)) {
                throw new IllegalArgumentException("Trong thời gian kiểm tra, bạn chỉ được mở các bài do giáo viên giao.");
            }

            boolean includeHiddenTests = "TEACHER".equalsIgnoreCase(authenticatedRole);
            Problem problem = problemService.getProblemDetail(problemId, includeHiddenTests);

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
            if (!"TEACHER".equalsIgnoreCase(authenticatedRole)
                    && !academicService.isProblemAllowedForStudent(authenticatedUserId, problemId)) {
                throw new IllegalArgumentException("Trong thời gian kiểm tra, bạn chỉ được nộp các bài do giáo viên giao.");
            }
            String sourceCode = data.get("sourceCode").getAsString();
            String language = data.get("language").getAsString();

            // 1. Kiểm tra bài Java OOP bắt buộc phải nộp ngôn ngữ Java
            Problem problem = problemService.getProblemDetail(problemId);
            if (problem != null && problem.isJavaOop()) {
                if (!"java".equalsIgnoreCase(language)) {
                    throw new IllegalArgumentException("Bài tập Java OOP bắt buộc phải nộp bằng ngôn ngữ Java");
                }
            }

            // 2. Chỉ gắn bài nộp với lượt thi đã được sinh viên bắt đầu rõ ràng.
            Integer assessmentId = null;
            Integer classId = data.has("classId") && !data.get("classId").isJsonNull()
                    ? data.get("classId").getAsInt() : null;

            if ("STUDENT".equalsIgnoreCase(authenticatedRole)) {
                Integer requestedAssessmentId = data.has("assessmentId") && !data.get("assessmentId").isJsonNull()
                        ? data.get("assessmentId").getAsInt() : null;
                JsonObject activeAssessment = academicService.getActiveAssessment(authenticatedUserId, null);
                if (requestedAssessmentId != null && (activeAssessment == null
                        || activeAssessment.get("id").getAsInt() != requestedAssessmentId)) {
                    throw new IllegalArgumentException("Bài kiểm tra đã kết thúc hoặc chưa được bắt đầu.");
                }
                if (activeAssessment != null && activeAssessment.has("problemIds")) {
                    for (var pid : activeAssessment.getAsJsonArray("problemIds")) {
                        if (pid.getAsInt() == problemId) {
                            assessmentId = activeAssessment.get("id").getAsInt();
                            classId = activeAssessment.get("classId").getAsInt();
                            break;
                        }
                    }
                }
                if (requestedAssessmentId != null && assessmentId == null) {
                    throw new IllegalArgumentException("Bài tập này không thuộc bài kiểm tra đang làm.");
                }

                if (assessmentId == null && (classId == null || classId <= 0)) {
                    try {
                        List<CreditClass> studentClasses = academicService.getClassesByStudent(authenticatedUserId);
                        if (studentClasses != null) {
                            for (CreditClass sc : studentClasses) {
                                List<Problem> classProbs = academicService.getProblemsByClassId(sc.getId());
                                if (classProbs != null && classProbs.stream().anyMatch(p -> p.getId() == problemId)) {
                                    classId = sc.getId();
                                    break;
                                }
                            }
                            if ((classId == null || classId <= 0) && studentClasses.size() == 1) {
                                classId = studentClasses.get(0).getId();
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Could not determine student class for language check: {}", ex.getMessage());
                    }
                }
            }

            if (classId != null && classId > 0) {
                CreditClass creditClass = academicService.getClassById(classId);
                if (creditClass != null && !creditClass.isLanguageAllowed(language)) {
                    throw new IllegalArgumentException("Lớp " + creditClass.getName() + " chỉ cho phép nộp bằng ngôn ngữ: " + creditClass.getEffectiveAllowedLanguages().toUpperCase());
                }
            }

            boolean isZip = data.has("isZip") && data.get("isZip").getAsBoolean();

            Submission submission = submissionService.submit(
                    authenticatedUserId, problemId, assessmentId, sourceCode, language, isZip
            );

            // Trả về PENDING ngay lập tức — kết quả sẽ được push sau khi chấm xong
            JsonObject responseData = new JsonObject();
            responseData.addProperty("submissionId", submission.getId());
            responseData.addProperty("verdict", submission.getVerdict()); // "PENDING"
            responseData.addProperty("submittedAt", submission.getSubmittedAt());
            responseData.addProperty("queueSize", submissionService.getHistory(authenticatedUserId).size());
            if (assessmentId != null) responseData.addProperty("assessmentId", assessmentId);

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
            int assessmentId = -1;
            if (request.getData() != null && !request.getData().isJsonNull()) {
                JsonObject data = request.getData().getAsJsonObject();
                if (data.has("problemId")) {
                    problemId = data.get("problemId").getAsInt();
                }
                if (data.has("assessmentId")) {
                    assessmentId = data.get("assessmentId").getAsInt();
                }
            }

            List<Submission> history;
            if (problemId > 0 && assessmentId > 0) {
                history = submissionService.getHistoryForAssessmentProblem(
                        authenticatedUserId, problemId, assessmentId);
            } else if (problemId > 0) {
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

    // ─── Proctoring / Anti-Cheat Handlers ─────────────────────────────────────

    private void handleCreateAssessment(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền tạo bài kiểm tra.");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            List<Integer> problemIds = new java.util.ArrayList<>();
            if (data.has("problemIds") && data.get("problemIds").isJsonArray()) {
                for (var id : data.getAsJsonArray("problemIds")) problemIds.add(id.getAsInt());
            }
            int id = academicService.createAssessment(
                    data.get("classId").getAsInt(),
                    data.has("title") ? data.get("title").getAsString() : "",
                    java.time.LocalDateTime.parse(data.get("opensAt").getAsString()),
                    java.time.LocalDateTime.parse(data.get("closesAt").getAsString()),
                    data.get("durationMinutes").getAsInt(), authenticatedUserId, problemIds);
            JsonObject result = new JsonObject();
            result.addProperty("assessmentId", id);
            send(Message.success(MessageType.CREATE_ASSESSMENT_RESPONSE, request.getRequestId(),
                    "Đã lưu và công bố cấu hình bài kiểm tra.", result));
        } catch (Exception e) {
            logger.error("CreateAssessment error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể tạo bài kiểm tra.");
        }
    }

    private void handleUpdateAssessment(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền cập nhật bài kiểm tra.");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            List<Integer> problemIds = null;
            if (data.has("problemIds") && data.get("problemIds").isJsonArray()) {
                problemIds = new java.util.ArrayList<>();
                for (var id : data.getAsJsonArray("problemIds")) problemIds.add(id.getAsInt());
            }
            int assessmentId = data.get("assessmentId").getAsInt();
            academicService.updateAssessment(
                    assessmentId,
                    data.has("title") ? data.get("title").getAsString() : "",
                    java.time.LocalDateTime.parse(data.get("opensAt").getAsString()),
                    java.time.LocalDateTime.parse(data.get("closesAt").getAsString()),
                    data.get("durationMinutes").getAsInt(),
                    problemIds);
            send(Message.success(MessageType.UPDATE_ASSESSMENT_RESPONSE, request.getRequestId(),
                    "Đã cập nhật bài kiểm tra thành công.", null));
        } catch (Exception e) {
            logger.error("UpdateAssessment error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể cập nhật bài kiểm tra.");
        }
    }

    private void handleDeleteAssessment(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xóa bài kiểm tra.");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int assessmentId = data.get("assessmentId").getAsInt();
            academicService.deleteAssessment(assessmentId);
            send(Message.success(MessageType.DELETE_ASSESSMENT_RESPONSE, request.getRequestId(),
                    "Đã xóa bài kiểm tra thành công.", null));
        } catch (Exception e) {
            logger.error("DeleteAssessment error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể xóa bài kiểm tra.");
        }
    }

    private void handleGetActiveAssessment(Message request) {
        if (!"STUDENT".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chức năng này chỉ dành cho sinh viên.");
            return;
        }
        try {
            Integer classId = null;
            if (request.getData() != null && request.getData().isJsonObject()) {
                JsonObject data = request.getData().getAsJsonObject();
                if (data.has("classId") && !data.get("classId").isJsonNull()) classId = data.get("classId").getAsInt();
            }
            JsonObject active = academicService.getActiveAssessment(authenticatedUserId, classId);
            send(Message.success(MessageType.GET_ACTIVE_ASSESSMENT_RESPONSE, request.getRequestId(), "OK", active));
        } catch (Exception e) {
            logger.error("GetActiveAssessment error", e);
            sendError(request.getRequestId(), "Không thể kiểm tra lịch bài kiểm tra.");
        }
    }

    private void handleGetStudentAssessments(Message request) {
        if (!"STUDENT".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chức năng này chỉ dành cho sinh viên.");
            return;
        }
        try {
            JsonObject data = request.getData() != null && request.getData().isJsonObject()
                    ? request.getData().getAsJsonObject() : null;
            boolean summariesOnly = data != null && data.has("summariesOnly") && data.get("summariesOnly").getAsBoolean();
            send(Message.success(MessageType.GET_STUDENT_ASSESSMENTS_RESPONSE, request.getRequestId(), "OK",
                    summariesOnly ? academicService.getStudentAssessmentSummaries(authenticatedUserId)
                            : academicService.getStudentAssessments(authenticatedUserId)));
        } catch (Exception e) {
            logger.error("GetStudentAssessments error", e);
            sendError(request.getRequestId(), "Không thể tải danh sách bài kiểm tra.");
        }
    }

    private void handleGetClassAssessments(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xem danh sách bài kiểm tra.");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int classId = data.get("classId").getAsInt();
            send(Message.success(MessageType.GET_CLASS_ASSESSMENTS_RESPONSE, request.getRequestId(), "OK",
                    academicService.getClassAssessments(classId, authenticatedUserId)));
        } catch (Exception e) {
            logger.error("GetClassAssessments error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể tải bài kiểm tra.");
        }
    }

    private void handleGetAssessmentMonitor(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền theo dõi bài kiểm tra.");
            return;
        }
        try {
            JsonObject data = request.getData().getAsJsonObject();
            int assessmentId = data.get("assessmentId").getAsInt();
            JsonObject monitor = academicService.getAssessmentMonitor(assessmentId, authenticatedUserId);
            JsonArray assessmentAlerts = new JsonArray();
            for (var log : proctorService.getLogsForAssessment(assessmentId)) {
                assessmentAlerts.add(MessageSerializer.toJsonElement(log));
            }
            monitor.add("alerts", assessmentAlerts);
            send(Message.success(MessageType.GET_ASSESSMENT_MONITOR_RESPONSE, request.getRequestId(), "OK", monitor));
        } catch (Exception e) {
            logger.error("GetAssessmentMonitor error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể tải tiến độ bài kiểm tra.");
        }
    }

    private void handleStartAssessment(Message request) {
        if (!"STUDENT".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chức năng này chỉ dành cho sinh viên.");
            return;
        }
        try {
            int assessmentId = request.getData().getAsJsonObject().get("assessmentId").getAsInt();
            academicService.startAssessment(assessmentId, authenticatedUserId);
            send(Message.success(MessageType.START_ASSESSMENT_RESPONSE, request.getRequestId(),
                    "Đã bắt đầu bài kiểm tra.", academicService.getActiveAssessment(authenticatedUserId, null)));
        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("StartAssessment error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể bắt đầu bài kiểm tra.");
        }
    }

    private void handleCompleteAssessment(Message request) {
        if (!"STUDENT".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chức năng này chỉ dành cho sinh viên.");
            return;
        }
        try {
            int assessmentId = request.getData().getAsJsonObject().get("assessmentId").getAsInt();
            academicService.completeAssessment(assessmentId, authenticatedUserId);
            send(Message.success(MessageType.COMPLETE_ASSESSMENT_RESPONSE, request.getRequestId(),
                    "Đã hoàn thành và nộp bài kiểm tra."));
        } catch (IllegalArgumentException e) {
            sendError(request.getRequestId(), e.getMessage());
        } catch (Exception e) {
            logger.error("CompleteAssessment error", e);
            sendError(request.getRequestId(), e.getMessage() != null ? e.getMessage() : "Không thể hoàn thành bài kiểm tra.");
        }
    }

    private void handleProctorEvent(Message request) {
        try {
            JsonObject data = (request.getData() != null && request.getData().isJsonObject())
                    ? request.getData().getAsJsonObject()
                    : new JsonObject();

            int problemId = data.has("problemId") ? data.get("problemId").getAsInt() : 0;
            String problemTitle = data.has("problemTitle") && !data.get("problemTitle").isJsonNull()
                    ? data.get("problemTitle").getAsString()
                    : "Bài tập #" + problemId;
            String eventType = data.has("eventType") && !data.get("eventType").isJsonNull()
                    ? data.get("eventType").getAsString()
                    : "UNKNOWN";

            if ("FOCUS_LOSS".equalsIgnoreCase(eventType)) {
                // Đã loại bỏ giám sát mất focus: trả lời OK nhưng không ghi nhận vi phạm
                JsonObject resData = new JsonObject();
                resData.addProperty("violationCount", 0);
                resData.addProperty("severity", "INFO");
                resData.addProperty("warningMessage", "OK");
                send(Message.success(MessageType.PROCTOR_EVENT_RESPONSE, request.getRequestId(), "OK", resData));
                return;
            }

            String username = authService.getUsernameFromToken(request.getToken());
            if (username == null || username.isBlank()) {
                username = "User#" + authenticatedUserId;
            }
            String fullName = authService.getFullNameFromToken(request.getToken());
            if (fullName == null || fullName.isBlank()) {
                fullName = username;
            }
            String detail = (data.has("detail") && !data.get("detail").isJsonNull())
                    ? data.get("detail").getAsString()
                    : null;
            // Không tin assessmentId do client gửi: tự đối chiếu bài đang thi trên server
            // để cảnh báo không thể bị gắn nhầm/giả mạo sang một ca thi khác.
            Integer assessmentId = "STUDENT".equalsIgnoreCase(authenticatedRole)
                    ? academicService.getActiveAssessmentIdForProblem(authenticatedUserId, problemId)
                    : null;
            String clientIp = socket.getInetAddress().getHostAddress();

            var result = proctorService.recordEvent(
                    authenticatedUserId, username, fullName,
                    problemId, assessmentId, problemTitle, eventType, detail, clientIp
            );

            // 1. Phản hồi cảnh báo cho sinh viên
            JsonObject resData = new JsonObject();
            resData.addProperty("violationCount", result.violationCount());
            resData.addProperty("severity", result.severity());
            resData.addProperty("warningMessage", result.warningMessage());

            send(Message.success(
                    MessageType.PROCTOR_EVENT_RESPONSE,
                    request.getRequestId(),
                    result.warningMessage(),
                    resData
            ));

            // 2. Real-time push alert đến toàn bộ các tài khoản Giám thị / Giáo viên đang online
            JsonObject alertData = new JsonObject();
            alertData.addProperty("userId", authenticatedUserId);
            alertData.addProperty("username", username);
            alertData.addProperty("fullName", fullName);
            alertData.addProperty("problemId", problemId);
            alertData.addProperty("problemTitle", problemTitle);
            if (assessmentId != null) alertData.addProperty("assessmentId", assessmentId);
            alertData.addProperty("eventType", eventType);
            alertData.addProperty("detail", detail != null ? detail : "");
            alertData.addProperty("violationCount", result.violationCount());
            alertData.addProperty("severity", result.severity());
            alertData.addProperty("clientIp", clientIp);
            alertData.addProperty("timestamp", result.log().getTimestamp());
            alertData.addProperty("formattedTime", result.log().getFormattedTime());

            String teacherMsg;
            if ("AI_TOOL_DETECTED".equalsIgnoreCase(eventType)) {
                String toolName = (detail != null && !detail.isBlank()) ? detail : "Công cụ AI";
                teacherMsg = String.format("🚨 GIAN LẬN AI: Sinh viên %s (%s) đang mở [%s] (Bài: %s)",
                        fullName, username, toolName, problemTitle);
            } else if ("AI_CODE_PASTED".equalsIgnoreCase(eventType)) {
                String sign = (detail != null && !detail.isBlank()) ? detail : "Cú pháp AI";
                teacherMsg = String.format("🚨 GIAN LẬN AI: Sinh viên %s (%s) dán mã nguồn có dấu hiệu AI [%s] (Bài: %s)",
                        fullName, username, sign, problemTitle);
            } else {
                teacherMsg = String.format("⚠️ Sinh viên %s (%s) vi phạm giám sát lần %d (Bài: %s)",
                        fullName, username, result.violationCount(), problemTitle);
            }

            Message teacherAlert = new Message(MessageType.PROCTOR_ALERT, null);
            teacherAlert.setStatus(result.severity());
            teacherAlert.setMessage(teacherMsg);
            teacherAlert.setData(alertData);

            connectionRegistry.broadcastToTeachers(teacherAlert);

        } catch (Exception e) {
            logger.error("ProctorEvent error", e);
            sendError(request.getRequestId(), "Lỗi ghi nhận sự kiện giám sát: " + e.getMessage());
        }
    }

    private void handleGetClassStudentStats(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xem thống kê lớp");
            return;
        }
        try {
            int classId = request.getData().getAsJsonObject().get("classId").getAsInt();
            String sql = "SELECT u.id AS student_id, COUNT(DISTINCT s.problem_id) AS solved_count " +
                    "FROM class_enrollments ce JOIN users u ON u.id = ce.student_id " +
                    "LEFT JOIN submissions s ON s.user_id = u.id AND s.verdict = 'AC' " +
                    "WHERE u.role = 'STUDENT' AND ce.class_id = ? GROUP BY u.id";
            JsonArray rows = new JsonArray();
            try (Connection conn = com.grading.server.config.DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, classId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject row = new JsonObject();
                        row.addProperty("studentId", rs.getInt("student_id"));
                        row.addProperty("solvedCount", rs.getInt("solved_count"));
                        rows.add(row);
                    }
                }
            }
            send(Message.success(MessageType.GET_CLASS_STUDENT_STATS_RESPONSE, request.getRequestId(), "OK", rows));
        } catch (Exception e) {
            logger.error("GetClassStudentStats error", e);
            sendError(request.getRequestId(), "Không thể tải thống kê sinh viên");
        }
    }

    private void handleGetProctorLogs(Message request) {
        if (!"TEACHER".equalsIgnoreCase(authenticatedRole)) {
            sendError(request.getRequestId(), "Chỉ giáo viên mới có quyền xem nhật ký giám sát");
            return;
        }
        try {
            var logs = proctorService.getAllLogs();
            send(Message.success(
                    MessageType.GET_PROCTOR_LOGS_RESPONSE,
                    request.getRequestId(),
                    "OK",
                    MessageSerializer.toJsonElement(logs)
            ));
        } catch (Exception e) {
            logger.error("GetProctorLogs error", e);
            sendError(request.getRequestId(), "Không thể tải nhật ký giám sát");
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
                connectionRegistry.register(userId, writer, socket, socket.getInetAddress().getHostAddress(), authenticatedRole);
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
            if ("TEACHER".equalsIgnoreCase(authenticatedRole) && "SUCCESS".equalsIgnoreCase(message.getStatus())) {
                String scope = studentDataChangeScope(message.getType());
                if (scope != null) connectionRegistry.broadcastStudentDataChanged(scope, message.getType().name());
            }
        } catch (Exception e) {
            logger.error("Error sending response", e);
        }
    }

    static String studentDataChangeScope(MessageType type) {
        if (type == null) return null;
        return switch (type) {
            case CREATE_PROBLEM_RESPONSE, UPDATE_PROBLEM_RESPONSE, DELETE_PROBLEM_RESPONSE,
                    TOGGLE_PROBLEM_ACTIVE_RESPONSE, ASSIGN_CLASS_PROBLEMS_RESPONSE, REMOVE_CLASS_PROBLEM_RESPONSE -> "PROBLEMS";
            case CREATE_COURSE_RESPONSE, UPDATE_COURSE_RESPONSE, DELETE_COURSE_RESPONSE,
                    TOGGLE_COURSE_ACTIVE_RESPONSE -> "COURSES";
            case CREATE_CLASS_RESPONSE, UPDATE_CLASS_RESPONSE, DELETE_CLASS_RESPONSE -> "CLASSES";
            case IMPORT_STUDENTS_RESPONSE, UPDATE_STUDENT_RESPONSE, DELETE_STUDENT_RESPONSE -> "ENROLLMENTS";
            case CREATE_ASSESSMENT_RESPONSE -> "ASSESSMENTS";
            default -> null;
        };
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
        // Unregister an toàn từ ConnectionRegistry (chỉ unregister nếu socket trùng khớp)
        if (authenticatedUserId > 0) {
            connectionRegistry.unregister(authenticatedUserId, socket);
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
