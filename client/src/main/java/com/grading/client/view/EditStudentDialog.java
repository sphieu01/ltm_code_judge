package com.grading.client.view;

import com.grading.client.model.CreditClass;
import com.grading.client.model.User;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Hộp thoại cho phép Giáo viên sửa thông tin sinh viên (Họ tên, Lớp tín chỉ, Đặt lại mật khẩu).
 */
public class EditStudentDialog extends Dialog<EditStudentDialog.EditStudentPayload> {

    public record EditStudentPayload(int studentId, String fullName, Integer classId, String newPassword) {}

    private TextField usernameField;
    private TextField fullNameField;
    private ComboBox<CreditClass> classCombo;
    private PasswordBox newPasswordBox;

    public EditStudentDialog(Stage owner, User.UserDTO student, List<CreditClass> classes) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Chỉnh Sửa Thông Tin Sinh Viên");

        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(440);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        Label userLabel = new Label("Tên đăng nhập (Username):");
        userLabel.getStyleClass().add("meta-item");
        usernameField = new TextField(student.getUsername());
        usernameField.setEditable(false);
        usernameField.setStyle("-fx-opacity: 0.7;");

        Label nameLabel = new Label("Họ và tên (*):");
        nameLabel.getStyleClass().add("meta-item");
        fullNameField = new TextField(student.getFullName());
        fullNameField.setPromptText("Nhập họ và tên sinh viên");

        Label classLabel = new Label("Lớp tín chỉ:");
        classLabel.getStyleClass().add("meta-item");
        classCombo = new ComboBox<>(FXCollections.observableArrayList(classes));
        classCombo.setMaxWidth(Double.MAX_VALUE);
        classCombo.setPromptText("Chưa gán lớp");

        // Chọn lớp hiện tại của sinh viên nếu có
        if (student.getClassId() != null) {
            for (CreditClass c : classes) {
                if (c.getId() == student.getClassId()) {
                    classCombo.getSelectionModel().select(c);
                    break;
                }
            }
        }

        Label passLabel = new Label("Mật khẩu mới (bỏ trống nếu không đổi):");
        passLabel.getStyleClass().add("meta-item");
        newPasswordBox = new PasswordBox("Nhập mật khẩu mới nếu muốn đặt lại");

        form.getChildren().addAll(
                userLabel, usernameField,
                nameLabel, fullNameField,
                classLabel, classCombo,
                passLabel, newPasswordBox
        );

        pane.setContent(form);

        ButtonType saveBtnType = new ButtonType("Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");

        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String fullName = fullNameField.getText() != null ? fullNameField.getText().trim() : "";
                if (fullName.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.initOwner(owner);
                    alert.setTitle("Cảnh báo");
                    alert.setHeaderText(null);
                    alert.setContentText("Họ và tên sinh viên không được để trống!");
                    alert.showAndWait();
                    return null;
                }
                CreditClass selectedClass = classCombo.getSelectionModel().getSelectedItem();
                Integer classId = selectedClass != null ? selectedClass.getId() : null;
                String newPass = newPasswordBox.getText();
                if (newPass != null && newPass.isBlank()) newPass = null;

                return new EditStudentPayload(student.getId(), fullName, classId, newPass);
            }
            return null;
        });
    }
}
