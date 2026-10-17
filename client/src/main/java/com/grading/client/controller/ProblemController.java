package com.grading.client.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grading.client.model.Problem;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
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

    public ProblemView getView() {
        return view;
    }
}
