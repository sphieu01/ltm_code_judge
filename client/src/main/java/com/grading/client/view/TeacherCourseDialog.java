package com.grading.client.view;

import com.grading.client.model.Course;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog tạo môn học mới dành cho Giáo viên.
 */
public class TeacherCourseDialog extends Dialog<Course> {

    private TextField codeField;
    private TextField nameField;
    private ComboBox<String> languageCombo;
    private TextArea descArea;

    public TeacherCourseDialog(Stage owner) {
        this(owner, null);
    }

    public TeacherCourseDialog(Stage owner, Course existing) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(existing == null ? "Thêm Môn Học Mới" : "Sửa Môn Học");

        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(480);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        Label codeLabel = new Label("Mã môn học (*)");
        codeLabel.getStyleClass().add("meta-item");
        codeField = new TextField();
        codeField.setPromptText("Ví dụ: INT2204, CS101...");

        Label nameLabel = new Label("Tên môn học (*)");
        nameLabel.getStyleClass().add("meta-item");
        nameField = new TextField();
        nameField.setPromptText("Ví dụ: Lập trình C++, Lập trình Java...");

        Label langLabel = new Label("Ngôn ngữ lập trình chính");
        langLabel.getStyleClass().add("meta-item");
        languageCombo = new ComboBox<>(FXCollections.observableArrayList(
                "cpp - C/C++",
                "java - Java",
                "python - Python 3",
                "c - Ngôn ngữ C"
        ));
        languageCombo.getSelectionModel().select(0);
        languageCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) {
            codeField.setText(existing.getCode());
            nameField.setText(existing.getName());
            String language = existing.getLanguage() == null ? "cpp" : existing.getLanguage();
            languageCombo.getSelectionModel().select(language.equals("java") ? 1 : language.equals("python") ? 2 : language.equals("c") ? 3 : 0);
        }

        Label descLabel = new Label("Mô tả môn học");
        descLabel.getStyleClass().add("meta-item");
        descArea = new TextArea();
        descArea.setPromptText("Mô tả tóm tắt nội dung môn học...");
        descArea.setPrefRowCount(3);
        if (existing != null) descArea.setText(existing.getDescription());

        form.getChildren().addAll(codeLabel, codeField, nameLabel, nameField, langLabel, languageCombo, descLabel, descArea);
        pane.setContent(form);

        ButtonType saveBtnType = new ButtonType("Lưu môn học", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");
        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String code = codeField.getText().trim();
                String name = nameField.getText().trim();
                if (code.isEmpty() || name.isEmpty()) return null;

                String langRaw = languageCombo.getSelectionModel().getSelectedItem();
                String lang = "cpp";
                if (langRaw != null && langRaw.startsWith("java")) lang = "java";
                else if (langRaw != null && langRaw.startsWith("python")) lang = "python";
                else if (langRaw != null && langRaw.startsWith("c -")) lang = "c";

                Course c = new Course();
                if (existing != null) { c.setId(existing.getId()); c.setActive(existing.isActive()); }
                c.setCode(code);
                c.setName(name);
                c.setLanguage(lang);
                c.setDescription(descArea.getText().trim());
                return c;
            }
            return null;
        });
    }
}
