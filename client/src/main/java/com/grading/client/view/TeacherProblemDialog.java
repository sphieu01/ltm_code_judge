package com.grading.client.view;

import com.grading.client.model.Problem;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * TeacherProblemDialog — Giao diện tạo và tải lên bài tập dành cho Giáo viên.
 */
public class TeacherProblemDialog extends Dialog<Problem> {

    private TextField titleField;
    private ComboBox<String> difficultyCombo;
    private TextField timeLimitField;
    private TextField memoryLimitField;

    private TextArea descriptionArea;
    private TextArea inputFormatArea;
    private TextArea outputFormatArea;
    private TextArea constraintsArea;

    private TextArea sampleInputArea;
    private TextArea sampleOutputArea;

    private TextArea hiddenInputArea;
    private TextArea hiddenOutputArea;

    public TeacherProblemDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Thêm bài tập mới — Dành cho Giáo viên");

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("card");
        pane.setPrefWidth(680);
        pane.setPrefHeight(600);

        ScrollPane scrollPane = new ScrollPane(buildFormContent());
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setFitToWidth(true);
        pane.setContent(scrollPane);

        ButtonType saveBtnType = new ButtonType("Lưu bài tập", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        // Nút lưu
        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-submit");

        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                return extractProblemFromForm();
            }
            return null;
        });
    }

    private VBox buildFormContent() {
        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        // Tiêu đề & Độ khó
        Label titleLabel = new Label("Tiêu đề bài tập (*)");
        titleLabel.getStyleClass().add("meta-item");
        titleField = new TextField();
        titleField.setPromptText("Ví dụ: Tìm số nguyên tố, Sắp xếp mảng...");

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox diffBox = new VBox(4);
        Label diffLabel = new Label("Độ khó");
        diffLabel.getStyleClass().add("meta-item");
        difficultyCombo = new ComboBox<>(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));
        difficultyCombo.getSelectionModel().select("EASY");
        diffBox.getChildren().addAll(diffLabel, difficultyCombo);

        VBox timeBox = new VBox(4);
        Label timeLabel = new Label("Thời gian tối đa (ms)");
        timeLabel.getStyleClass().add("meta-item");
        timeLimitField = new TextField("1000");
        timeBox.getChildren().addAll(timeLabel, timeLimitField);

        VBox memBox = new VBox(4);
        Label memLabel = new Label("Bộ nhớ tối đa (KB)");
        memLabel.getStyleClass().add("meta-item");
        memoryLimitField = new TextField("262144"); // 256MB
        memBox.getChildren().addAll(memLabel, memoryLimitField);

        metaRow.getChildren().addAll(diffBox, timeBox, memBox);

        // Mô tả đề bài
        Label descLabel = new Label("Mô tả bài toán (*)");
        descLabel.getStyleClass().add("meta-item");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Nêu rõ yêu cầu bài toán...");
        descriptionArea.setPrefRowCount(4);

        // Input & Output format
        Label inFmtLabel = new Label("Định dạng đầu vào");
        inFmtLabel.getStyleClass().add("meta-item");
        inputFormatArea = new TextArea();
        inputFormatArea.setPromptText("Dòng 1 chứa n, dòng 2 chứa dãy...");
        inputFormatArea.setPrefRowCount(2);

        Label outFmtLabel = new Label("Định dạng đầu ra");
        outFmtLabel.getStyleClass().add("meta-item");
        outputFormatArea = new TextArea();
        outputFormatArea.setPromptText("In ra một số nguyên...");
        outputFormatArea.setPrefRowCount(2);

        // Constraints
        Label conLabel = new Label("Ràng buộc");
        conLabel.getStyleClass().add("meta-item");
        constraintsArea = new TextArea();
        constraintsArea.setPromptText("1 <= N <= 10^5...");
        constraintsArea.setPrefRowCount(2);

        // Sample Test Case
        Label sampleTitle = new Label("VÍ DỤ MẪU (HIỂN THỊ TRONG ĐỀ BÀI)");
        sampleTitle.getStyleClass().add("section-header");

        HBox sampleRow = new HBox(12);
        VBox sinBox = new VBox(4, new Label("Sample Input"), sampleInputArea = new TextArea());
        sampleInputArea.setPromptText("Dữ liệu vào ví dụ");
        sampleInputArea.setPrefRowCount(3);
        HBox.setHgrow(sinBox, Priority.ALWAYS);

        VBox soutBox = new VBox(4, new Label("Sample Output"), sampleOutputArea = new TextArea());
        sampleOutputArea.setPromptText("Dữ liệu ra mong đợi");
        sampleOutputArea.setPrefRowCount(3);
        HBox.setHgrow(soutBox, Priority.ALWAYS);

        sampleRow.getChildren().addAll(sinBox, soutBox);

        // Secret Test Case
        Label secretTitle = new Label("BỘ TEST CASE DÙNG ĐỂ CHẤM (SECRET)");
        secretTitle.getStyleClass().add("section-header");

        HBox secretRow = new HBox(12);
        VBox hinBox = new VBox(4, new Label("Secret Input"), hiddenInputArea = new TextArea());
        hiddenInputArea.setPromptText("Dữ liệu test chấm");
        hiddenInputArea.setPrefRowCount(3);
        HBox.setHgrow(hinBox, Priority.ALWAYS);

        VBox houtBox = new VBox(4, new Label("Expected Output"), hiddenOutputArea = new TextArea());
        hiddenOutputArea.setPromptText("Kết quả chuẩn");
        hiddenOutputArea.setPrefRowCount(3);
        HBox.setHgrow(houtBox, Priority.ALWAYS);

        secretRow.getChildren().addAll(hinBox, houtBox);

        form.getChildren().addAll(
                titleLabel, titleField,
                metaRow,
                descLabel, descriptionArea,
                inFmtLabel, inputFormatArea,
                outFmtLabel, outputFormatArea,
                conLabel, constraintsArea,
                sampleTitle, sampleRow,
                secretTitle, secretRow
        );

        return form;
    }

    private Problem extractProblemFromForm() {
        Problem problem = new Problem();
        problem.setTitle(titleField.getText().trim().isEmpty() ? "Bài tập mới" : titleField.getText().trim());
        problem.setDifficulty(difficultyCombo.getSelectionModel().getSelectedItem());
        try {
            problem.setTimeLimitMs(Integer.parseInt(timeLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setTimeLimitMs(1000);
        }
        try {
            problem.setMemoryLimitKb(Integer.parseInt(memoryLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setMemoryLimitKb(262144);
        }

        StringBuilder fullDesc = new StringBuilder();
        fullDesc.append(descriptionArea.getText().trim());

        problem.setDescription(fullDesc.toString());
        return problem;
    }

    public String getInputFormat() { return inputFormatArea.getText().trim(); }
    public String getOutputFormat() { return outputFormatArea.getText().trim(); }
    public String getConstraints() { return constraintsArea.getText().trim(); }
    public String getSampleInput() { return sampleInputArea.getText().trim(); }
    public String getSampleOutput() { return sampleOutputArea.getText().trim(); }
    public String getSecretInput() { return hiddenInputArea.getText().trim(); }
    public String getSecretOutput() { return hiddenOutputArea.getText().trim(); }
}
