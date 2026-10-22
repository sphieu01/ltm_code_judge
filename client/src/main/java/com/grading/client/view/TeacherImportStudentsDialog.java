package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog nạp dữ liệu từ file để cấp tài khoản sinh viên hàng loạt cho Lớp tín chỉ.
 * Tuân thủ quy chuẩn DESIGN.md Component 7 (Data Import Component).
 */
public class TeacherImportStudentsDialog extends Dialog<TeacherImportStudentsDialog.ImportPayload> {

    public static class StudentPreviewRow {
        public final String username;
        public final String fullName;

        public StudentPreviewRow(String username, String fullName) {
            this.username = username;
            this.fullName = fullName;
        }
    }

    public static class ImportPayload {
        public final int classId;
        public final JsonArray studentsArray;

        public ImportPayload(int classId, JsonArray studentsArray) {
            this.classId = classId;
            this.studentsArray = studentsArray;
        }
    }

    private ComboBox<CreditClass> targetClassCombo;
    private Label fileStatusLabel;
    private TableView<StudentPreviewRow> previewTable;
    private ObservableList<StudentPreviewRow> previewList = FXCollections.observableArrayList();

    public TeacherImportStudentsDialog(Stage owner, List<CreditClass> classes, CreditClass preselectedClass) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Nạp Dữ Liệu Cấp Tài Khoản Sinh Viên Theo Lớp");

        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(720);
        pane.setPrefHeight(600);
        setResizable(true);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        // 1. Chọn lớp tín chỉ mục tiêu
        Label classLabel = new Label("Cấp tài khoản cho Lớp tín chỉ (*)");
        classLabel.getStyleClass().add("meta-item");
        targetClassCombo = new ComboBox<>(FXCollections.observableArrayList(classes));
        if (preselectedClass != null) {
            targetClassCombo.getSelectionModel().select(preselectedClass);
        } else if (!classes.isEmpty()) {
            targetClassCombo.getSelectionModel().select(0);
        }
        targetClassCombo.setMaxWidth(Double.MAX_VALUE);

        // 2. Vùng nạp file dữ liệu (Dropzone)
        VBox dropzone = new VBox(8);
        dropzone.getStyleClass().add("file-dropzone");
        dropzone.setAlignment(Pos.CENTER);

        Label dropTitle = new Label("Nhấp vào đây để chọn file dữ liệu (.csv, .txt)");
        dropTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label dropFormat = new Label("Mỗi dòng: B23DCCNxxx,Họ và tên (xxx từ 001–999; mật khẩu ban đầu là mã sinh viên)");
        dropFormat.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary;");

        fileStatusLabel = new Label("Chưa chọn file");
        fileStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #58A6FF;");

        dropzone.getChildren().addAll(dropTitle, dropFormat, fileStatusLabel);
        dropzone.setOnMouseClicked(e -> chooseFile(owner));

        // 3. Bảng xem trước dữ liệu
        Label previewTitle = new Label("Xem trước danh sách tài khoản:");
        previewTitle.getStyleClass().add("meta-item");

        previewTable = new TableView<>();
        previewTable.getStyleClass().add("table-view");
        previewTable.setPlaceholder(new Label("Chưa nạp dữ liệu. Hãy chọn file CSV hoặc TXT ở trên."));
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudentPreviewRow, String> colUser = new TableColumn<>("Mã sinh viên (*)");
        colUser.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        colUser.setMinWidth(210);
        colUser.setPrefWidth(260);

        TableColumn<StudentPreviewRow, String> colName = new TableColumn<>("Họ và tên");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fullName));
        colName.setMinWidth(300);
        colName.setPrefWidth(390);

        previewTable.getColumns().addAll(colUser, colName);
        previewTable.setItems(previewList);
        TableViewSupport.fitHeightToRows(previewTable, 5);

        form.getChildren().addAll(classLabel, targetClassCombo, dropzone, previewTitle, previewTable);

        Button cancelBtn = new Button("Hủy");
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(event -> close());

        Button saveBtn = new Button("Xác nhận & lưu");
        saveBtn.getStyleClass().addAll("button", "btn-primary");
        saveBtn.setDefaultButton(true);
        saveBtn.setDisable(true);
        Runnable updateSaveState = () -> saveBtn.setDisable(
                targetClassCombo.getSelectionModel().getSelectedItem() == null || previewList.isEmpty());
        previewList.addListener((javafx.collections.ListChangeListener<StudentPreviewRow>) change -> updateSaveState.run());
        targetClassCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldValue, newValue) -> updateSaveState.run());
        saveBtn.setOnAction(event -> {
            CreditClass selectedClass = targetClassCombo.getSelectionModel().getSelectedItem();
            if (selectedClass == null || previewList.isEmpty()) return;
            JsonArray array = new JsonArray();
            for (StudentPreviewRow row : previewList) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username", row.username);
                obj.addProperty("fullName", row.fullName);
                array.add(obj);
            }
            setResult(new ImportPayload(selectedClass.getId(), array));
            close();
        });

        HBox actionBar = new HBox(10, cancelBtn, saveBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.setPadding(new Insets(12, 16, 12, 16));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        BorderPane content = new BorderPane();
        content.setCenter(scroll);
        content.setBottom(actionBar);
        pane.setContent(content);
    }

    private void chooseFile(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file dữ liệu danh sách sinh viên");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File dữ liệu (*.csv, *.txt)", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("Tất cả các file", "*.*")
        );
        File file = chooser.showOpenDialog(owner);
        if (file != null && file.exists()) {
            parseFile(file);
        }
    }

    private void parseFile(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            previewList.clear();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("username")) {
                    continue; // Bỏ qua dòng trống, comment, hoặc header
                }

                List<String> parts = parseDelimitedLine(line);
                if (!parts.isEmpty()) {
                    String username = parts.get(0).trim();
                    if (parts.size() != 2) continue;
                    String fullName = parts.get(1).trim();

                    if (username.matches("^B23DCCN(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})$") && !fullName.isBlank()) {
                        previewList.add(new StudentPreviewRow(username, fullName));
                    }
                }
            }

            fileStatusLabel.setText("Đã nạp: " + file.getName() + " (" + previewList.size() + " sinh viên hợp lệ)");
        } catch (Exception ex) {
            fileStatusLabel.setText("Lỗi đọc file: " + ex.getMessage());
        }
    }

    private List<String> parseDelimitedLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append(ch); i++; }
                else quoted = !quoted;
            } else if (!quoted && (ch == ',' || ch == ';' || ch == '\t')) {
                values.add(current.toString());
                current.setLength(0);
            } else current.append(ch);
        }
        values.add(current.toString());
        return values;
    }
}
