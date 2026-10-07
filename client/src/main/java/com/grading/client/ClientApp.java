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

        if (problem.getId() == 1) {
            view.setProblemDetails(
                    "Cho hai số nguyên a và b. Nhiệm vụ của bạn là tính và in ra giá trị tổng a + b.\n" +
                    "Đây là bài tập cơ bản kiểm tra luồng vào/ra chuẩn stdin/stdout.",
                    "Một dòng duy nhất chứa hai số nguyên a và b cách nhau bởi dấu cách.",
                    "In ra một số nguyên duy nhất là kết quả a + b.",
                    "• -10^9 ≤ a, b ≤ 10^9\n• Giới hạn thời gian: 1.0 giây\n• Giới hạn bộ nhớ: 256 MB",
                    "3 5",
                    "8"
            );
            view.addSampleTestCase("Ví dụ 2", "-10 25", "15");
        } else if (problem.getId() == 2) {
            view.setProblemDetails(
                    "Cho một số nguyên dương N. Hãy kiểm tra xem N có phải là số nguyên tố hay không.",
                    "Gồm một dòng chứa duy nhất số nguyên dương N.",
                    "In ra 'YES' nếu N là số nguyên tố, ngược lại in ra 'NO'.",
                    "• 1 ≤ N ≤ 10^12\n• Giới hạn thời gian: 1.0 giây\n• Giới hạn bộ nhớ: 256 MB",
                    "7",
                    "YES"
            );
            view.addSampleTestCase("Ví dụ 2", "12", "NO");
        } else {
            view.setProblemDetails(
                    problem.getDescription() != null ? problem.getDescription() : "Mô tả chi tiết bài toán.",
                    "Đầu vào chuẩn theo quy định bài toán.",
                    "Đầu ra chuẩn theo quy định bài toán.",
                    "• Giới hạn thời gian: " + problem.getTimeLimitFormatted() + "\n• Giới hạn bộ nhớ: " + problem.getMemoryLimitFormatted(),
                    "1",
                    "1"
            );
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
