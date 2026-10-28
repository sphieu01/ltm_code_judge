package com.grading.client.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Problem;
import com.grading.client.model.AssessmentSchedule;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.proctor.AiToolDetector;
import com.grading.client.protocol.Response;
import com.grading.client.view.ProblemView;
import javafx.application.Platform;

/**
 * ProblemController — Điều khiển logic nộp bài giữa ProblemView và Socket Server.
 *
 * Chức năng:
 * - Gắn action Nộp bài (Submit) từ ProblemView
 * - Gửi request nộp code (SUBMIT) qua SocketClient lên Server
 * - Nhận SUBMIT_RESPONSE (PENDING) và lắng nghe push notification JUDGE_NOTIFICATION từ server
 * - Cập nhật kết quả chấm bài trực tiếp lên khu vực kết quả của ProblemView
 */
public class ProblemController {

    private static final Gson GSON = new Gson();

    private final ProblemView view;
    private final SessionManager sessionManager;
    private volatile boolean disposed;
    private boolean finishingAssessment;
    private boolean assessmentRefreshInFlight;
    private Integer submittingAssessmentId;
    private Integer pendingSubmissionId;
    private boolean awaitingSubmitResponse;
    private final java.util.Map<Integer, Response> earlyJudgeNotifications = new java.util.HashMap<>();
    private final java.util.List<JsonObject> scheduledAssessments = new java.util.ArrayList<>();
    private final java.util.function.Consumer<Response> dataChangedListener = this::handleDataChanged;
    private final javafx.animation.PauseTransition dataRefreshDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
    private boolean reloadClassMetadata;
    private long problemMetadataVersion;

    public ProblemController(ProblemView view) {
        this.view = view;
        this.sessionManager = SessionManager.getInstance();
        initHandlers();
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null) socketClient.addDataChangedListener(dataChangedListener);
        dataRefreshDebounce.setOnFinished(event -> refreshLiveProblem());
        registerPushNotifications();
        initProctoring();
        initCountdownTimer();
        loadSubmissionHistory();
        startAssessmentGuard();
    }

    private void initHandlers() {
        view.setOnSubmitCode(this::handleSubmitCode);
        view.setOnLanguageChange(this::handleLanguageChange);
        view.setAssessmentMode(sessionManager.isAssessmentMode());
        view.setOnCompleteAssessment(this::completeAssessment);
    }

    private void handleDataChanged(Response response) {
        Platform.runLater(() -> {
            if (disposed) return;
            JsonObject data = response.getData() != null && response.getData().isJsonObject()
                    ? response.getData().getAsJsonObject() : new JsonObject();
            String scope = jsonString(data, "scope");
            reloadClassMetadata |= java.util.List.of("CLASSES", "COURSES", "ENROLLMENTS").contains(scope);
            dataRefreshDebounce.playFromStart();
        });
    }

    private void refreshLiveProblem() {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (disposed || socketClient == null || !socketClient.isConnected()) return;
        refreshAssessmentGuard();
        long version = ++problemMetadataVersion;
        if (reloadClassMetadata) {
            reloadClassMetadata = false;
            socketClient.getMyClasses(response -> Platform.runLater(() -> {
                if (disposed || version != problemMetadataVersion || !"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
                com.grading.client.model.CreditClass currentClass = sessionManager.getCurrentClass();
                com.grading.client.model.CreditClass updatedClass = null;
                if (response.getData() != null && response.getData().isJsonArray() && currentClass != null) {
                    for (JsonElement element : response.getData().getAsJsonArray()) {
                        com.grading.client.model.CreditClass candidate = GSON.fromJson(element, com.grading.client.model.CreditClass.class);
                        if (candidate.getId() == currentClass.getId()) { updatedClass = candidate; break; }
                    }
                    if (updatedClass == null) {
                        returnToDashboard();
                        return;
                    }
                    sessionManager.setCurrentClass(updatedClass);
                }
                fetchUpdatedProblem(version);
            }));
        } else {
            fetchUpdatedProblem(version);
        }
    }

    private void fetchUpdatedProblem(long version) {
        SocketClient socketClient = sessionManager.getSocketClient();
        Problem current = view.getCurrentProblem();
        if (disposed || current == null || socketClient == null || !socketClient.isConnected()) return;
        socketClient.getProblemDetail(current.getId(), response -> Platform.runLater(() -> {
            if (disposed || version != problemMetadataVersion) return;
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
                // Transport failures leave the uploaded source in place for a retry.
                String message = response.getMessage() == null ? "" : response.getMessage();
                if (message.contains("không tồn tại") || message.contains("chỉ được mở")) returnToDashboard();
                return;
            }
            if (response.getData() == null || !response.getData().isJsonObject()) return;
            JsonObject data = response.getData().getAsJsonObject();
            Problem updated = GSON.fromJson(data, Problem.class);
            if (!updated.isActive()) { returnToDashboard(); return; }
            com.grading.client.model.CreditClass currentClass = sessionManager.getCurrentClass();
            if (currentClass != null) {
                updated.setClassId(currentClass.getId());
                updated.setClassName(currentClass.getName());
                updated.setAllowedLanguages(currentClass.getEffectiveAllowedLanguages());
            } else {
                updated.setClassId(current.getClassId());
                updated.setClassName(current.getClassName());
                updated.setAllowedLanguages(current.getAllowedLanguages());
            }
            view.refreshProblemMetadata(updated);
            String description = updated.getDescription() == null ? "" : updated.getDescription();
            String inputFormat = "Đầu vào chuẩn theo quy định bài toán.";
            String outputFormat = "Đầu ra chuẩn theo quy định bài toán.";
            int inputIndex = description.indexOf("**Input:**");
            int outputIndex = description.indexOf("**Output:**");
            if (inputIndex >= 0 && outputIndex > inputIndex) {
                inputFormat = description.substring(inputIndex + 10, outputIndex).trim();
                outputFormat = description.substring(outputIndex + 11).trim();
                description = description.substring(0, inputIndex).trim();
            }
            view.setProblemDetails(description, inputFormat, outputFormat,
                    String.format("• Giới hạn thời gian: %s\n• Giới hạn bộ nhớ: %s",
                            updated.getTimeLimitFormatted(), updated.getMemoryLimitFormatted()), null, null);
            if (data.has("testCases") && data.get("testCases").isJsonArray()) {
                int index = 1;
                for (JsonElement element : data.getAsJsonArray("testCases")) {
                    JsonObject testCase = element.getAsJsonObject();
                    view.addSampleTestCase("Ví dụ " + index++, jsonString(testCase, "inputData"), jsonString(testCase, "expectedOutput"));
                }
            }
        }));
    }

    /**
     * Đăng ký nhận push notification (JUDGE_NOTIFICATION) từ Server.
     */
    private void registerPushNotifications() {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null) {
            socketClient.setNotificationCallback(this::handleJudgeNotification);
        }
    }

    // =========================================================================
    // XỬ LÝ NỘP BÀI (SUBMIT CODE TRỰC TIẾP LÊN SERVER)
    // =========================================================================

    private void handleSubmitCode(ProblemView v) {
        if (disposed || finishingAssessment) return;
        if (sessionManager.isAssessmentMode() && sessionManager.getRemainingAssessmentSeconds() <= 0) {
            v.showExecutionResult("Bài kiểm tra đã hết giờ. Không thể nộp thêm bài.", "ASSESSMENT_EXPIRED", true);
            refreshAssessmentGuard();
            return;
        }
        String code = v.getCode().trim();
        String lang = v.getSelectedLanguage();
        Problem problem = v.getCurrentProblem();

        if (code.isEmpty()) {
            v.showExecutionResult("Lỗi: Bạn chưa nhập mã nguồn để nộp.", "EMPTY_CODE", true);
            return;
        }

        // Kiểm tra chữ ký / định dạng sinh bởi AI (ChatGPT / Gemini)
        String aiCodeSign = sessionManager.isAssessmentMode() ? AiToolDetector.detectAiPastedCode(code) : null;
        if (aiCodeSign != null) {
            reportAiViolation("AI_CODE_PASTED", aiCodeSign);
            v.showAiWarningAlert("Hệ thống phát hiện mã nguồn có dấu hiệu do AI sinh ra.\nVi phạm này đã được gửi trực tiếp đến Giám thị coi thi.");
        }

        int problemId = (problem != null) ? problem.getId() : 1;

        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) {
            // Chế độ demo khi chưa bật server
            Thread mockThread = new Thread(() -> {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException ignored) {}

                Platform.runLater(() -> {
                    v.setBusy(false, null);
                    v.showJudgingResult("ACCEPTED", 100, 5, 5, 48, 1024, null);
                    sessionManager.recordSubmission(problemId, "ACCEPTED");
                });
            }, "Mock-Judge-Thread");
            mockThread.setDaemon(true);
            mockThread.start();
            return;
        }

        // Đã có kết nối -> Gửi request nộp bài qua SocketClient
        v.setBusy(true, "Đang nộp bài lên Server...");

        Integer classId = (sessionManager.getCurrentClass() != null)
                ? sessionManager.getCurrentClass().getId()
                : (problem != null ? problem.getClassId() : null);

        boolean isZip = v.isZipSubmission();

        submittingAssessmentId = sessionManager.isAssessmentMode() ? sessionManager.getActiveAssessmentId() : null;
        pendingSubmissionId = null;
        awaitingSubmitResponse = true;
        earlyJudgeNotifications.clear();
        socketClient.submitCode(problemId, lang, code, classId, isZip, submittingAssessmentId, response -> {
            Platform.runLater(() -> {
                if (disposed) return;
                handleSubmitResponse(response);
            });
        });
    }

    /**
     * Xử lý SUBMIT_RESPONSE từ Server.
     */
    private void handleSubmitResponse(Response response) {
        awaitingSubmitResponse = false;
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            int submissionId = 0;
            if (response.getData() != null && response.getData().isJsonObject()) {
                JsonObject dataObj = response.getData().getAsJsonObject();
                if (dataObj.has("submissionId")) {
                    submissionId = dataObj.get("submissionId").getAsInt();
                }
            }

            pendingSubmissionId = submissionId > 0 ? submissionId : null;

            view.showExecutionResult(
                    String.format(">> Đã nộp bài thành công (Mã nộp: #%d).\n>> Trạng thái: PENDING (Đang xếp hàng chờ Judge Worker chấm trên Server...)\n", submissionId),
                    "PENDING",
                    false
            );
            view.setBusy(true, "Server đang chấm bài...");
            Response earlyResult = pendingSubmissionId == null ? null : earlyJudgeNotifications.get(pendingSubmissionId);
            earlyJudgeNotifications.clear();
            if (earlyResult != null) handleJudgeNotification(earlyResult);
        } else {
            earlyJudgeNotifications.clear();
            view.setBusy(false, null);
            view.showExecutionResult(
                    "Lỗi nộp bài: " + response.getMessage(),
                    "SUBMIT_FAILED",
                    true
            );
        }
    }

    /**
     * Xử lý push notification JUDGE_NOTIFICATION khi Judge Worker chấm xong.
     */
    public void handleJudgeNotification(Response response) {
        Platform.runLater(() -> {
            if (disposed) return;

            if (response == null || response.getData() == null || !response.getData().isJsonObject()) return;

            try {
                JsonObject data = response.getData().getAsJsonObject();
                Problem current = view.getCurrentProblem();
                if (awaitingSubmitResponse && data.has("submissionId") && data.has("problemId")
                        && current != null && data.get("problemId").getAsInt() == current.getId()) {
                    earlyJudgeNotifications.put(data.get("submissionId").getAsInt(), response);
                    return;
                }
                if (pendingSubmissionId == null || !data.has("submissionId")
                        || data.get("submissionId").getAsInt() != pendingSubmissionId
                        || current == null || !data.has("problemId")
                        || data.get("problemId").getAsInt() != current.getId()) return;
                view.setBusy(false, null);
                String verdict = data.has("verdict") ? data.get("verdict").getAsString() : "UNKNOWN";
                int passed = data.has("passedTests") ? data.get("passedTests").getAsInt()
                        : (data.has("passed") ? data.get("passed").getAsInt() : 0);
                int total = data.has("totalTests") ? data.get("totalTests").getAsInt()
                        : (data.has("total") ? data.get("total").getAsInt() : 0);
                int score = data.has("score") ? data.get("score").getAsInt()
                        : (total > 0 ? (int) Math.round((double) passed / total * 100) : 0);
                long timeMs = data.has("execTimeMs") ? data.get("execTimeMs").getAsLong()
                        : (data.has("executionTimeMs") ? data.get("executionTimeMs").getAsLong() : 0);
                long memKb = data.has("memoryUsedKb") ? data.get("memoryUsedKb").getAsLong()
                        : (data.has("memoryKb") ? data.get("memoryKb").getAsLong() : 0);
                String compileError = (data.has("compileError") && !data.get("compileError").isJsonNull())
                        ? data.get("compileError").getAsString() : null;

                view.showJudgingResult(verdict, score, passed, total, timeMs, memKb, compileError);
                Problem cp = view.getCurrentProblem();
                if (cp != null && submittingAssessmentId == null && !sessionManager.isAssessmentMode()) {
                    sessionManager.recordSubmission(cp.getId(), verdict);
                }
                pendingSubmissionId = null;

            } catch (Exception ex) {
                view.showExecutionResult("Lỗi phân tích kết quả chấm: " + ex.getMessage(), "PARSE_ERROR", true);
            }
        });
    }

    private void handleLanguageChange(String newLanguage) {
        // Dự phòng hook đổi ngôn ngữ
    }

    // =========================================================================
    // PROCTORING: PHÁT HIỆN SỬ DỤNG CHATGPT / GEMINI (CHO PHÉP DÙNG IDE NGOÀI)
    // =========================================================================

    private static final long AI_VIOLATION_COOLDOWN_MS = 45_000L; // 45 giây cooldown giữa các lần tăng count và popup cảnh báo
    private long lastAiDetectionTime = 0;
    private final java.util.concurrent.atomic.AtomicBoolean isScanning = new java.util.concurrent.atomic.AtomicBoolean(false);
    private javafx.beans.value.ChangeListener<Boolean> currentFocusListener;
    private javafx.stage.Window trackedWindow;
    private javafx.animation.Timeline periodicAiChecker;
    private javafx.animation.Timeline assessmentChecker;
    private javafx.animation.Timeline countdownTimeline;

    private void initCountdownTimer() {
        boolean inAssessment = sessionManager.isAssessmentMode();
        view.setCountdownTimerVisible(inAssessment);
        if (inAssessment) {
            updateCountdownTimerDisplay();
        }

        countdownTimeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1), e -> {
                    leavePracticeForUpcomingAssessment();
                    if (sessionManager.isAssessmentMode()) {
                        view.setCountdownTimerVisible(true);
                        updateCountdownTimerDisplay();
                    } else {
                        view.setCountdownTimerVisible(false);
                    }
                }
        ));
        countdownTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateCountdownTimerDisplay() {
        if (!sessionManager.isAssessmentMode()) {
            view.setCountdownTimerVisible(false);
            return;
        }
        view.setCountdownTimerVisible(true);
        long remaining = sessionManager.getRemainingAssessmentSeconds();
        String formatted = sessionManager.formatRemainingAssessmentTime();
        view.updateCountdownTimer(remaining, formatted);
    }

    private void startAssessmentGuard() {
        assessmentChecker = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(3), event -> refreshAssessmentGuard()));
        assessmentChecker.setCycleCount(javafx.animation.Animation.INDEFINITE);
        assessmentChecker.play();
        refreshAssessmentGuard();
    }

    private void refreshAssessmentGuard() {
        SocketClient socketClient = sessionManager.getSocketClient();
        Problem current = view.getCurrentProblem();
        if (disposed || finishingAssessment || assessmentRefreshInFlight || socketClient == null
                || !socketClient.isConnected() || current == null) return;
        assessmentRefreshInFlight = true;
        if (sessionManager.isAssessmentMode()) {
            socketClient.getActiveAssessment(null, response -> Platform.runLater(() -> {
                assessmentRefreshInFlight = false;
                if (disposed || finishingAssessment || !"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
                JsonObject active = response.getData() != null && response.getData().isJsonObject()
                        ? response.getData().getAsJsonObject() : null;
                Integer assessmentId = active != null && active.has("id") ? active.get("id").getAsInt() : null;
                if (active == null || !java.util.Objects.equals(sessionManager.getActiveAssessmentId(), assessmentId)) {
                    sessionManager.setActiveAssessment(null, false);
                    returnToDashboard();
                    return;
                }
                sessionManager.synchronizeAssessmentClock(jsonString(active, "serverTime"));
                Long remaining = active.has("remainingSeconds") && !active.get("remainingSeconds").isJsonNull()
                        ? active.get("remainingSeconds").getAsLong() : null;
                sessionManager.setAssessmentClosesAt(jsonString(active, "closesAt"), remaining);
                updateCountdownTimerDisplay();
                boolean allowed = false;
                if (active.has("problemIds") && active.get("problemIds").isJsonArray()) {
                    for (JsonElement id : active.getAsJsonArray("problemIds")) {
                        if (id.getAsInt() == current.getId()) { allowed = true; break; }
                    }
                }
                if (!allowed) returnToDashboard();
            }));
        } else {
            socketClient.getStudentAssessments(response -> Platform.runLater(() -> {
                assessmentRefreshInFlight = false;
                if (disposed || finishingAssessment || !"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
                scheduledAssessments.clear();
                if (response.getData() != null && response.getData().isJsonArray()) {
                    for (JsonElement element : response.getData().getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject assessment = element.getAsJsonObject();
                        sessionManager.synchronizeAssessmentClock(jsonString(assessment, "serverTime"));
                        scheduledAssessments.add(assessment);
                    }
                }
                leavePracticeForUpcomingAssessment();
            }));
        }
    }

    private void leavePracticeForUpcomingAssessment() {
        if (disposed || finishingAssessment || sessionManager.isAssessmentMode()) return;
        long now = sessionManager.getAssessmentNowEpochMs();
        for (JsonObject assessment : scheduledAssessments) {
            boolean completed = assessment.has("completed") && assessment.get("completed").getAsBoolean();
            AssessmentSchedule schedule = AssessmentSchedule.from(jsonString(assessment, "opensAt"),
                    jsonString(assessment, "closesAt"));
            if (schedule.isLobbyEligible(jsonString(assessment, "status"), completed, now)) {
                returnToDashboard();
                return;
            }
        }
    }

    private String jsonString(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private void returnToDashboard() {
        cleanup();
        view.navigateBackToDashboard();
    }

    private void completeAssessment() {
        Integer id = sessionManager.getActiveAssessmentId();
        if (disposed || finishingAssessment || id == null) return;
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Kết thúc bài thi và quay lại luyện tập? Sau khi kết thúc, bạn không thể làm tiếp bài thi này.",
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        confirm.initOwner(view.getScene().getWindow());
        confirm.setHeaderText("Kết thúc bài thi?");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                != javafx.scene.control.ButtonType.OK) return;
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) return;
        finishingAssessment = true;
        socketClient.completeAssessment(id, response -> Platform.runLater(() -> {
            finishingAssessment = false;
            if (disposed) return;
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                sessionManager.setActiveAssessment(null, true);
                returnToDashboard();
            } else {
                view.showExecutionResult(response.getMessage() == null
                        ? "Không thể kết thúc bài thi. Hãy thử lại." : response.getMessage(), "COMPLETE_FAILED", true);
            }
        }));
    }

    private void initProctoring() {
        view.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                cleanup();
                return;
            }
            if (newScene != null) {
                newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                    if (newWin != null) {
                        attachWindowFocusListener(newWin);
                    }
                });
                if (newScene.getWindow() != null) {
                    attachWindowFocusListener(newScene.getWindow());
                }
            }
        });

        // Luyện tập chỉ cần listener dọn dẹp khi rời trang, không bật giám sát.
        if (!sessionManager.isAssessmentMode()) return;

        if (view.getScene() != null && view.getScene().getWindow() != null) {
            attachWindowFocusListener(view.getScene().getWindow());
        }

        // Quét định kỳ kiểm tra AI mỗi 8 giây (đề phòng mở ChatGPT trên màn hình phụ)
        periodicAiChecker = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(8), e -> scanForAiCheating(false))
        );
        periodicAiChecker.setCycleCount(javafx.animation.Animation.INDEFINITE);
        periodicAiChecker.play();
    }

    private void attachWindowFocusListener(javafx.stage.Window window) {
        if (disposed || !sessionManager.isAssessmentMode()) return;
        if (trackedWindow == window) {
            return;
        }
        if (trackedWindow != null && currentFocusListener != null) {
            trackedWindow.focusedProperty().removeListener(currentFocusListener);
        }

        trackedWindow = window;
        currentFocusListener = (obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && sessionManager.isAssessmentMode()) {
                // Rời khỏi cửa sổ: Cho phép sinh viên dùng IDE bên ngoài (Dev-C++, CLion, VS Code...) hoàn toàn hợp lệ,
                // KHÔNG cảnh báo mất focus (FOCUS_LOSS).
                // Chỉ quét ngầm kiểm tra xem sinh viên có mở công cụ AI (ChatGPT, Gemini, Copilot...) hay không.
                scanForAiCheating(true);
            }
        };
        window.focusedProperty().addListener(currentFocusListener);
    }

    private void scanForAiCheating(boolean triggeredByFocusLoss) {
        if (disposed || finishingAssessment || !sessionManager.isAssessmentMode() || view.isChoosingFile()) {
            return; // Đang chọn file mã nguồn hợp lệ
        }

        long now = System.currentTimeMillis();
        if (now - lastAiDetectionTime < AI_VIOLATION_COOLDOWN_MS) {
            return; // Chưa đủ 45 giây kể từ lần cảnh báo/tăng count gần nhất
        }

        if (!isScanning.compareAndSet(false, true)) {
            return; // Đang có lượt quét trước đó chưa kết thúc
        }

        // Quét bất đồng bộ tiêu đề trình duyệt và tiến trình AI
        AiToolDetector.detectRunningAiToolAsync().whenComplete((detectedAi, ex) -> {
            isScanning.set(false);
            if (ex == null && detectedAi != null) {
                // PHÁT HIỆN ĐANG MỞ CHATGPT / GEMINI / AI TOOL
                lastAiDetectionTime = System.currentTimeMillis();
                Platform.runLater(() -> reportAiViolation("AI_TOOL_DETECTED", detectedAi));
            } else {
                // Sinh viên chỉ sang IDE code (VS Code, CLion...) -> Hoàn toàn hợp lệ!
            }
        });
    }

    private void reportAiViolation(String eventType, String detail) {
        if (disposed || finishingAssessment || !sessionManager.isAssessmentMode()) return;
        Problem problem = view.getCurrentProblem();
        int problemId = (problem != null) ? problem.getId() : 1;
        String problemTitle = (problem != null && problem.getTitle() != null)
                ? problem.getTitle()
                : "Bài tập #" + problemId;

        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            Integer assessmentId = sessionManager.getActiveAssessmentId();
            socketClient.sendProctorEvent(problemId, problemTitle, eventType, detail,
                    assessmentId, response -> {
                Platform.runLater(() -> {
                    if (disposed || finishingAssessment || !sessionManager.isAssessmentMode()
                            || !java.util.Objects.equals(assessmentId, sessionManager.getActiveAssessmentId())) return;
                    if ("SUCCESS".equalsIgnoreCase(response.getStatus())
                            && response.getData() != null
                            && response.getData().isJsonObject()) {
                        JsonObject data = response.getData().getAsJsonObject();
                        int count = data.has("violationCount") ? data.get("violationCount").getAsInt() : 1;
                        String severity = data.has("severity") ? data.get("severity").getAsString() : "CRITICAL";
                        String msg = data.has("warningMessage") ? data.get("warningMessage").getAsString() : response.getMessage();
                        view.updateProctorStatus(count, severity, detail);
                        view.showProctorWarningToast(msg, severity);
                    }
                });
            });
        }
    }

    public void cleanup() {
        disposed = true;
        problemMetadataVersion++;
        dataRefreshDebounce.stop();
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null) socketClient.removeDataChangedListener(dataChangedListener);
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        if (periodicAiChecker != null) {
            periodicAiChecker.stop();
        }
        if (assessmentChecker != null) {
            assessmentChecker.stop();
        }
        if (trackedWindow != null && currentFocusListener != null) {
            trackedWindow.focusedProperty().removeListener(currentFocusListener);
        }
        view.dismissProctorAlert();
    }

    /**
     * Tải lịch sử nộp bài của riêng bài tập này từ Server và hiển thị vào thẻ Lịch sử ở cột phải.
     */
    public void loadSubmissionHistory() {
        Problem problem = view.getCurrentProblem();
        if (problem == null) return;
        int problemId = problem.getId();

        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) return;

        Integer assessmentId = sessionManager.isAssessmentMode()
                ? sessionManager.getActiveAssessmentId() : null;
        socketClient.getHistory(problemId, assessmentId, response -> {
            Platform.runLater(() -> {
                if (disposed) return;
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())
                        && response.getData() != null && response.getData().isJsonArray()) {
                    view.clearHistory();
                    JsonArray arr = response.getData().getAsJsonArray();
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        if (assessmentId == null && obj.has("assessmentId") && !obj.get("assessmentId").isJsonNull()) continue;
                        String title = problem.getTitle();
                        if (obj.has("problemTitle") && !obj.get("problemTitle").isJsonNull()) {
                            title = obj.get("problemTitle").getAsString();
                        }
                        String time = obj.has("submittedAt") && !obj.get("submittedAt").isJsonNull()
                                ? obj.get("submittedAt").getAsString() : "";
                        String verdict = obj.has("verdict") && !obj.get("verdict").isJsonNull()
                                ? obj.get("verdict").getAsString() : "--";
                        String sourceCode = obj.has("sourceCode") && !obj.get("sourceCode").isJsonNull()
                                ? obj.get("sourceCode").getAsString() : null;
                        boolean isZip = obj.has("isZip") && !obj.get("isZip").isJsonNull() && obj.get("isZip").getAsBoolean();

                        view.appendSubmissionHistory(title, time, verdict, sourceCode, isZip);
                    }
                }
            });
        });
    }

    public ProblemView getView() {
        return view;
    }
}
