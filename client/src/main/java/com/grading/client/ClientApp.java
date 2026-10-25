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
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.net.URL;

/**
 * ClientApp — Lớp khởi chạy và điều phối luồng màn hình (Scene Navigator).
 *
 * Luồng ứng dụng:
 * 1. Màn hình Đăng nhập / Đăng ký (LoginView)
 * 2. Màn hình Danh sách bài tập / Chọn bài (DashboardView)
 * - Giáo viên có thêm nút "➕ Thêm bài tập"
 * 3. Màn hình Làm bài & Chấm code (ProblemView)
 * - Bấm nút "⬅ Danh sách bài" để quay lại Dashboard
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

        primaryStage.setTitle("Auto Grader - Hệ Thống Chấm Code Tự Động");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        // Mặc định mở full màn hình nhưng chừa lại Taskbar (Visual Bounds)
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(visualBounds.getMinX());
        primaryStage.setY(visualBounds.getMinY());
        primaryStage.setWidth(visualBounds.getWidth());
        primaryStage.setHeight(visualBounds.getHeight());
        primaryStage.setMaximized(true);

        primaryStage.setOnCloseRequest(event -> {
            try {
                SessionManager.getInstance().clear();
            } catch (Exception ignored) {
            }
            Platform.exit();
            System.exit(0);
        });

        // Khởi động tại màn hình Đăng nhập
        showLoginView();
        primaryStage.show();

        // Đảm bảo cửa sổ được maximize tối đa (vẫn chừa taskbar) sau khi hiển thị
        primaryStage.setMaximized(true);
        Platform.runLater(() -> primaryStage.setMaximized(true));
    }

    @Override
    public void stop() throws Exception {
        try {
            SessionManager.getInstance().clear();
        } catch (Exception ignored) {
        }
        super.stop();
        System.exit(0);
    }

    /**
     * Hiển thị màn hình Đăng nhập.
     */
    public void showLoginView() {
        LoginView loginView = new LoginView();
        LoginController loginController = new LoginController(loginView);

        loginController.setOnLoginSuccess(user -> {
            var client = SessionManager.getInstance().getSocketClient();
            if (client != null) {
                client.setForceLogoutCallback(reason -> {
                    javafx.application.Platform.runLater(() -> {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.WARNING
                        );
                        alert.setTitle("Cảnh báo bảo mật phiên");
                        alert.setHeaderText("Phiên làm việc đã bị kết thúc!");
                        alert.setContentText(reason);
                        alert.show();

                        SessionManager.getInstance().clear();
                        showLoginView();
                    });
                });
            }

            if (user != null && user.isTeacher()) {
                showAdminView(user);
            } else {
                showDashboardView(user);
            }
        });

        switchScene(loginView, 950, 650);
    }

    /**
     * Hiển thị Cổng Quản Trị dành riêng cho Giáo viên (AdminView).
     */
    public void showAdminView(User user) {
        com.grading.client.view.AdminView adminView = new com.grading.client.view.AdminView();
        adminView.setUser(user);

        // Lắng nghe push cảnh báo giám sát từ sinh viên
        var client = SessionManager.getInstance().getSocketClient();
        if (client != null) {
            client.setProctorAlertCallback(alertResponse -> {
                javafx.application.Platform.runLater(() -> {
                    String msg = alertResponse.getMessage() != null
                            ? alertResponse.getMessage()
                            : "Cảnh báo vi phạm phòng thi mới!";
                    adminView.showProctorAlertToast(msg);
                });
            });
        }

        com.grading.client.controller.AdminController adminController =
                new com.grading.client.controller.AdminController(adminView, primaryStage);

        adminController.setOnLogout(this::showLoginView);

        switchScene(adminView, 1150, 720);
    }

    /**
     * Hiển thị màn hình Dashboard (Danh sách bài tập của Sinh viên).
     */
    public void showDashboardView(User user) {
        DashboardView dashboardView = new DashboardView();
        dashboardView.setUser(user);

        DashboardController dashboardController = new DashboardController(dashboardView, primaryStage);

        dashboardController.setOnSelectProblem(problem -> {
            showProblemView(problem);
        });

        dashboardController.setOnLogout(this::showLoginView);

        switchScene(dashboardView, 1100, 700);
    }

    /**
     * Hiển thị màn hình Chi tiết bài làm & Code Editor (ProblemView).
     */
    public void showProblemView(Problem problem) {
        com.grading.client.model.CreditClass curClass = SessionManager.getInstance().getCurrentClass();
        if (curClass != null) {
            if (problem.getClassId() == null || problem.getClassId() <= 0) {
                problem.setClassId(curClass.getId());
                problem.setClassName(curClass.getName());
            }
        }

        ProblemView problemView = new ProblemView();
        if (problem.getAllowedLanguages() != null && !problem.getAllowedLanguages().isBlank()) {
            problemView.setAllowedLanguages(problem.getAllowedLanguages());
        } else if (curClass != null) {
            problemView.setAllowedLanguages(curClass.getEffectiveAllowedLanguages());
        }
        problemView.loadProblem(problem);

        // Tùy biến chi tiết đề bài theo bài được chọn
        populateProblemDetails(problemView, problem);

        ProblemController problemController = new ProblemController(problemView);

        // Nút quay lại: nếu là Giáo viên về AdminView, nếu là Sinh viên về DashboardView
        problemView.setOnBackToDashboard(() -> {
            problemController.cleanup();
            User currentUser = SessionManager.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.isTeacher()) {
                showAdminView(currentUser);
            } else {
                showDashboardView(currentUser);
            }
        });

        switchScene(problemView, 1200, 750);
    }


    private void switchScene(javafx.scene.Parent root, double width, double height) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        if (mainScene == null) {
            mainScene = new Scene(root, visualBounds.getWidth(), visualBounds.getHeight());
            com.grading.client.view.ThemeManager.getInstance().registerScene(mainScene);
            primaryStage.setScene(mainScene);
        } else {
            mainScene.setRoot(root);
            com.grading.client.view.ThemeManager.getInstance().applyTheme(mainScene);
        }
        // Giữ các giao diện mặc định luôn full màn hình (chừa taskbar)
        primaryStage.setMaximized(true);
    }

    private void populateProblemDetails(ProblemView view, Problem problem) {
        if (problem == null)
            return;

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
            } catch (Exception ignored) {
            }
        }

        String constraints = String.format("• Giới hạn thời gian: %s\n• Giới hạn bộ nhớ: %s",
                problem.getTimeLimitFormatted(), problem.getMemoryLimitFormatted());

        view.setProblemDetails(desc, inputFormat, outputFormat, constraints, null, null);

        // Nạp sample test cases thật từ server qua GET_PROBLEM_DETAIL
        com.grading.client.network.SocketClient socketClient = SessionManager.getInstance().getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            socketClient.getProblemDetail(problem.getId(), resp -> {
                if ("SUCCESS".equalsIgnoreCase(resp.getStatus()) && resp.getData() != null
                        && resp.getData().isJsonObject()) {
                    com.google.gson.JsonObject pObj = resp.getData().getAsJsonObject();
                    if (pObj.has("testCases") && pObj.get("testCases").isJsonArray()) {
                        com.google.gson.JsonArray tcArr = pObj.getAsJsonArray("testCases");
                        javafx.application.Platform.runLater(() -> {
                            int count = 1;
                            for (com.google.gson.JsonElement el : tcArr) {
                                com.google.gson.JsonObject tc = el.getAsJsonObject();
                                String input = tc.has("inputData") && !tc.get("inputData").isJsonNull()
                                        ? tc.get("inputData").getAsString()
                                        : "";
                                String expected = tc.has("expectedOutput") && !tc.get("expectedOutput").isJsonNull()
                                        ? tc.get("expectedOutput").getAsString()
                                        : "";
                                view.addSampleTestCase("Ví dụ " + count++, input, expected);
                            }
                        });
                    }
                }
            });
        } else {
            // Mẫu test case khi chạy offline để hiển thị bảng ví dụ
            if (problem.getId() == 1 || "GẤP ĐÔI".equalsIgnoreCase(problem.getTitle())) {
                view.addSampleTestCase("Ví dụ", "23", "46");
            } else if (problem.getId() == 2) {
                view.addSampleTestCase("Ví dụ", "2 3", "5");
            } else if (problem.getId() == 3) {
                view.addSampleTestCase("Ví dụ", "7", "YES");
            } else {
                view.addSampleTestCase("Ví dụ", "6\n1 2 5 3 4 7", "4");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
