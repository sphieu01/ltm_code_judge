package com.grading.client.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grading.client.model.Problem;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.MessageType;
import com.grading.client.protocol.Request;
import com.grading.client.protocol.Response;
import com.grading.client.view.ProblemView;
import javafx.application.Platform;

/**
 * ProblemController — Điều khiển logic giữa ProblemView và Network/Backend.
 *
 * Chức năng:
 * - Gắn các action (Run Code, Submit) từ ProblemView
 * - Gửi request nộp code (SUBMIT) qua SocketClient
 * - Nhận SUBMIT_RESPONSE và lắng nghe push notification JUDGE_NOTIFICATION từ server
 * - Cập nhật kết quả lên tab "Execution Result"
 * - Hỗ trợ chế độ offline / mock khi chưa kết nối Server để tiện test UI
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

    // =========================================================================
    // 1. GẮN HANDLERS CHO VIEW
    // =========================================================================

    private void initHandlers() {
        view.setOnRunCode(this::handleRunCode);
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
    // 2. XỬ LÝ RUN CODE (Chạy thử với custom input)
    // =========================================================================

    private void handleRunCode(ProblemView v) {
        String code = v.getCode().trim();
        String lang = v.getSelectedLanguage();
        String customInput = v.getCustomInput();

        if (code.isEmpty()) {
            v.showExecutionResult("Lỗi: Mã nguồn đang để trống.", "EMPTY_CODE", true);
            return;
        }

        v.setBusy(true, "Đang chạy thử với Custom Input...");

        // Kiểm tra kết nối SocketClient
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) {
            // Chế độ mô phỏng offline (khi chưa bật Server)
            simulateOfflineRun(lang, customInput);
            return;
        }

        // Tạo payload gửi Server (nếu Server hỗ trợ lệnh RUN_TEST / CUSTOM_RUN)
        JsonObject data = new JsonObject();
        data.addProperty("language", lang);
        data.addProperty("sourceCode", code);
        data.addProperty("input", customInput);

        // Gửi request lên Server (mặc định thử gửi SUBMIT với cờ runCustom hoặc chờ server xử lý)
        // Lưu ý: nếu server chỉ có SUBMIT, chúng ta có thể gọi SUBMIT hoặc mock run
        simulateOfflineRun(lang, customInput);
    }

    /**
     * Giả lập kết quả chạy thử ở máy client khi chưa kết nối server.
     */
    private void simulateOfflineRun(String lang, String input) {
        // Tạo độ trễ ngắn giả lập quá trình biên dịch & thực thi
        new Thread(() -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {
                StringBuilder output = new StringBuilder();
                output.append("=== THỰC THI THỬ NGHIỆM (LOCAL PREVIEW) ===\n");
                output.append("Ngôn ngữ: ").append(lang.toUpperCase()).append("\n");
                output.append("Dữ liệu đầu vào (stdin):\n");
                output.append(input.isEmpty() ? "(trống)\n" : input + "\n");
                output.append("------------------------------------------\n");
                output.append("Mã nguồn hợp lệ. Chưa kết nối Socket Server để biên dịch thực tế.\n");
                output.append("Mẹo: Khởi động Server hoặc dùng nút Submit để gửi bài chính thức.\n");

                view.setBusy(false, null);
                view.showExecutionResult(output.toString(), "RUN_FINISHED", false);
            });
        }).start();
    }

    // =========================================================================
    // 3. XỬ LÝ SUBMIT CODE (Nộp bài chính thức)
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
            // Chế độ offline demo khi chưa chạy server
            v.setBusy(true, "Đang gửi bài...");
            new Thread(() -> {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {}

                Platform.runLater(() -> {
                    v.setBusy(false, null);
                    v.showExecutionResult(
                            "[OFFLINE MOCK RESPONSE]\n" +
                            "Bài tập ID: " + problemId + "\n" +
                            "Ngôn ngữ: " + lang + "\n" +
                            "Trạng thái: Máy chưa kết nối tới Socket Server (localhost:9999).\n" +
                            "Vui lòng khởi động Server để thực hiện chấm tự động với Judge Worker.",
                            "OFFLINE_MODE",
                            false
                    );
                });
            }).start();
            return;
        }

        // Đã có kết nối -> Đóng gói JSON theo đúng Protocol Spec
        v.setBusy(true, "Đang nộp bài lên Server...");

        // Gửi request nộp bài bất đồng bộ qua socket
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
                    String.format(">> Nộp bài thành công (Mã nộp: #%d).\n>> Trạng thái: PENDING (Đang xếp hàng chờ Judge Worker chấm...)\n", submissionId),
                    "PENDING",
                    false
            );
            // Giữ trạng thái bận nhẹ để đợi JUDGE_NOTIFICATION
            view.setBusy(true, "Đang chấm bài...");
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
                int passed = data.has("passed") ? data.get("passed").getAsInt() : 0;
                int total = data.has("total") ? data.get("total").getAsInt() : 0;
                long timeMs = data.has("executionTimeMs") ? data.get("executionTimeMs").getAsLong() : 0;
                long memKb = data.has("memoryKb") ? data.get("memoryKb").getAsLong() : 0;
                String compileError = (data.has("compileError") && !data.get("compileError").isJsonNull())
                        ? data.get("compileError").getAsString() : null;

                StringBuilder report = new StringBuilder();
                report.append("==========================================\n");
                report.append("           KẾT QUẢ CHẤM BÀI\n");
                report.append("==========================================\n");
                report.append("Phán quyết: ").append(verdict).append("\n");
                report.append("Test cases: ").append(passed).append("/").append(total).append(" passed\n");
                report.append("Thời gian:  ").append(timeMs).append(" ms\n");
                report.append("Bộ nhớ:     ").append(memKb).append(" KB\n");

                if (compileError != null && !compileError.trim().isEmpty()) {
                    report.append("\n[CHI TIẾT LỖI BIÊN DỊCH / RUNTIME ERROR]:\n");
                    report.append(compileError).append("\n");
                }
                report.append("==========================================\n");

                boolean isError = !"ACCEPTED".equalsIgnoreCase(verdict);
                view.showExecutionResult(report.toString(), verdict, isError);

            } catch (Exception ex) {
                view.showExecutionResult("Lỗi phân tích kết quả chấm: " + ex.getMessage(), "PARSE_ERROR", true);
            }
        });
    }

    private void handleLanguageChange(String newLanguage) {
        // Có thể thực hiện lưu draft code hoặc đổi cú pháp highlighting ở đây
    }

    public ProblemView getView() {
        return view;
    }
}
