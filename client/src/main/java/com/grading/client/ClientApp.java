package com.grading.client;

import com.grading.client.controller.DashboardController;
import com.grading.client.controller.LoginController;
import com.grading.client.controller.ProblemController;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.view.DashboardView;
import com.grading.client.view.LoginView;
import com.grading.client.view.ProblemView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * ClientApp — Lớp khởi chạy và điều phối luồng màn hình (Scene Navigator).
 *
 * Luồng ứng dụng:
 * 1. Màn hình Đăng nhập / Đăng ký (LoginView)
 * 2. Màn hình Danh sách bài tập / Chọn bài (DashboardView)
 *    - Giáo viên có thêm nút "➕ Thêm bài tập"
 * 3. Màn hình Làm bài & Chấm code (ProblemView)
 *    - Bấm nút "⬅ Danh sách bài" để quay lại Dashboard
 */
public class ClientApp extends Application {

    private Stage primaryStage;
    private Scene mainScene;
    private String stylesheetUrl;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        URL css = getClass().getResource("/css/style.css");
        if (css != null) {
            stylesheetUrl = css.toExternalForm();
        }

        primaryStage.setTitle("Auto Grader — Hệ Thống Chấm Code Tự Động");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        primaryStage.setOnCloseRequest(event -> {
            SessionManager.getInstance().clear();
        });

        // Khởi động tại màn hình Đăng nhập
        showLoginView();
        primaryStage.show();
    }

    /**
     * Hiển thị màn hình Đăng nhập / Đăng ký.
     */
    public void showLoginView() {
        LoginView loginView = new LoginView();
        LoginController loginController = new LoginController(loginView);

        loginController.setOnLoginSuccess(user -> {
            showDashboardView(user);
        });

        switchScene(loginView, 950, 650);
    }

    /**
     * Hiển thị màn hình Dashboard (Danh sách bài tập).
     */
    public void showDashboardView(User user) {
        DashboardView dashboardView = new DashboardView();
        dashboardView.setUser(user);

        DashboardController dashboardController = new DashboardController(dashboardView, primaryStage);

        dashboardController.setOnSelectProblem(problem -> {
            showProblemView(problem);
        });

        dashboardController.setOnLogout(() -> {
            showLoginView();
        });

        switchScene(dashboardView, 1100, 700);
    }

    /**
     * Hiển thị màn hình Chi tiết bài làm & Code Editor (ProblemView).
     */
    public void showProblemView(Problem problem) {
        ProblemView problemView = new ProblemView();
        problemView.loadProblem(problem);

        // Tùy biến chi tiết đề bài theo bài được chọn
        populateProblemDetails(problemView, problem);

        ProblemController problemController = new ProblemController(problemView);

        // Nút quay lại Dashboard
        problemView.setOnBackToDashboard(() -> {
            User currentUser = SessionManager.getInstance().getCurrentUser();
            showDashboardView(currentUser);
        });

        switchScene(problemView, 1200, 750);
    }

    private void switchScene(javafx.scene.Parent root, double width, double height) {
        if (mainScene == null) {
            mainScene = new Scene(root, width, height);
            com.grading.client.view.ThemeManager.getInstance().registerScene(mainScene);
            primaryStage.setScene(mainScene);
        } else {
            mainScene.setRoot(root);
            com.grading.client.view.ThemeManager.getInstance().applyTheme(mainScene);
        }
    }

    private void populateProblemDetails(ProblemView view, Problem problem) {
        if (problem == null) return;

        String desc = problem.getDescription() != null ? problem.getDescription() : "Mô tả chi tiết bài toán.";
        String inputFormat = "Đầu vào chuẩn theo quy định bài toán.";
        String outputFormat = "Đầu ra chuẩn theo quy định bài toán.";

        if (desc.contains("**Input:**") && desc.contains("**Output:**")) {
            try {
                int inIdx = desc.indexOf("**Input:**");
                int outIdx = desc.indexOf("**Output:**");
                String mainDesc = desc.substring(0, inIdx).trim();
                inputFormat = desc.substring(inIdx + 10, outIdx).trim();
                outputFormat = desc.substring(outIdx + 11).trim();
                desc = mainDesc;
            } catch (Exception ignored) {}
        }

        String constraints = String.format("• Giới hạn thời gian: %s\n• Giới hạn bộ nhớ: %s",
                problem.getTimeLimitFormatted(), problem.getMemoryLimitFormatted());

        view.setProblemDetails(desc, inputFormat, outputFormat, constraints, null, null);

        // Nạp sample test cases thật từ server qua GET_PROBLEM_DETAIL
        com.grading.client.network.SocketClient socketClient = SessionManager.getInstance().getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            socketClient.getProblemDetail(problem.getId(), resp -> {
                if ("SUCCESS".equalsIgnoreCase(resp.getStatus()) && resp.getData() != null && resp.getData().isJsonObject()) {
                    com.google.gson.JsonObject pObj = resp.getData().getAsJsonObject();
                    if (pObj.has("testCases") && pObj.get("testCases").isJsonArray()) {
                        com.google.gson.JsonArray tcArr = pObj.getAsJsonArray("testCases");
                        javafx.application.Platform.runLater(() -> {
                            int count = 1;
                            for (com.google.gson.JsonElement el : tcArr) {
                                com.google.gson.JsonObject tc = el.getAsJsonObject();
                                String input = tc.has("inputData") && !tc.get("inputData").isJsonNull() ? tc.get("inputData").getAsString() : "";
                                String expected = tc.has("expectedOutput") && !tc.get("expectedOutput").isJsonNull() ? tc.get("expectedOutput").getAsString() : "";
                                view.addSampleTestCase("Ví dụ " + count++, input, expected);
                            }
                        });
                    }
                }
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
