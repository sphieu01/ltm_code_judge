package com.grading.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * LoginView — Màn hình Đăng nhập tài khoản (Minimalist Flat).
 * Hệ thống chia thành 2 đối tượng chính: Giáo viên và Sinh viên.
 * Tài khoản Sinh viên được cấp bởi Giáo viên qua file danh sách lớp tín chỉ.
 */
public class LoginView extends BorderPane {

    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;

    private Label errorLabel;
    private Button submitBtn;

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

        // Tiêu đề & Theme Toggle
        HBox topTitleBox = new HBox(8);
        topTitleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Auto Grader");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button themeBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeBtn.getStyleClass().addAll("button", "btn-theme");
        themeBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });
        topTitleBox.getChildren().addAll(titleLabel, titleSpacer, themeBtn);

        Label subtitleLabel = new Label("Hệ Thống Chấm Code Trực Tuyến & Quản Lý Lớp Tín Chỉ");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-secondary;");

        // Backend server mặc định
        hostField = new TextField("localhost");
        portField = new TextField("9999");

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

        // Thông báo lỗi
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        // Nút chính: Đăng nhập
        submitBtn = new Button("Đăng nhập");
        submitBtn.getStyleClass().addAll("button", "btn-primary");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setStyle("-fx-font-size: 13.5px; -fx-padding: 8 0 8 0;");

        card.getChildren().addAll(
                topTitleBox,
                subtitleLabel,
                userBox,
                passBox,
                errorLabel,
                submitBtn);

        centerWrapper.getChildren().add(card);
        setCenter(centerWrapper);
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

    public void setError(String msg) {
        errorLabel.setText(msg);
    }

    public void setBusy(boolean busy) {
        submitBtn.setDisable(busy);
    }

    public Button getSubmitBtn() {
        return submitBtn;
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }
}
