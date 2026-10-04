package com.grading.client.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * LoginView — Màn hình Đăng nhập / Đăng ký tài khoản (Minimalist Flat).
 * Hỗ trợ chọn vai trò: Học sinh (STUDENT) hoặc Giáo viên (TEACHER).
 */
public class LoginView extends BorderPane {

    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;

    // Các trường khi ở chế độ Đăng ký
    private VBox registerExtraBox;
    private TextField fullNameField;
    private ComboBox<String> roleComboBox;

    private Label errorLabel;
    private Button submitBtn;
    private Button toggleModeBtn;
    private Button offlineGuestBtn;

    private boolean isRegisterMode = false;

    public LoginView() {
        buildLayout();
    }

    private void buildLayout() {
        getStyleClass().add("main-pane");

        VBox centerWrapper = new VBox();
        centerWrapper.setAlignment(Pos.CENTER);
        centerWrapper.setPadding(new Insets(30));

        // Flat Card
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setMaxWidth(400);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 36, 32, 36));

        // Tiêu đề
        Label titleLabel = new Label("Auto Grader");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Label subtitleLabel = new Label("Hệ Thống Chấm Code Trực Tuyến");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-muted;");

        // Cấu hình Server Host & Port
        HBox serverBox = new HBox(8);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        hostField = new TextField("localhost");
        hostField.setPromptText("Host");
        HBox.setHgrow(hostField, Priority.ALWAYS);

        portField = new TextField("9999");
        portField.setPromptText("Port");
        portField.setPrefWidth(75);
        serverBox.getChildren().addAll(new Label("Server:"), hostField, portField);

        // Username
        VBox userBox = new VBox(4);
        Label userLabel = new Label("Tên đăng nhập");
        userLabel.getStyleClass().add("meta-item");
        usernameField = new TextField();
        usernameField.setPromptText("Nhập username...");
        userBox.getChildren().addAll(userLabel, usernameField);

        // Password
        VBox passBox = new VBox(4);
        Label passLabel = new Label("Mật khẩu");
        passLabel.getStyleClass().add("meta-item");
        passwordField = new PasswordField();
        passwordField.setPromptText("Nhập mật khẩu...");
        passBox.getChildren().addAll(passLabel, passwordField);

        // Phần bổ sung khi chọn Đăng ký
        registerExtraBox = new VBox(12);
        registerExtraBox.setVisible(false);
        registerExtraBox.setManaged(false);

        VBox nameBox = new VBox(4);
        Label nameLabel = new Label("Họ và tên");
        nameLabel.getStyleClass().add("meta-item");
        fullNameField = new TextField();
        fullNameField.setPromptText("Nguyễn Văn A...");
        nameBox.getChildren().addAll(nameLabel, fullNameField);

        VBox roleBox = new VBox(4);
        Label roleLabel = new Label("Vai trò");
        roleLabel.getStyleClass().add("meta-item");
        roleComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "Học sinh (STUDENT)",
                "Giáo viên (TEACHER)"
        ));
        roleComboBox.getSelectionModel().select(0);
        roleComboBox.setMaxWidth(Double.MAX_VALUE);
        roleBox.getChildren().addAll(roleLabel, roleComboBox);

        registerExtraBox.getChildren().addAll(nameBox, roleBox);

        // Thông báo lỗi
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: -verdict-wa; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        // Nút chính: Đăng nhập / Đăng ký
        submitBtn = new Button("Đăng nhập");
        submitBtn.getStyleClass().addAll("button", "btn-primary");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setStyle("-fx-font-size: 13.5px; -fx-padding: 8 0 8 0;");

        // Nút chuyển chế độ
        toggleModeBtn = new Button("Chưa có tài khoản? Đăng ký ngay");
        toggleModeBtn.getStyleClass().addAll("button", "btn-secondary");
        toggleModeBtn.setMaxWidth(Double.MAX_VALUE);
        toggleModeBtn.setOnAction(e -> toggleMode());

        // Nút khách dùng thử
        offlineGuestBtn = new Button("Dùng thử không cần Server (Khách)");
        offlineGuestBtn.getStyleClass().addAll("button", "btn-secondary");
        offlineGuestBtn.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-muted;");
        offlineGuestBtn.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                serverBox,
                userBox,
                passBox,
                registerExtraBox,
                errorLabel,
                submitBtn,
                toggleModeBtn,
                offlineGuestBtn
        );

        centerWrapper.getChildren().add(card);
        setCenter(centerWrapper);
    }

    public void toggleMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            registerExtraBox.setVisible(true);
            registerExtraBox.setManaged(true);
            submitBtn.setText("Tạo tài khoản");
            toggleModeBtn.setText("Đã có tài khoản? Quay lại Đăng nhập");
        } else {
            registerExtraBox.setVisible(false);
            registerExtraBox.setManaged(false);
            submitBtn.setText("Đăng nhập");
            toggleModeBtn.setText("Chưa có tài khoản? Đăng ký ngay");
        }
        errorLabel.setText("");
    }

    public boolean isRegisterMode() {
        return isRegisterMode;
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 9999;
        }
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getFullName() {
        return fullNameField.getText().trim();
    }

    public String getSelectedRole() {
        String sel = roleComboBox.getSelectionModel().getSelectedItem();
        if (sel != null && sel.contains("TEACHER")) return "TEACHER";
        return "STUDENT";
    }

    public void setError(String msg) {
        errorLabel.setText(msg);
    }

    public void setBusy(boolean busy) {
        submitBtn.setDisable(busy);
        toggleModeBtn.setDisable(busy);
    }

    public Button getSubmitBtn() {
        return submitBtn;
    }

    public Button getOfflineGuestBtn() {
        return offlineGuestBtn;
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }
}
