package com.grading.client.view;

import com.grading.client.model.Course;
import com.grading.client.model.CreditClass;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Dialog tạo lớp tín chỉ mới dành cho Giáo viên.
 */
public class TeacherClassDialog extends Dialog<CreditClass> {

    private TextField classCodeField;
    private TextField nameField;
    private ComboBox<Course> courseCombo;

    public TeacherClassDialog(Stage owner, List<Course> availableCourses) {
        this(owner, availableCourses, null);
    }

    public TeacherClassDialog(Stage owner, List<Course> availableCourses, CreditClass existingClass) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        boolean isEdit = (existingClass != null);
        setTitle(isEdit ? "Chỉnh Sửa Lớp Tín Chỉ" : "Thêm Lớp Tín Chỉ Mới");

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(480);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        Label codeLabel = new Label("Mã lớp tín chỉ (*)");
        codeLabel.getStyleClass().add("meta-item");
        classCodeField = new TextField(isEdit ? existingClass.getClassCode() : "");
        classCodeField.setPromptText("Ví dụ: INT2204_01, D21CNPM01...");

        Label nameLabel = new Label("Tên lớp tín chỉ (*)");
        nameLabel.getStyleClass().add("meta-item");
        nameField = new TextField(isEdit ? existingClass.getName() : "");
        nameField.setPromptText("Ví dụ: Lớp Thực hành C++ Nhóm 1...");

        Label courseLabel = new Label("Thuộc Môn học (*)");
        courseLabel.getStyleClass().add("meta-item");
        courseCombo = new ComboBox<>(FXCollections.observableArrayList(availableCourses));
        if (isEdit) {
            for (Course c : availableCourses) {
                if (c.getId() == existingClass.getCourseId()) {
                    courseCombo.getSelectionModel().select(c);
                    break;
                }
            }
        } else if (!availableCourses.isEmpty()) {
            courseCombo.getSelectionModel().select(0);
        }
        courseCombo.setMaxWidth(Double.MAX_VALUE);

        form.getChildren().addAll(codeLabel, classCodeField, nameLabel, nameField, courseLabel, courseCombo);
        pane.setContent(form);

        ButtonType saveBtnType = new ButtonType(isEdit ? "Lưu thay đổi" : "Lưu lớp tín chỉ", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");
        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String code = classCodeField.getText().trim();
                String name = nameField.getText().trim();
                Course selectedCourse = courseCombo.getSelectionModel().getSelectedItem();
                if (code.isEmpty() || name.isEmpty() || selectedCourse == null) return null;

                CreditClass cc = new CreditClass();
                if (isEdit) {
                    cc.setId(existingClass.getId());
                }
                cc.setClassCode(code);
                cc.setName(name);
                cc.setCourseId(selectedCourse.getId());
                cc.setCourseName(selectedCourse.getName());
                return cc;
            }
            return null;
        });
    }
}
