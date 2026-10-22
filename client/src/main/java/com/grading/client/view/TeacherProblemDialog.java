package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * TeacherProblemDialog — Giao diện tạo bài tập dành cho Giáo viên.
 * Hỗ trợ thêm không giới hạn các testcase chấm bài bằng nút bấm động.
 * Tuân thủ quy chuẩn DESIGN.md Component 8.
 */
public class TeacherProblemDialog extends Dialog<Problem> {

    public static class TestCaseItem {
        public final CheckBox sampleCheck;
        public final TextArea inputArea;
        public final TextArea outputArea;
        public final VBox rootCard;

        public TestCaseItem(boolean isSample, String inputDefault, String outputDefault, Runnable onDelete) {
            rootCard = new VBox(6);
            rootCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 10px;");

            HBox cardHeader = new HBox(10);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            sampleCheck = new CheckBox("Là Testcase ví dụ (Sample - Hiển thị trong đề bài)");
            sampleCheck.setSelected(isSample);
            sampleCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-bright;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button deleteBtn = new Button("🗑 Xóa");
            deleteBtn.getStyleClass().addAll("button", "btn-secondary");
            deleteBtn.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            deleteBtn.setOnAction(e -> onDelete.run());

            cardHeader.getChildren().addAll(sampleCheck, sp, deleteBtn);

            HBox ioRow = new HBox(10);
            VBox inBox = new VBox(3);
            Label inLbl = new Label("Input:");
            inLbl.getStyleClass().add("meta-item");
            inputArea = new TextArea(inputDefault);
            inputArea.setPromptText("Dữ liệu đầu vào (Input)...");
            inputArea.setPrefRowCount(2);
            inputArea.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
            inBox.getChildren().addAll(inLbl, inputArea);
            HBox.setHgrow(inBox, Priority.ALWAYS);

            VBox outBox = new VBox(3);
            Label outLbl = new Label("Expected Output:");
            outLbl.getStyleClass().add("meta-item");
            outputArea = new TextArea(outputDefault);
            outputArea.setPromptText("Kết quả mong đợi (Output)...");
            outputArea.setPrefRowCount(2);
            outputArea.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
            outBox.getChildren().addAll(outLbl, outputArea);
            HBox.setHgrow(outBox, Priority.ALWAYS);

            ioRow.getChildren().addAll(inBox, outBox);
            rootCard.getChildren().addAll(cardHeader, ioRow);
        }
    }

    private TextField titleField;
    private ComboBox<String> difficultyCombo;
    private TextField timeLimitField;
    private TextField memoryLimitField;

    private TextArea descriptionArea;
    private TextArea inputFormatArea;
    private TextArea outputFormatArea;
    private TextArea constraintsArea;

    private ComboBox<CreditClass> classCombo;
    private List<CreditClass> availableClasses;
    private CreditClass defaultClass;

    private VBox testCasesContainer;
    private final List<TestCaseItem> testCaseItems = new ArrayList<>();

    public TeacherProblemDialog(Stage owner) {
        this(owner, null, null);
    }

    public TeacherProblemDialog(Stage owner, List<CreditClass> classes, CreditClass defaultClass) {
        this.availableClasses = classes;
        this.defaultClass = defaultClass;
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Thêm bài tập mới — Dành cho Giáo viên");

        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(750);
        pane.setPrefHeight(650);

        ScrollPane scrollPane = new ScrollPane(buildFormContent());
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setFitToWidth(true);
        pane.setContent(scrollPane);

        ButtonType saveBtnType = new ButtonType("Lưu bài tập", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");

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

        // 1. Lớp tín chỉ áp dụng
        Label classLabel = new Label("Gán cho Lớp tín chỉ");
        classLabel.getStyleClass().add("meta-item");
        classCombo = new ComboBox<>();
        if (availableClasses != null && !availableClasses.isEmpty()) {
            classCombo.setItems(FXCollections.observableArrayList(availableClasses));
            if (defaultClass != null) {
                classCombo.getSelectionModel().select(defaultClass);
            } else {
                classCombo.getSelectionModel().select(0);
            }
        }
        classCombo.setPromptText("Tất cả các lớp (Bài tập chung)");
        classCombo.setMaxWidth(Double.MAX_VALUE);

        // 2. Tiêu đề & Thông số
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
        timeLimitField = new TextField("2000");
        timeBox.getChildren().addAll(timeLabel, timeLimitField);

        VBox memBox = new VBox(4);
        Label memLabel = new Label("Bộ nhớ tối đa (KB)");
        memLabel.getStyleClass().add("meta-item");
        memoryLimitField = new TextField("262144"); // 256MB
        memBox.getChildren().addAll(memLabel, memoryLimitField);

        metaRow.getChildren().addAll(diffBox, timeBox, memBox);

        // 3. Mô tả đề bài
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

        // 4. DANH SÁCH TESTCASE ĐỘNG
        HBox tcHeader = new HBox(10);
        tcHeader.setAlignment(Pos.CENTER_LEFT);
        tcHeader.setPadding(new Insets(10, 0, 4, 0));

        Label tcTitle = new Label("DANH SÁCH BỘ TEST CASE CHẤM BÀI");
        tcTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -accent;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addTcBtn = new Button("➕ Thêm Testcase");
        addTcBtn.getStyleClass().addAll("button", "btn-primary");
        addTcBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 4 10 4 10;");
        addTcBtn.setOnAction(e -> addTestCase(false, "", ""));

        tcHeader.getChildren().addAll(tcTitle, sp, addTcBtn);

        testCasesContainer = new VBox(8);

        // Khởi tạo sẵn 2 testcase ban đầu: 1 sample + 1 secret
        addTestCase(true, "3 5", "8");
        addTestCase(false, "100 200", "300");

        form.getChildren().addAll(
                classLabel, classCombo,
                titleLabel, titleField,
                metaRow,
                descLabel, descriptionArea,
                inFmtLabel, inputFormatArea,
                outFmtLabel, outputFormatArea,
                conLabel, constraintsArea,
                tcHeader, testCasesContainer
        );

        return form;
    }

    private void addTestCase(boolean isSample, String inDefault, String outDefault) {
        TestCaseItem item = new TestCaseItem(isSample, inDefault, outDefault, () -> {
            if (testCaseItems.size() > 1) {
                testCaseItems.removeIf(tc -> tc.inputArea == null || tc.rootCard == null);
            }
        });
        // Remove handler
        Runnable removeAction = () -> {
            if (testCaseItems.size() > 1) {
                testCaseItems.remove(item);
                testCasesContainer.getChildren().remove(item.rootCard);
            }
        };
        // Rebuild card with functional delete
        TestCaseItem actualItem = new TestCaseItem(isSample, inDefault, outDefault, removeAction);
        testCaseItems.add(actualItem);
        testCasesContainer.getChildren().add(actualItem.rootCard);
    }

    private Problem extractProblemFromForm() {
        Problem problem = new Problem();
        problem.setTitle(titleField.getText().trim().isEmpty() ? "Bài tập mới" : titleField.getText().trim());
        problem.setDifficulty(difficultyCombo.getSelectionModel().getSelectedItem());
        try {
            problem.setTimeLimitMs(Integer.parseInt(timeLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setTimeLimitMs(2000);
        }
        try {
            problem.setMemoryLimitKb(Integer.parseInt(memoryLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setMemoryLimitKb(262144);
        }

        if (classCombo != null) {
            CreditClass selClass = classCombo.getSelectionModel().getSelectedItem();
            if (selClass != null) {
                problem.setClassId(selClass.getId());
                problem.setClassName(selClass.getName());
            }
        }

        StringBuilder fullDesc = new StringBuilder();
        fullDesc.append(descriptionArea.getText().trim());

        String inFmt = inputFormatArea.getText().trim();
        String outFmt = outputFormatArea.getText().trim();
        String con = constraintsArea.getText().trim();

        if (!inFmt.isEmpty()) {
            fullDesc.append("\n\n**Input:**\n").append(inFmt);
        }
        if (!outFmt.isEmpty()) {
            fullDesc.append("\n\n**Output:**\n").append(outFmt);
        }
        if (!con.isEmpty()) {
            fullDesc.append("\n\n**Giới hạn (Constraints):**\n").append(con);
        }

        problem.setDescription(fullDesc.toString());
        return problem;
    }

    /**
     * Trả về toàn bộ danh sách testcase dưới dạng JsonArray để nộp lên Server.
     */
    public JsonArray getTestCasesArray() {
        JsonArray arr = new JsonArray();
        for (TestCaseItem item : testCaseItems) {
            JsonObject obj = new JsonObject();
            obj.addProperty("inputData", item.inputArea.getText());
            obj.addProperty("expectedOutput", item.outputArea.getText());
            obj.addProperty("isSample", item.sampleCheck.isSelected());
            arr.add(obj);
        }
        return arr;
    }

    /** Nạp dữ liệu bài đã có để giáo viên sửa cả metadata lẫn test case. */
    public void loadForEditing(Problem problem, JsonArray testCases) {
        if (problem == null) return;
        setTitle("Chỉnh sửa bài tập");
        titleField.setText(problem.getTitle() == null ? "" : problem.getTitle());
        difficultyCombo.getSelectionModel().select(problem.getDifficulty());
        timeLimitField.setText(String.valueOf(problem.getTimeLimitMs()));
        memoryLimitField.setText(String.valueOf(problem.getMemoryLimitKb()));
        descriptionArea.setText(problem.getDescription() == null ? "" : problem.getDescription());
        if (classCombo != null && problem.getClassId() != null) {
            for (CreditClass c : classCombo.getItems()) {
                if (c.getId() == problem.getClassId()) { classCombo.getSelectionModel().select(c); break; }
            }
        }
        testCaseItems.clear();
        testCasesContainer.getChildren().clear();
        if (testCases != null) {
            for (JsonElement element : testCases) {
                if (!element.isJsonObject()) continue;
                JsonObject test = element.getAsJsonObject();
                addTestCase(test.has("isSample") && test.get("isSample").getAsBoolean(),
                        test.has("inputData") ? test.get("inputData").getAsString() : "",
                        test.has("expectedOutput") ? test.get("expectedOutput").getAsString() : "");
            }
        }
        if (testCaseItems.isEmpty()) addTestCase(true, "", "");
    }

    public String getSampleInput() {
        for (TestCaseItem item : testCaseItems) {
            if (item.sampleCheck.isSelected()) return item.inputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).inputArea.getText();
    }

    public String getSampleOutput() {
        for (TestCaseItem item : testCaseItems) {
            if (item.sampleCheck.isSelected()) return item.outputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).outputArea.getText();
    }

    public String getSecretInput() {
        for (TestCaseItem item : testCaseItems) {
            if (!item.sampleCheck.isSelected()) return item.inputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).inputArea.getText();
    }

    public String getSecretOutput() {
        for (TestCaseItem item : testCaseItems) {
            if (!item.sampleCheck.isSelected()) return item.outputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).outputArea.getText();
    }
}
