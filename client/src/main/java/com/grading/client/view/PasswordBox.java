package com.grading.client.view;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * PasswordBox — Ô nhập mật khẩu tích hợp icon mắt để bật/tắt hiển thị mật khẩu.
 * Hỗ trợ đồng bộ dữ liệu hai chiều, chuyển tiếp phím Enter và tương thích Light/Dark theme.
 */
public class PasswordBox extends StackPane {

    private final PasswordField passwordField;
    private final TextField plainTextField;
    private final Button toggleBtn;
    private final SVGPath eyeIcon;
    private boolean isPasswordVisible = false;

    // SVG paths: Material Design Visibility (mắt mở) và Visibility Off (mắt gạch chéo)
    private static final String ICON_EYE = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String ICON_EYE_OFF = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    public PasswordBox() {
        this("");
    }

    public PasswordBox(String promptText) {
        setAlignment(Pos.CENTER_RIGHT);
        setMaxWidth(Double.MAX_VALUE);

        passwordField = new PasswordField();
        plainTextField = new TextField();

        passwordField.setMaxWidth(Double.MAX_VALUE);
        plainTextField.setMaxWidth(Double.MAX_VALUE);

        // Chừa khoảng trống bên phải (34px) để chữ không bị đè lên icon mắt
        passwordField.setStyle("-fx-padding: 6 34 6 10;");
        plainTextField.setStyle("-fx-padding: 6 34 6 10;");

        setPromptText(promptText);

        plainTextField.setVisible(false);
        plainTextField.setManaged(false);

        // Đồng bộ dữ liệu hai chiều giữa PasswordField và TextField
        passwordField.textProperty().bindBidirectional(plainTextField.textProperty());

        // Khi người dùng bấm Enter ở chế độ hiện mật khẩu, chuyển tiếp sự kiện
        plainTextField.setOnAction(e -> {
            if (passwordField.getOnAction() != null) {
                passwordField.getOnAction().handle(e);
            }
        });

        // Icon SVG hiển thị hình con mắt
        eyeIcon = new SVGPath();
        eyeIcon.setContent(ICON_EYE);
        eyeIcon.setFillRule(FillRule.EVEN_ODD);
        eyeIcon.setScaleX(0.72);
        eyeIcon.setScaleY(0.72);
        eyeIcon.setFill(Color.web("#858585"));
        eyeIcon.setStyle("-fx-fill: -text-secondary;");
        eyeIcon.getStyleClass().add("eye-icon");

        // Nút toggle
        toggleBtn = new Button();
        toggleBtn.setGraphic(eyeIcon);
        toggleBtn.getStyleClass().addAll("button", "btn-eye-toggle");
        toggleBtn.setFocusTraversable(false); // Không cản trở Tab chuyển focus giữa các ô nhập
        toggleBtn.setTooltip(new Tooltip("Hiện mật khẩu"));
        StackPane.setMargin(toggleBtn, new Insets(0, 4, 0, 0));

        toggleBtn.hoverProperty().addListener((obs, oldVal, isHovered) -> {
            if (isHovered) {
                eyeIcon.setStyle("-fx-fill: -accent;");
            } else {
                eyeIcon.setStyle("-fx-fill: -text-secondary;");
            }
        });

        toggleBtn.setOnAction(e -> toggleVisibility());

        getChildren().addAll(passwordField, plainTextField, toggleBtn);
    }

    /**
     * Bật/Tắt chế độ hiển thị mật khẩu
     */
    public void toggleVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            int pos = passwordField.getCaretPosition();
            passwordField.setVisible(false);
            passwordField.setManaged(false);

            plainTextField.setVisible(true);
            plainTextField.setManaged(true);
            plainTextField.requestFocus();
            plainTextField.positionCaret(pos);

            eyeIcon.setContent(ICON_EYE_OFF);
            toggleBtn.setTooltip(new Tooltip("Ẩn mật khẩu"));
        } else {
            int pos = plainTextField.getCaretPosition();
            plainTextField.setVisible(false);
            plainTextField.setManaged(false);

            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordField.requestFocus();
            passwordField.positionCaret(pos);

            eyeIcon.setContent(ICON_EYE);
            toggleBtn.setTooltip(new Tooltip("Hiện mật khẩu"));
        }
    }

    public String getText() {
        return passwordField.getText();
    }

    public void setText(String text) {
        passwordField.setText(text);
    }

    public void setPromptText(String prompt) {
        passwordField.setPromptText(prompt);
        plainTextField.setPromptText(prompt);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        passwordField.setOnAction(handler);
        plainTextField.setOnAction(handler);
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }

    public TextField getPlainTextField() {
        return plainTextField;
    }

    public Button getToggleBtn() {
        return toggleBtn;
    }
}
