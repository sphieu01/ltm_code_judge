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
 * LoginController — Xử lý kết nối TCP và Đăng nhập / Đăng ký qua SocketClient.
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
        view.getOfflineGuestBtn().setOnAction(e -> handleGuestLogin());
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
        new Thread(() -> {
            SocketClient client = sessionManager.getSocketClient();
            try {
                if (client == null || !client.isConnected()) {
                    client = new SocketClient(host, port);
                    client.connect();
                    sessionManager.setSocketClient(client);
                }

                if (view.isRegisterMode()) {
                    // Đăng ký
                    String fullName = view.getFullName();
                    String role = view.getSelectedRole();
                    client.register(username, password, fullName, response -> {
                        Platform.runLater(() -> handleRegisterResponse(response));
                    });
                } else {
                    // Đăng nhập
                    client.login(username, password, response -> {
                        Platform.runLater(() -> handleLoginResponse(response));
                    });
                }

            } catch (IOException ex) {
                Platform.runLater(() -> {
                    view.setBusy(false);
                    view.setError("Không thể kết nối đến Server (" + host + ":" + port + "). Vui lòng kiểm tra lại Server hoặc dùng nút 'Dùng thử không cần Server' bên dưới.");
                });
            }
        }).start();
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

    private void handleRegisterResponse(Response response) {
        view.setBusy(false);
        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
            view.setError("");
            view.toggleMode(); // Chuyển về màn hình đăng nhập
            view.setError("Đăng ký thành công! Hãy đăng nhập bằng tài khoản vừa tạo.");
        } else {
            view.setError("Đăng ký thất bại: " + response.getMessage());
        }
    }

    private void handleGuestLogin() {
        User guest = new User();
        guest.setId(999);
        guest.setUsername("guest");
        guest.setFullName("Người dùng thử nghiệm");
        guest.setRole("TEACHER"); // Cho phép test cả chức năng giáo viên up bài và học sinh giải bài
        sessionManager.setCurrentUser(guest);

        if (onLoginSuccess != null) {
            onLoginSuccess.accept(guest);
        }
    }

    public void setOnLoginSuccess(Consumer<User> callback) {
        this.onLoginSuccess = callback;
    }

    public LoginView getView() {
        return view;
    }
}
