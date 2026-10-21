package com.grading.client.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Problem;
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

    public ProblemController(ProblemView view) {
        this.view = view;
        this.sessionManager = SessionManager.getInstance();
        initHandlers();
        registerPushNotifications();
        initProctoring();
        loadSubmissionHistory();
    }

    private void initHandlers() {
        view.setOnSubmitCode(this::handleSubmitCode);
        view.setOnLanguageChange(this::handleLanguageChange);
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
        String code = v.getCode().trim();
        String lang = v.getSelectedLanguage();
        Problem problem = v.getCurrentProblem();

        if (code.isEmpty()) {
            v.showExecutionResult("Lỗi: Bạn chưa nhập mã nguồn để nộp.", "EMPTY_CODE", true);
            return;
        }

        // Kiểm tra chữ ký / định dạng sinh bởi AI (ChatGPT / Gemini)
        String aiCodeSign = AiToolDetector.detectAiPastedCode(code);
        if (aiCodeSign != null) {
            reportAiViolation("AI_CODE_PASTED", aiCodeSign);
            v.showAiWarningAlert("Hệ thống phát hiện mã nguồn có chứa cú pháp/câu giải thích từ AI (" + aiCodeSign + ").\nVi phạm này đã được gửi trực tiếp đến Giám thị coi thi.");
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

        socketClient.submitCode(problemId, lang, code, response -> {
            Platform.runLater(() -> {
                handleSubmitResponse(response);
            });
        });
    }

    /**
     * Xử lý SUBMIT_RESPONSE từ Server.
     */
    private void handleSubmitResponse(Response response) {
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            int submissionId = 0;
            if (response.getData() != null && response.getData().isJsonObject()) {
                JsonObject dataObj = response.getData().getAsJsonObject();
                if (dataObj.has("submissionId")) {
                    submissionId = dataObj.get("submissionId").getAsInt();
                }
            }

            view.showExecutionResult(
                    String.format(">> Đã nộp bài thành công (Mã nộp: #%d).\n>> Trạng thái: PENDING (Đang xếp hàng chờ Judge Worker chấm trên Server...)\n", submissionId),
                    "PENDING",
                    false
            );
            view.setBusy(true, "Server đang chấm bài...");
        } else {
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
            view.setBusy(false, null);

            if (response == null || response.getData() == null) {
                view.showExecutionResult("Nhận thông báo chấm bài nhưng dữ liệu trống.", "UNKNOWN", true);
                return;
            }

            try {
                JsonObject data = response.getData().getAsJsonObject();
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
                if (cp != null) {
                    sessionManager.recordSubmission(cp.getId(), verdict);
                }

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

    private long lastAiDetectionTime = 0;
    private javafx.beans.value.ChangeListener<Boolean> currentFocusListener;
    private javafx.stage.Window trackedWindow;
    private javafx.animation.Timeline periodicAiChecker;

    private void initProctoring() {
        view.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
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
        if (trackedWindow == window) {
            return;
        }
        if (trackedWindow != null && currentFocusListener != null) {
            trackedWindow.focusedProperty().removeListener(currentFocusListener);
        }

        trackedWindow = window;
        currentFocusListener = (obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                // Rời khỏi cửa sổ: Quét xem sinh viên có vào ChatGPT / Gemini hay không.
                // Nếu sinh viên chỉ mở VS Code, CLion, Dev-C++... thì xem là hợp lệ và KHÔNG phạt!
                scanForAiCheating(true);
            }
        };
        window.focusedProperty().addListener(currentFocusListener);
    }

    private void scanForAiCheating(boolean triggeredByFocusLoss) {
        if (view.isChoosingFile()) {
            return; // Đang chọn file mã nguồn hợp lệ
        }

        long now = System.currentTimeMillis();
        if (now - lastAiDetectionTime < 3000) {
            return; // Debounce 3 giây tránh gửi trùng lặp
        }

        // Quét bất đồng bộ tiêu đề trình duyệt và tiến trình AI
        AiToolDetector.detectRunningAiToolAsync().thenAccept(detectedAi -> {
            if (detectedAi != null) {
                // PHÁT HIỆN ĐANG MỞ CHATGPT / GEMINI / AI TOOL
                lastAiDetectionTime = System.currentTimeMillis();
                Platform.runLater(() -> reportAiViolation("AI_TOOL_DETECTED", detectedAi));
            } else {
                // Sinh viên chỉ sang IDE code (VS Code, CLion...) -> Hoàn toàn hợp lệ!
            }
        });
    }

    private void reportAiViolation(String eventType, String detail) {
        Problem problem = view.getCurrentProblem();
        int problemId = (problem != null) ? problem.getId() : 1;
        String problemTitle = (problem != null && problem.getTitle() != null)
                ? problem.getTitle()
                : "Bài tập #" + problemId;

        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            socketClient.sendProctorEvent(problemId, problemTitle, eventType, detail, response -> {
                Platform.runLater(() -> {
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
        if (periodicAiChecker != null) {
            periodicAiChecker.stop();
        }
        if (trackedWindow != null && currentFocusListener != null) {
            trackedWindow.focusedProperty().removeListener(currentFocusListener);
        }
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

        socketClient.getHistory(problemId, response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())
                        && response.getData() != null && response.getData().isJsonArray()) {
                    view.clearHistory();
                    JsonArray arr = response.getData().getAsJsonArray();
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
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

                        view.appendSubmissionHistory(title, time, verdict, sourceCode);
                    }
                }
            });
        });
    }

    public ProblemView getView() {
        return view;
    }
}
