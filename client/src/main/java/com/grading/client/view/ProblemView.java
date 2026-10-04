package com.grading.client.view;

import com.grading.client.model.Problem;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ProblemView — Giao diện chính màn hình làm bài và nộp code.
 *
 * Thiết kế tối giản phẳng (flat minimalist):
 * - Không gradient, không bóng đổ (drop shadow), không viền neon.
 * - Cho phép copy toàn bộ nội dung đề bài và test cases mẫu.
 * - Bố cục:
 *   + Root: BorderPane
 *   + Top: HBox Header/Toolbar (Nút Back, Tiêu đề, ComboBox ngôn ngữ, Run Code, Submit)
 *   + Center: SplitPane chia Trái (Đề bài copyable) / Phải (Editor + Console)
 */
public class ProblemView extends BorderPane {

    // ======================== TOP CONTROLS ========================
    private Button backButton;
    private Label problemTitleLabel;
    private ComboBox<String> languageComboBox;
    private Button runButton;
    private Button submitButton;

    // ======================== LEFT PANEL CONTROLS (COPYABLE) ========================
    private Label metaTimeLimitLabel;
    private Label metaMemoryLimitLabel;
    private Label metaDifficultyLabel;
    private TextArea descriptionArea;
    private TextArea inputFormatArea;
    private TextArea outputFormatArea;
    private TextArea constraintsArea;
    private VBox sampleCasesContainer;

    // ======================== RIGHT PANEL CONTROLS ========================
    private TextArea codeEditorArea;
    private TabPane consoleTabPane;
    private Tab testCaseTab;
    private Tab resultTab;
    private TextArea customInputArea;
    private TextArea consoleOutputArea;
    private Label resultStatusBadge;

    // ======================== DATA & HANDLERS ========================
    private Problem currentProblem;
    private Consumer<ProblemView> onRunCodeHandler;
    private Consumer<ProblemView> onSubmitCodeHandler;
    private Consumer<String> onLanguageChangeHandler;
    private Runnable onBackToDashboardHandler;

    // Boilerplate code mẫu cho từng ngôn ngữ
    private final Map<String, String> languageTemplates = new HashMap<>();

    public ProblemView() {
        initLanguageTemplates();
        buildLayout();
        setupEditorTabHandling();
        setupEventBindings();
    }

    // =========================================================================
    // 1. XÂY DỰNG BỐ CỤC CHÍNH (LAYOUT BUILDER)
    // =========================================================================

    private void buildLayout() {
        getStyleClass().add("main-pane");

        // Top: Toolbar
        HBox topToolbar = buildTopToolbar();
        setTop(topToolbar);

        // Center: SplitPane chính chia đôi màn hình (Trái: Đề bài, Phải: Code + Console)
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);

        // Panel Trái: Đề bài (hỗ trợ copy)
        ScrollPane leftProblemPane = buildProblemDescriptionPanel();

        // Panel Phải: Code Editor (Trên) & Console TabPane (Dưới)
        SplitPane rightCodeAndConsoleSplitPane = buildRightPanel();

        mainSplitPane.getItems().addAll(leftProblemPane, rightCodeAndConsoleSplitPane);
        mainSplitPane.setDividerPositions(0.40); // 40% đề bài, 60% code & console

        setCenter(mainSplitPane);
    }

    // =========================================================================
    // 2. TOP TOOLBAR (HBox)
    // =========================================================================

    private HBox buildTopToolbar() {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("top-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setSpacing(12);
        toolbar.setPadding(new Insets(8, 16, 8, 16));

        // Nút quay lại danh sách bài
        backButton = new Button("⬅ Danh sách bài");
        backButton.getStyleClass().addAll("button", "btn-secondary");

        // Tiêu đề bài toán
        problemTitleLabel = new Label("Bài toán: Đang tải...");
        problemTitleLabel.getStyleClass().add("problem-title-header");

        // Khoảng trống co giãn (HBox.setHgrow)
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ComboBox chọn ngôn ngữ (C++, Java, Python, v.v.)
        languageComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "C++ (g++ 17)",
                "Java (OpenJDK 17)",
                "Python 3"
        ));
        languageComboBox.getSelectionModel().select("C++ (g++ 17)");

        // Nút "Run Code" (màu xám cơ bản, phẳng, không bóng đổ)
        runButton = new Button("▶  Run Code");
        runButton.getStyleClass().addAll("button", "btn-run");

        // Nút "Submit" (màu xanh lá mặn/nhạt, tối giản, phẳng)
        submitButton = new Button("✔  Submit");
        submitButton.getStyleClass().addAll("button", "btn-submit");

        toolbar.getChildren().addAll(
                backButton,
                problemTitleLabel,
                spacer,
                languageComboBox,
                runButton,
                submitButton
        );

        return toolbar;
    }

    // =========================================================================
    // 3. PANEL TRÁI: ĐỀ BÀI (ScrollPane bọc VBox, cho phép bôi đen & Copy)
    // =========================================================================

    private ScrollPane buildProblemDescriptionPanel() {
        VBox contentBox = new VBox();
        contentBox.getStyleClass().add("problem-panel");
        contentBox.setSpacing(12);
        contentBox.setPadding(new Insets(16, 20, 20, 20));

        // Thanh meta thông số (Time limit, Memory limit, Difficulty)
        HBox metaBar = new HBox(16);
        metaBar.getStyleClass().add("meta-info-bar");
        metaBar.setAlignment(Pos.CENTER_LEFT);

        metaTimeLimitLabel = new Label("Giới hạn thời gian: --");
        metaTimeLimitLabel.getStyleClass().add("meta-item");

        metaMemoryLimitLabel = new Label("Giới hạn bộ nhớ: --");
        metaMemoryLimitLabel.getStyleClass().add("meta-item");

        metaDifficultyLabel = new Label("Độ khó: --");
        metaDifficultyLabel.getStyleClass().add("meta-item");

        metaBar.getChildren().addAll(metaTimeLimitLabel, metaMemoryLimitLabel, metaDifficultyLabel);

        // Mục 1: Mô tả (không còn ngoặc đơn)
        Label descHeader = createSectionHeader("MÔ TẢ");
        descriptionArea = createSelectableTextArea("Chưa có thông tin mô tả.");

        // Mục 2: Định dạng đầu vào (bỏ chữ INPUT)
        Label inputHeader = createSectionHeader("ĐỊNH DẠNG ĐẦU VÀO");
        inputFormatArea = createSelectableTextArea("Chưa có định dạng đầu vào.");

        // Mục 3: Định dạng đầu ra (bỏ chữ OUTPUT)
        Label outputHeader = createSectionHeader("ĐỊNH DẠNG ĐẦU RA");
        outputFormatArea = createSelectableTextArea("Chưa có định dạng đầu ra.");

        // Mục 4: Ràng buộc (bỏ chữ CONSTRAINTS)
        Label constraintsHeader = createSectionHeader("RÀNG BUỘC");
        constraintsArea = createSelectableTextArea("Chưa có thông tin ràng buộc.");

        // Mục 5: Ví dụ (bỏ chữ SAMPLE CASES)
        Label sampleHeader = createSectionHeader("VÍ DỤ");
        sampleCasesContainer = new VBox(12);

        contentBox.getChildren().addAll(
                metaBar,
                descHeader, descriptionArea,
                inputHeader, inputFormatArea,
                outputHeader, outputFormatArea,
                constraintsHeader, constraintsArea,
                sampleHeader, sampleCasesContainer
        );

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        return scrollPane;
    }

    /**
     * Tạo một TextArea read-only nhưng cho phép người dùng bôi đen và Ctrl+C sao chép.
     */
    private TextArea createSelectableTextArea(String text) {
        TextArea area = new TextArea(text != null ? text : "");
        area.setEditable(false);
        area.setWrapText(true);
        area.getStyleClass().addAll("text-area", "selectable-text");
        adjustTextAreaHeight(area, text);
        return area;
    }

    private void adjustTextAreaHeight(TextArea area, String text) {
        if (text == null || text.isEmpty()) {
            area.setPrefRowCount(2);
            return;
        }
        int lineCount = text.split("\n").length;
        // Ước lượng thêm số dòng do wrap text
        int wrapEstimate = text.length() / 60;
        int totalLines = Math.max(2, Math.min(15, lineCount + wrapEstimate));
        area.setPrefRowCount(totalLines);
    }

    // =========================================================================
    // 4. PANEL PHẢI: KHU VỰC CODE & CONSOLE (SplitPane chia Trên/Dưới)
    // =========================================================================

    private SplitPane buildRightPanel() {
        SplitPane rightSplitPane = new SplitPane();
        rightSplitPane.setOrientation(Orientation.VERTICAL);

        // Phần Trên: Code Editor
        VBox editorPane = buildCodeEditorPane();

        // Phần Dưới: Console / Result TabPane
        VBox consolePane = buildConsolePane();

        rightSplitPane.getItems().addAll(editorPane, consolePane);
        rightSplitPane.setDividerPositions(0.65); // 65% cho editor, 35% cho console

        return rightSplitPane;
    }

    private VBox buildCodeEditorPane() {
        VBox container = new VBox();
        VBox.setVgrow(container, Priority.ALWAYS);

        // Thanh tiêu đề editor nhỏ
        HBox editorHeader = new HBox(8);
        editorHeader.getStyleClass().add("editor-header-bar");
        editorHeader.setAlignment(Pos.CENTER_LEFT);
        Label editorTitle = new Label("Source Code");
        editorTitle.getStyleClass().add("editor-title");
        editorHeader.getChildren().add(editorTitle);

        // TextArea gõ code
        codeEditorArea = new TextArea();
        codeEditorArea.getStyleClass().addAll("text-area", "code-editor");
        codeEditorArea.setPromptText("// Gõ mã nguồn của bạn tại đây...");
        VBox.setVgrow(codeEditorArea, Priority.ALWAYS);

        // Đảm bảo font chữ là Monospace
        Font monoFont = Font.font("Consolas", 13.5);
        if (monoFont.getFamily().equals("System")) {
            monoFont = Font.font("Monospaced", 13.5);
        }
        codeEditorArea.setFont(monoFont);

        // Nạp template ban đầu
        codeEditorArea.setText(languageTemplates.get("C++ (g++ 17)"));

        container.getChildren().addAll(editorHeader, codeEditorArea);
        return container;
    }

    private VBox buildConsolePane() {
        VBox container = new VBox();
        VBox.setVgrow(container, Priority.ALWAYS);

        consoleTabPane = new TabPane();
        consoleTabPane.getStyleClass().add("tab-pane");
        consoleTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(consoleTabPane, Priority.ALWAYS);

        // Tab 1: "Test Cases" (TextArea để nhập custom input)
        testCaseTab = new Tab("Test Cases");
        customInputArea = new TextArea();
        customInputArea.getStyleClass().addAll("text-area", "console-area");
        customInputArea.setPromptText("Nhập dữ liệu đầu vào (stdin) để chạy thử...");
        customInputArea.setFont(Font.font("Consolas", 12.5));
        testCaseTab.setContent(customInputArea);

        // Tab 2: "Execution Result" (TextArea read-only in console output hoặc lỗi)
        resultTab = new Tab("Execution Result");
        VBox resultBox = new VBox();
        VBox.setVgrow(resultBox, Priority.ALWAYS);

        consoleOutputArea = new TextArea();
        consoleOutputArea.getStyleClass().addAll("text-area", "console-area");
        consoleOutputArea.setEditable(false);
        consoleOutputArea.setPromptText("Kết quả thực thi hoặc lỗi sẽ hiển thị tại đây...");
        consoleOutputArea.setFont(Font.font("Consolas", 12.5));
        VBox.setVgrow(consoleOutputArea, Priority.ALWAYS);

        // Thanh trạng thái dưới đáy của tab Execution Result (Verdict / status)
        HBox statusBar = new HBox(12);
        statusBar.getStyleClass().add("result-status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Label statusTitle = new Label("Trạng thái:");
        statusTitle.getStyleClass().add("status-badge-neutral");

        resultStatusBadge = new Label("Chưa có lượt chạy");
        resultStatusBadge.getStyleClass().add("status-badge-neutral");

        statusBar.getChildren().addAll(statusTitle, resultStatusBadge);
        resultBox.getChildren().addAll(consoleOutputArea, statusBar);

        resultTab.setContent(resultBox);

        consoleTabPane.getTabs().addAll(testCaseTab, resultTab);
        container.getChildren().add(consoleTabPane);

        return container;
    }

    // =========================================================================
    // 5. CẤU HÌNH PHÍM TAB TRONG CODE EDITOR (Tránh nhảy focus ra ngoài)
    // =========================================================================

    private void setupEditorTabHandling() {
        codeEditorArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.TAB) {
                event.consume();
                int caretPos = codeEditorArea.getCaretPosition();
                codeEditorArea.insertText(caretPos, "    ");
            }
        });
    }

    // =========================================================================
    // 6. EVENT BINDINGS & TEMPLATES
    // =========================================================================

    private void setupEventBindings() {
        backButton.setOnAction(e -> {
            if (onBackToDashboardHandler != null) {
                onBackToDashboardHandler.run();
            }
        });

        runButton.setOnAction(e -> {
            if (onRunCodeHandler != null) {
                onRunCodeHandler.accept(this);
            }
        });

        submitButton.setOnAction(e -> {
            if (onSubmitCodeHandler != null) {
                onSubmitCodeHandler.accept(this);
            }
        });

        languageComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String currentCode = codeEditorArea.getText().trim();
                String oldTemplate = (oldVal != null) ? languageTemplates.getOrDefault(oldVal, "").trim() : "";

                if (currentCode.isEmpty() || currentCode.equals(oldTemplate)) {
                    codeEditorArea.setText(languageTemplates.getOrDefault(newVal, ""));
                }

                if (onLanguageChangeHandler != null) {
                    onLanguageChangeHandler.accept(newVal);
                }
            }
        });
    }

    private void initLanguageTemplates() {
        languageTemplates.put("C++ (g++ 17)",
                "#include <iostream>\n" +
                "using namespace std;\n\n" +
                "int main() {\n" +
                "    ios_base::sync_with_stdio(false);\n" +
                "    cin.tie(NULL);\n\n" +
                "    // Viết code giải bài tại đây\n\n" +
                "    return 0;\n" +
                "}\n"
        );

        languageTemplates.put("Java (OpenJDK 17)",
                "import java.util.Scanner;\n\n" +
                "public class Solution {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner scanner = new Scanner(System.in);\n\n" +
                "        // Viết code giải bài tại đây\n\n" +
                "    }\n" +
                "}\n"
        );

        languageTemplates.put("Python 3",
                "import sys\n\n" +
                "def solve():\n" +
                "    input_data = sys.stdin.read().split()\n" +
                "    # Viết code giải bài tại đây\n\n" +
                "if __name__ == '__main__':\n" +
                "    solve()\n"
        );
    }

    // =========================================================================
    // 7. PUBLIC API & COPY FEATURE
    // =========================================================================

    public void loadProblem(Problem problem) {
        this.currentProblem = problem;
        if (problem == null) return;

        problemTitleLabel.setText(String.format("Bài %d: %s", problem.getId(), problem.getTitle()));
        metaTimeLimitLabel.setText(String.format("Giới hạn thời gian: %s", problem.getTimeLimitFormatted()));
        metaMemoryLimitLabel.setText(String.format("Giới hạn bộ nhớ: %s", problem.getMemoryLimitFormatted()));
        metaDifficultyLabel.setText(String.format("Độ khó: %s",
                problem.getDifficulty() != null ? problem.getDifficulty() : "Chưa xác định"));

        if (problem.getDescription() != null) {
            descriptionArea.setText(problem.getDescription());
            adjustTextAreaHeight(descriptionArea, problem.getDescription());
        }
    }

    public void setProblemDetails(String description, String inputFormat, String outputFormat,
                                  String constraints, String sampleInput, String sampleOutput) {
        if (description != null) {
            descriptionArea.setText(description);
            adjustTextAreaHeight(descriptionArea, description);
        }
        if (inputFormat != null) {
            inputFormatArea.setText(inputFormat);
            adjustTextAreaHeight(inputFormatArea, inputFormat);
        }
        if (outputFormat != null) {
            outputFormatArea.setText(outputFormat);
            adjustTextAreaHeight(outputFormatArea, outputFormat);
        }
        if (constraints != null) {
            constraintsArea.setText(constraints);
            adjustTextAreaHeight(constraintsArea, constraints);
        }

        sampleCasesContainer.getChildren().clear();
        if (sampleInput != null || sampleOutput != null) {
            addSampleTestCase("Ví dụ 1", sampleInput != null ? sampleInput : "",
                                         sampleOutput != null ? sampleOutput : "");
        }
    }

    /**
     * Thêm một cặp ví dụ mẫu Input / Output.
     * Cung cấp TextArea có thể bôi đen copy VÀ nút "Sao chép" trực tiếp vào Clipboard!
     */
    public void addSampleTestCase(String title, String input, String output) {
        VBox caseBox = new VBox(6);
        caseBox.setPadding(new Insets(4, 0, 8, 0));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-main; -fx-font-size: 12.5px;");

        // Input header + Copy button
        HBox inHeader = new HBox(8);
        inHeader.setAlignment(Pos.CENTER_LEFT);
        Label inLabel = new Label("Input:");
        inLabel.getStyleClass().add("meta-item");
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Button copyInBtn = createCopyButton(input.trim());
        inHeader.getChildren().addAll(inLabel, spacer1, copyInBtn);

        // Input content (Selectable TextArea)
        TextArea inArea = new TextArea(input.trim());
        inArea.setEditable(false);
        inArea.getStyleClass().addAll("text-area", "sample-box");
        inArea.setPrefRowCount(Math.max(1, Math.min(6, input.trim().split("\n").length)));

        // Output header + Copy button
        HBox outHeader = new HBox(8);
        outHeader.setAlignment(Pos.CENTER_LEFT);
        Label outLabel = new Label("Output:");
        outLabel.getStyleClass().add("meta-item");
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Button copyOutBtn = createCopyButton(output.trim());
        outHeader.getChildren().addAll(outLabel, spacer2, copyOutBtn);

        // Output content (Selectable TextArea)
        TextArea outArea = new TextArea(output.trim());
        outArea.setEditable(false);
        outArea.getStyleClass().addAll("text-area", "sample-box");
        outArea.setPrefRowCount(Math.max(1, Math.min(6, output.trim().split("\n").length)));

        caseBox.getChildren().addAll(titleLabel, inHeader, inArea, outHeader, outArea);
        sampleCasesContainer.getChildren().add(caseBox);

        // Tự động điền input mẫu vào tab Test Cases nếu tab này đang trống
        if (customInputArea.getText().trim().isEmpty() && !input.trim().isEmpty()) {
            customInputArea.setText(input.trim());
        }
    }

    /**
     * Tạo nút sao chép text vào hệ thống Clipboard kèm phản hồi trực quan.
     */
    private Button createCopyButton(String contentToCopy) {
        Button btn = new Button("📋 Sao chép");
        btn.getStyleClass().add("btn-copy");
        btn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(contentToCopy);
            clipboard.setContent(content);

            btn.setText("✔ Đã chép!");
            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
            pause.setOnFinished(ev -> btn.setText("📋 Sao chép"));
            pause.play();
        });
        return btn;
    }

    public String getCode() {
        return codeEditorArea.getText();
    }

    public void setCode(String code) {
        codeEditorArea.setText(code);
    }

    public String getSelectedLanguage() {
        String selected = languageComboBox.getSelectionModel().getSelectedItem();
        if (selected == null) return "cpp";
        if (selected.startsWith("Java")) return "java";
        if (selected.startsWith("Python")) return "python";
        return "cpp";
    }

    public void setSelectedLanguage(String languageName) {
        for (String item : languageComboBox.getItems()) {
            if (item.toLowerCase().contains(languageName.toLowerCase())) {
                languageComboBox.getSelectionModel().select(item);
                break;
            }
        }
    }

    public String getCustomInput() {
        return customInputArea.getText();
    }

    public void setCustomInput(String input) {
        customInputArea.setText(input);
    }

    public void showExecutionResult(String output, String verdict, boolean isError) {
        consoleOutputArea.setText(output != null ? output : "");

        resultStatusBadge.getStyleClass().removeAll("status-badge-ac", "status-badge-wa", "status-badge-neutral");
        if (verdict != null) {
            resultStatusBadge.setText(verdict);
            if (verdict.equalsIgnoreCase("ACCEPTED") || verdict.equalsIgnoreCase("AC")) {
                resultStatusBadge.getStyleClass().add("status-badge-ac");
            } else if (isError || verdict.contains("ERROR") || verdict.contains("WRONG") || verdict.equalsIgnoreCase("WA")) {
                resultStatusBadge.getStyleClass().add("status-badge-wa");
            } else {
                resultStatusBadge.getStyleClass().add("status-badge-neutral");
            }
        }

        consoleTabPane.getSelectionModel().select(resultTab);
    }

    public void setBusy(boolean isBusy, String waitingMessage) {
        runButton.setDisable(isBusy);
        submitButton.setDisable(isBusy);
        if (isBusy) {
            resultStatusBadge.getStyleClass().removeAll("status-badge-ac", "status-badge-wa");
            resultStatusBadge.getStyleClass().add("status-badge-neutral");
            resultStatusBadge.setText(waitingMessage != null ? waitingMessage : "Đang xử lý...");
        }
    }

    public void setOnRunCode(Consumer<ProblemView> handler) {
        this.onRunCodeHandler = handler;
    }

    public void setOnSubmitCode(Consumer<ProblemView> handler) {
        this.onSubmitCodeHandler = handler;
    }

    public void setOnLanguageChange(Consumer<String> handler) {
        this.onLanguageChangeHandler = handler;
    }

    public void setOnBackToDashboard(Runnable handler) {
        this.onBackToDashboardHandler = handler;
    }

    private Label createSectionHeader(String title) {
        Label header = new Label(title);
        header.getStyleClass().add("section-header");
        return header;
    }

    public Problem getCurrentProblem() {
        return currentProblem;
    }

    public TextArea getCodeEditorArea() {
        return codeEditorArea;
    }

    public TextArea getCustomInputArea() {
        return customInputArea;
    }

    public TextArea getConsoleOutputArea() {
        return consoleOutputArea;
    }

    public Button getRunButton() {
        return runButton;
    }

    public Button getSubmitButton() {
        return submitButton;
    }

    public Button getBackButton() {
        return backButton;
    }

    public ComboBox<String> getLanguageComboBox() {
        return languageComboBox;
    }
}
