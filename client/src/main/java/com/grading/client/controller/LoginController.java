package com.grading.client.controller;

import com.google.gson.JsonObject;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import com.grading.client.view.LoginView;
import javafx.application.Platform;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * LoginController — Xử lý kết nối TCP và Đăng nhập qua SocketClient.
 * Không sử dụng tài khoản dùng thử offline hay chế độ tự đăng ký.
 */
public class LoginController {

    private final LoginView view;
    private final SessionManager sessionManager;
    private Consumer<User> onLoginSuccess;

    public LoginController(LoginView view) {
        this.view = view;
        this.sessionManager = SessionManager.getInstance();
        initEvents();
    }

    private void initEvents() {
        view.getSubmitBtn().setOnAction(e -> handleSubmit());
        view.getPasswordField().setOnAction(e -> handleSubmit());
    }

    private void handleSubmit() {
        String username = view.getUsername();
        String password = view.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            view.setError("Vui lòng điền đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        String host = view.getHost();
        int port = view.getPort();

        view.setBusy(true);
        view.setError("");

        // Kết nối Socket nếu chưa kết nối hoặc đổi host/port
        Thread loginThread = new Thread(() -> {
            SocketClient client = sessionManager.getSocketClient();
            try {
                if (client == null || !client.isConnected()) {
                    client = new SocketClient(host, port);
                    client.connect();
                    sessionManager.setSocketClient(client);
                }

                // Gửi yêu cầu đăng nhập
                client.login(username, password, response -> {
                    Platform.runLater(() -> handleLoginResponse(response));
                });

            } catch (IOException ex) {
                Platform.runLater(() -> {
                    view.setBusy(false);
                    view.setError("Không thể kết nối đến Máy chủ Server (" + host + ":" + port + "). Vui lòng đảm bảo Server đã được khởi chạy.");
                });
            }
        }, "Login-Thread");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void handleLoginResponse(Response response) {
        view.setBusy(false);
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            User user = new User();
            if (response.getData() != null && response.getData().isJsonObject()) {
                JsonObject data = response.getData().getAsJsonObject();
                if (data.has("token")) sessionManager.getSocketClient().setAuthToken(data.get("token").getAsString());
                if (data.has("userId")) user.setId(data.get("userId").getAsInt());
                if (data.has("role")) user.setRole(data.get("role").getAsString());
                if (data.has("fullName")) user.setFullName(data.get("fullName").getAsString());
                if (data.has("classId") && !data.get("classId").isJsonNull()) {
                    user.setClassId(data.get("classId").getAsInt());
                }
                if (data.has("className") && !data.get("className").isJsonNull()) {
                    user.setClassName(data.get("className").getAsString());
                }
            }
            user.setUsername(view.getUsername());
            sessionManager.setCurrentUser(user);

            if (onLoginSuccess != null) {
                onLoginSuccess.accept(user);
            }
        } else {
            view.setError("Đăng nhập thất bại: " + response.getMessage());
        }
    }

    public void setOnLoginSuccess(Consumer<User> callback) {
        this.onLoginSuccess = callback;
    }

    public LoginView getView() {
        return view;
    }
}
