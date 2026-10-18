package com.grading.client.view;

import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * ChangePasswordDialog — Hộp thoại đổi mật khẩu cho Sinh viên (và Giáo viên).
 * Tuân thủ quy chuẩn DESIGN.md Component 10.
 */
public class ChangePasswordDialog extends Dialog<Boolean> {

    private PasswordField oldPasswordField;
    private PasswordField newPasswordField;
    private PasswordField confirmPasswordField;
    private Label errorLabel;
    private Button submitBtn;

    public ChangePasswordDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Đổi Mật Khẩu");

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(420);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        Label title = new Label("Đổi mật khẩu tài khoản");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        // 1. Mật khẩu hiện tại
        Label oldPassLabel = new Label("Mật khẩu hiện tại (*)");
        oldPassLabel.getStyleClass().add("meta-item");
        oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Nhập mật khẩu hiện tại...");

        // 2. Mật khẩu mới
        Label newPassLabel = new Label("Mật khẩu mới (*)");
        newPassLabel.getStyleClass().add("meta-item");
        newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Tối thiểu 6 ký tự...");

        // 3. Xác nhận mật khẩu mới
        Label confirmPassLabel = new Label("Xác nhận mật khẩu mới (*)");
        confirmPassLabel.getStyleClass().add("meta-item");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu mới...");

        // Label lỗi
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        form.getChildren().addAll(
                title,
                oldPassLabel, oldPasswordField,
                newPassLabel, newPasswordField,
                confirmPassLabel, confirmPasswordField,
                errorLabel
        );
        pane.setContent(form);

        ButtonType submitBtnType = new ButtonType("Đổi mật khẩu", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(submitBtnType, cancelBtnType);

        submitBtn = (Button) pane.lookupButton(submitBtnType);
        submitBtn.getStyleClass().addAll("button", "btn-primary");
        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        // Ngăn auto close khi click submit nếu có lỗi validation
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String oldPass = oldPasswordField.getText();
            String newPass = newPasswordField.getText();
            String confirmPass = confirmPasswordField.getText();

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                errorLabel.setText("Vui lòng nhập đầy đủ thông tin.");
                event.consume();
                return;
            }

            if (newPass.length() < 6) {
                errorLabel.setText("Mật khẩu mới phải có tối thiểu 6 ký tự.");
                event.consume();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                errorLabel.setText("Xác nhận mật khẩu mới không khớp.");
                event.consume();
                return;
            }

            // Gọi API đổi mật khẩu
            SocketClient client = SessionManager.getInstance().getSocketClient();
            if (client == null || !client.isConnected()) {
                errorLabel.setText("Mất kết nối với máy chủ.");
                event.consume();
                return;
            }

            event.consume(); // Consume to await server callback
            submitBtn.setDisable(true);
            errorLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 12px;");
            errorLabel.setText("Đang xử lý...");

            client.changePassword(oldPass, newPass, response -> {
                Platform.runLater(() -> {
                    submitBtn.setDisable(false);
                    if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Thông báo");
                        alert.setHeaderText(null);
                        alert.setContentText("Đổi mật khẩu thành công!");
                        alert.showAndWait();
                        setResult(true);
                        close();
                    } else {
                        errorLabel.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 12px;");
                        errorLabel.setText("Lỗi: " + response.getMessage());
                    }
                });
            });
        });
    }
}
