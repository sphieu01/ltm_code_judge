package com.grading.client.view;

import com.grading.client.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * ProfilePopup — Component hiển thị hồ sơ cá nhân và nút Đăng xuất
 * theo đúng quy chuẩn DESIGN.md khi bấm vào Avatar ở header.
 */
public class ProfilePopup extends Popup {

    private final User user;
    private Runnable onLogout;

    public ProfilePopup(User user) {
        this.user = user;
        setAutoHide(true);
        VBox content = buildPopupContent();
        String cssPath = ThemeManager.getInstance().isDark() ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            content.getStylesheets().setAll(res.toExternalForm());
        }
        ThemeManager.getInstance().applyCurrentZoom(content);
        getContent().add(content);
    }

    private VBox buildPopupContent() {
        VBox container = new VBox(10);
        container.getStyleClass().add("profile-popup");
        container.setMinWidth(280);
        container.setMaxWidth(320);
        // Đảm bảo nền mờ/xuyên thấu không xảy ra khi hiển thị trên các control khác
        boolean isDark = ThemeManager.getInstance().isDark();
        container.setStyle(
            "-fx-background-color: " + (isDark ? "#252526" : "#FFFFFF") + "; " +
            "-fx-border-color: " + (isDark ? "#3E3E42" : "#D0D7DE") + "; " +
            "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; " +
            "-fx-padding: 16px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 10, 0, 0, 4);"
        );

        // Header: Avatar lớn + Tên đầy đủ
        HBox userHeaderBox = new HBox(12);
        userHeaderBox.setAlignment(Pos.CENTER_LEFT);

        Label avatarBig = new Label(user != null ? user.getAvatarText() : "U");
        avatarBig.getStyleClass().add("avatar-large");

        VBox nameBox = new VBox(2);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        String displayName = (user != null && user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : (user != null ? user.getUsername() : "Người dùng");
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-size: 14.5px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Label usernameLabel = new Label("@" + (user != null ? user.getUsername() : "username"));
        usernameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-secondary;");

        nameBox.getChildren().addAll(nameLabel, usernameLabel);
        userHeaderBox.getChildren().addAll(avatarBig, nameBox);

        Separator sep1 = new Separator();

        // Chi tiết thông tin
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(8);
        infoGrid.setVgap(6);
        infoGrid.setPadding(new Insets(2, 0, 2, 0));

        Label roleKey = new Label("Vai trò:");
        roleKey.getStyleClass().add("meta-item");
        Label roleVal = new Label(user != null && user.isTeacher() ? "Giáo viên (Admin)" : "Sinh viên");
        roleVal.getStyleClass().add("badge-role");

        infoGrid.add(roleKey, 0, 0);
        infoGrid.add(roleVal, 1, 0);

        if (user != null && !user.isTeacher()) {
            Label classKey = new Label("Lớp tín chỉ:");
            classKey.getStyleClass().add("meta-item");

            String cName = user.getClassName() != null && !user.getClassName().isBlank()
                    ? user.getClassName()
                    : "Chưa phân lớp";
            Label classVal = new Label(cName);
            classVal.setStyle("-fx-text-fill: -text-primary; -fx-font-weight: bold;");

            infoGrid.add(classKey, 0, 1);
            infoGrid.add(classVal, 1, 1);
        }

        Separator sep2 = new Separator();

        Label zoomTitle = new Label("Thu phóng giao diện");
        zoomTitle.getStyleClass().add("meta-item");
        Button zoomOutBtn = new Button("−");
        Button zoomInBtn = new Button("+");
        Button zoomResetBtn = new Button();
        for (Button button : java.util.List.of(zoomOutBtn, zoomInBtn, zoomResetBtn)) {
            button.getStyleClass().addAll("button", "btn-secondary");
            button.setStyle("-fx-font-size: 12px; -fx-padding: 4px 10px;");
        }
        zoomOutBtn.setMinWidth(42);
        zoomInBtn.setMinWidth(42);
        zoomResetBtn.setMinWidth(82);
        HBox zoomBox = new HBox(8, zoomOutBtn, zoomResetBtn, zoomInBtn);
        zoomBox.setAlignment(Pos.CENTER);
        Runnable refreshZoom = () -> {
            ThemeManager manager = ThemeManager.getInstance();
            zoomResetBtn.setText(manager.getZoomPercent() + "%");
            zoomOutBtn.setDisable(!manager.canZoomOut());
            zoomInBtn.setDisable(!manager.canZoomIn());
        };
        zoomOutBtn.setOnAction(e -> { ThemeManager.getInstance().zoomOut(); refreshZoom.run(); });
        zoomInBtn.setOnAction(e -> { ThemeManager.getInstance().zoomIn(); refreshZoom.run(); });
        zoomResetBtn.setOnAction(e -> { ThemeManager.getInstance().resetZoom(); refreshZoom.run(); });
        refreshZoom.run();

        // Nút Đổi mật khẩu
        Button changePassBtn = new Button("🔒 Đổi mật khẩu");
        changePassBtn.setMaxWidth(Double.MAX_VALUE);
        changePassBtn.getStyleClass().addAll("button", "btn-secondary");
        changePassBtn.setStyle("-fx-padding: 6 0 6 0; -fx-font-size: 12px;");
        changePassBtn.setOnAction(e -> {
            hide();
            javafx.stage.Window window = getOwnerWindow();
            Stage stage = window instanceof Stage ? (Stage) window : null;
            ChangePasswordDialog dialog = new ChangePasswordDialog(stage);
            dialog.showAndWait();
        });

        // Nút Đăng xuất
        Button logoutBtn = new Button("Đăng xuất tài khoản");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.getStyleClass().addAll("button", "btn-secondary");
        logoutBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 7 0 7 0;");
        logoutBtn.setOnAction(e -> {
            hide();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        container.getChildren().addAll(userHeaderBox, sep1, infoGrid, sep2,
                zoomTitle, zoomBox, changePassBtn, logoutBtn);
        return container;
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }
}
