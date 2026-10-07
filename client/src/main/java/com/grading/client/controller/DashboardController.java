package com.grading.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.MessageType;
import com.grading.client.protocol.Response;
import com.grading.client.view.DashboardView;
import com.grading.client.view.TeacherProblemDialog;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * DashboardController — Quản lý danh sách bài tập và điều phối các thao tác
 * (Chọn bài, Giáo viên thêm bài tập, Đăng xuất).
 */
public class DashboardController {

    private final DashboardView view;
    private final SessionManager sessionManager;
    private Stage stage;

    private Consumer<Problem> onSelectProblem;
    private Runnable onLogout;

    public DashboardController(DashboardView view, Stage stage) {
        this.view = view;
        this.stage = stage;
        this.sessionManager = SessionManager.getInstance();

        initEvents();
        loadProblems();
    }

    private void initEvents() {
        view.setOnSelectProblem(problem -> {
            if (onSelectProblem != null) {
                onSelectProblem.accept(problem);
            }
        });

        view.setOnTeacherAddProblem(this::handleTeacherAddProblem);
        view.setOnRefresh(this::loadProblems);
        view.setOnLogout(() -> {
            sessionManager.clear();
            if (onLogout != null) onLogout.run();
        });
    }

    /**
     * Nạp danh sách bài tập từ Server (hoặc danh sách mẫu nếu đang offline).
     */
    public void loadProblems() {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            socketClient.getProblems(response -> {
                Platform.runLater(() -> handleProblemsResponse(response));
            });
        } else {
            // Danh sách bài tập mặc định khi chạy offline
            view.setProblems(createDefaultProblems());
        }
    }

    private void handleProblemsResponse(Response response) {
        if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
            List<Problem> list = new ArrayList<>();
            try {
                if (response.getData().isJsonArray()) {
                    JsonArray arr = response.getData().getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        Problem p = new Problem();
                        if (obj.has("id")) p.setId(obj.get("id").getAsInt());
                        if (obj.has("title")) p.setTitle(obj.get("title").getAsString());
                        if (obj.has("difficulty")) p.setDifficulty(obj.get("difficulty").getAsString());
                        if (obj.has("timeLimitMs")) p.setTimeLimitMs(obj.get("timeLimitMs").getAsInt());
                        if (obj.has("memoryLimitKb")) p.setMemoryLimitKb(obj.get("memoryLimitKb").getAsInt());
                        if (obj.has("description")) p.setDescription(obj.get("description").getAsString());
                        list.add(p);
                    }
                }
                view.setProblems(list);
            } catch (Exception ex) {
                view.setProblems(createDefaultProblems());
            }
        } else {
            view.setProblems(createDefaultProblems());
        }
    }

    /**
     * Mở Dialog dành cho Giáo viên để tạo bài tập mới.
     */
    private void handleTeacherAddProblem() {
        TeacherProblemDialog dialog = new TeacherProblemDialog(stage);
        Optional<Problem> result = dialog.showAndWait();

        result.ifPresent(newProblem -> {
            // Gán ID tạm thời
            newProblem.setId((int) (System.currentTimeMillis() % 10000));

            // Thêm vào danh sách hiển thị
            view.addProblem(newProblem);

            // Gửi lên Server nếu có kết nối
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                JsonObject data = new JsonObject();
                data.addProperty("title", newProblem.getTitle());
                data.addProperty("difficulty", newProblem.getDifficulty());
                data.addProperty("timeLimitMs", newProblem.getTimeLimitMs());
                data.addProperty("memoryLimitKb", newProblem.getMemoryLimitKb());
                data.addProperty("description", newProblem.getDescription());
                data.addProperty("inputFormat", dialog.getInputFormat());
                data.addProperty("outputFormat", dialog.getOutputFormat());
                data.addProperty("constraints", dialog.getConstraints());
                data.addProperty("sampleInput", dialog.getSampleInput());
                data.addProperty("sampleOutput", dialog.getSampleOutput());

                // Nếu có secret test cases
                JsonArray testCases = new JsonArray();
                JsonObject tc = new JsonObject();
                tc.addProperty("input", dialog.getSecretInput());
                tc.addProperty("output", dialog.getSecretOutput());
                testCases.add(tc);
                data.add("testCases", testCases);

                // Gửi request tạo bài
                client.sendRequest(MessageType.GET_PROBLEMS, data, resp -> {
                    // Xử lý callback nếu server hỗ trợ CREATE_PROBLEM
                });
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText("Bài tập '" + newProblem.getTitle() + "' đã được tạo thành công!");
            alert.showAndWait();
        });
    }

    private List<Problem> createDefaultProblems() {
        List<Problem> list = new ArrayList<>();

        Problem p1 = new Problem();
        p1.setId(1);
        p1.setTitle("A + B Problem");
        p1.setDifficulty("EASY");
        p1.setTimeLimitMs(1000);
        p1.setMemoryLimitKb(262144);
        p1.setDescription("Cho hai số nguyên a và b. Hãy tính và in ra tổng a + b.");
        list.add(p1);

        Problem p2 = new Problem();
        p2.setId(2);
        p2.setTitle("Kiểm tra số nguyên tố");
        p2.setDifficulty("EASY");
        p2.setTimeLimitMs(1000);
        p2.setMemoryLimitKb(262144);
        p2.setDescription("Nhập vào một số nguyên dương N. Kiểm tra xem N có phải là số nguyên tố hay không. In ra YES hoặc NO.");
        list.add(p2);

        Problem p3 = new Problem();
        p3.setId(3);
        p3.setTitle("Dãy con tăng dài nhất (LIS)");
        p3.setDifficulty("MEDIUM");
        p3.setTimeLimitMs(2000);
        p3.setMemoryLimitKb(262144);
        p3.setDescription("Cho dãy gồm N số nguyên. Tìm độ dài dãy con tăng nghiêm ngặt dài nhất.");
        list.add(p3);

        Problem p4 = new Problem();
        p4.setId(4);
        p4.setTitle("Đường đi ngắn nhất trên đồ thị (Dijkstra)");
        p4.setDifficulty("HARD");
        p4.setTimeLimitMs(3000);
        p4.setMemoryLimitKb(524288);
        p4.setDescription("Cho đồ thị có trọng số dương gồm N đỉnh và M cạnh. Tìm đường đi ngắn nhất từ đỉnh 1 đến đỉnh N.");
        list.add(p4);

        return list;
    }

    public void setOnSelectProblem(Consumer<Problem> callback) {
        this.onSelectProblem = callback;
    }

    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    public DashboardView getView() {
        return view;
    }
}
