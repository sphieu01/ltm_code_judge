package com.grading.client.view;

import com.grading.client.model.Problem;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ProblemView — Giao diện nộp bài chuẩn Hệ thống thực hành ảo CNTT.
 *
 * Tối ưu hóa theo phản hồi:
 * Đề bài hiển thị trước, sinh viên tải tệp mã nguồn để nộp và lịch sử nằm cuối
 * trang dưới dạng bảng.
 */
public class ProblemView extends BorderPane {

    // ======================== TOP TOOLBAR ========================
    private Button backButton;
    private Label proctorBadge;
    private Label countdownTimerBadge;
    private Button completeAssessmentButton;
    private Button themeToggleBtn;
    private boolean choosingFile = false;

    // ======================== PROBLEM DETAILS (LEFT COLUMN)
    // ========================
    private Label problemTitleHeader;
    private Label oopInfoBanner;
    private Label descriptionLabel;
    private Label inputFormatLabel;
    private Label outputFormatLabel;
    private VBox sampleTableContainer;
    private Label metaConstraintsLabel;

    // ======================== HISTORY TABLE (BOTTOM) ========================
    private TableView<SubmissionHistoryRow> historyTable;
    private javafx.collections.ObservableList<SubmissionHistoryRow> historyRows;

    // ======================== SUBMISSION & FILE UPLOAD ========================
    private ComboBox<String> languageComboBox;
    private Label lockIndicatorLabel;
    private HBox uploadDropZone;
    private HBox selectedFileBox;
    private Label selectedFileLabel;
    private Button changeFileBtn;
    private Button removeFileBtn;

    private ProgressBar progressBar;
    private Button submitButton;

    // ======================== DATA & HANDLERS ========================
    private Problem currentProblem;
    private File selectedFile;
    private String fileCodeContent;
    private String activeSubmissionCode;
    private boolean isZipSubmission = false;
    private String allowedLanguagesSetting = null;
    private static File lastDirectory = null;

    private Consumer<ProblemView> onSubmitCodeHandler;
    private Consumer<String> onLanguageChangeHandler;
    private Runnable onBackToDashboardHandler;
    private Runnable onCompleteAssessmentHandler;

    private final List<SampleCaseData> sampleCases = new ArrayList<>();

    public ProblemView() {
        buildLayout();
        setupFileUploadAndDragDrop();
        setupEventBindings();
    }

    // =========================================================================
    // 1. XÂY DỰNG BỐ CỤC CHÍNH (TOP TOOLBAR + SCROLLABLE PAGE)
    // =========================================================================

    private void buildLayout() {
        getStyleClass().add("main-pane");

        // Top Toolbar
        setTop(buildTopToolbar());

        // Center: ScrollPane chứa toàn bộ nội dung
        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(18, 24, 24, 24));
        contentBox.setMaxWidth(1150);

        VBox centerWrapper = new VBox(contentBox);
        centerWrapper.setAlignment(Pos.TOP_CENTER);
        centerWrapper.setPadding(new Insets(0));

        // 1. Nội dung đề bài
        VBox problemSection = new VBox(10);

        problemTitleHeader = new Label("GẤP ĐÔI");
        problemTitleHeader.getStyleClass().add("problem-title-header");

        VBox problemContent = buildProblemSection();
        problemSection.getChildren().addAll(problemTitleHeader, problemContent);

        // 2. Thanh thao tác: Tải file lên & Nộp bài đi liền với nhau
        BorderPane submissionBar = buildSubmissionBar();

        // 3. Lịch sử nộp bài ở cuối trang
        VBox historySection = buildHistorySection();

        contentBox.getChildren().addAll(problemSection, submissionBar, historySection);

        ScrollPane scrollPane = new ScrollPane(centerWrapper);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        setCenter(scrollPane);

        // Footer đã được gỡ bỏ hoàn toàn theo yêu cầu
        setBottom(null);
    }

    // =========================================================================
    // 2. TOP TOOLBAR
    // =========================================================================

    private HBox buildTopToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.getStyleClass().add("top-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 20, 8, 20));

        backButton = new Button("⬅ Danh sách bài");
        backButton.getStyleClass().addAll("button", "btn-secondary");

        proctorBadge = new Label("🛡️ Giám sát: Bình thường (Cho phép IDE)");
        proctorBadge.setTooltip(new Tooltip(
                "Hệ thống cho phép chuyển sang IDE ngoài (VS Code, CLion, Dev-C++...) để code.\nNghiêm cấm mở ChatGPT, Gemini hoặc các công cụ AI trợ giúp."));
        proctorBadge.setStyle(
                "-fx-background-color: rgba(34, 197, 94, 0.15); -fx-text-fill: #16a34a; -fx-padding: 4 12; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: rgba(34, 197, 94, 0.3); -fx-border-radius: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Đồng hồ đếm ngược lúc thi ở góc trên (đơn giản, tinh tế)
        countdownTimerBadge = new Label("");
        countdownTimerBadge.setStyle(
                "-fx-background-color: rgba(14, 165, 233, 0.12); -fx-text-fill: #0284c7; -fx-padding: 4 14; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: rgba(14, 165, 233, 0.35); -fx-border-radius: 12px;");
        countdownTimerBadge.setVisible(false);
        countdownTimerBadge.setManaged(false);

        completeAssessmentButton = new Button("Kết thúc bài thi");
        completeAssessmentButton.setId("complete-assessment-button");
        completeAssessmentButton.getStyleClass().addAll("button", "btn-primary");
        completeAssessmentButton.setVisible(false);
        completeAssessmentButton.setManaged(false);
        completeAssessmentButton.setOnAction(e -> {
            if (onCompleteAssessmentHandler != null)
                onCompleteAssessmentHandler.run();
        });

        themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setStyle("-fx-font-size: 14px; -fx-padding: 4 10 4 10;");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        toolbar.getChildren().addAll(backButton, proctorBadge, spacer, countdownTimerBadge,
                completeAssessmentButton, themeToggleBtn);
        return toolbar;
    }

    // =========================================================================
    // 3. KHUNG ĐỀ BÀI (KHÔNG CẦN CHO VÀO BOX XÁM)
    // =========================================================================

    private VBox buildProblemSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(4, 0, 8, 0));

        // Banner thông báo dành cho bài tập Java OOP
        oopInfoBanner = new Label("📦 DẠNG BÀI: LẬP TRÌNH HƯỚNG ĐỐI TƯỢNG (JAVA OOP)\n"
                + "• Sinh viên nộp gói .zip (hoặc file .java) chứa các class và Main.java điều phối I/O.\n"
                + "• Hệ thống sẽ kiểm tra nghiêm ngặt cấu trúc OOP (tính đóng gói private, kế thừa, phương thức) trước. "
                + "Nếu cấu trúc đạt 100% mới tiến hành chấm bộ test case qua Main.java.");
        oopInfoBanner.setWrapText(true);
        oopInfoBanner.setStyle(
                "-fx-background-color: rgba(0, 122, 204, 0.12); -fx-text-fill: -accent; -fx-padding: 10 14; -fx-background-radius: 6px; -fx-border-color: -accent; -fx-border-radius: 6px; -fx-font-size: 13px; -fx-line-spacing: 3px; -fx-font-weight: bold;");
        oopInfoBanner.setVisible(false);
        oopInfoBanner.setManaged(false);

        // Nội dung mô tả đề bài hiển thị trực tiếp dạng văn bản tự nhiên
        descriptionLabel = new Label("Mô tả bài toán đang được tải...");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: -text-primary; -fx-line-spacing: 4px;");

        // Input
        Label inTitle = new Label("Input");
        inTitle.setStyle(
                "-fx-font-weight: bold; -fx-text-fill: -text-primary; -fx-font-size: 15px; -fx-padding: 6 0 0 0;");
        inputFormatLabel = new Label("Đang tải định dạng đầu vào...");
        inputFormatLabel.setWrapText(true);
        inputFormatLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 15px;");

        // Output
        Label outTitle = new Label("Output");
        outTitle.setStyle(
                "-fx-font-weight: bold; -fx-text-fill: -text-primary; -fx-font-size: 15px; -fx-padding: 6 0 0 0;");
        outputFormatLabel = new Label("Đang tải định dạng đầu ra...");
        outputFormatLabel.setWrapText(true);
        outputFormatLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 15px;");

        // Bảng ví dụ mẫu
        Label sampleTitle = new Label("Ví dụ");
        sampleTitle.setStyle(
                "-fx-font-weight: bold; -fx-text-fill: -text-primary; -fx-font-size: 15px; -fx-padding: 6 0 0 0;");
        sampleTableContainer = new VBox(6);

        // Giới hạn thời gian / bộ nhớ
        metaConstraintsLabel = new Label("Giới hạn thời gian: 1s  |  Giới hạn bộ nhớ: 65536Kb");
        metaConstraintsLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -text-secondary;");

        section.getChildren().addAll(
                oopInfoBanner,
                descriptionLabel,
                inTitle, inputFormatLabel,
                outTitle, outputFormatLabel,
                sampleTitle, sampleTableContainer);

        return section;
    }

    // =========================================================================
    // 4. LỊCH SỬ NỘP BÀI (BẢNG Ở CUỐI TRANG)
    // =========================================================================

    private VBox buildHistorySection() {
        VBox section = new VBox(10);
        Label title = new Label("Lịch sử nộp bài");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        historyRows = FXCollections.observableArrayList();
        historyTable = new TableView<>(historyRows);
        historyTable.getStyleClass().add("table-view");
        historyTable.setPlaceholder(new Label("Chưa có lượt nộp bài nào."));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SubmissionHistoryRow, String> timeCol = new TableColumn<>("Thời gian nộp");
        timeCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().timestamp()));
        timeCol.setMinWidth(165);
        timeCol.setPrefWidth(210);
        TableColumn<SubmissionHistoryRow, String> verdictCol = new TableColumn<>("Kết quả");
        verdictCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        com.grading.client.model.Submission.formatVerdict(data.getValue().verdict())));
        verdictCol.setStyle("-fx-alignment: CENTER;");
        verdictCol.setMinWidth(170);
        verdictCol.setPrefWidth(190);
        verdictCol.setMaxWidth(225);
        verdictCol.setCellFactory(column -> new TableCell<SubmissionHistoryRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label pill = new Label(item);
                pill.setMinWidth(Region.USE_PREF_SIZE);
                applyPillStyle(pill, item);
                setGraphic(pill);
                setText(null);
            }
        });
        TableColumn<SubmissionHistoryRow, Void> viewCodeCol = new TableColumn<>("Thao tác");
        viewCodeCol.setStyle("-fx-alignment: CENTER;");
        viewCodeCol.setMinWidth(125);
        viewCodeCol.setPrefWidth(145);
        viewCodeCol.setMaxWidth(165);
        viewCodeCol.setCellFactory(column -> new TableCell<SubmissionHistoryRow, Void>() {
            private final Button viewButton = new Button("Mở mã nguồn");
            {
                viewButton.getStyleClass().addAll("button", "btn-secondary");
                viewButton.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                viewButton.setOnAction(event -> {
                    SubmissionHistoryRow row = getTableView().getItems().get(getIndex());
                    showSubmittedCode(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewButton);
            }
        });
        historyTable.getColumns().addAll(timeCol, verdictCol, viewCodeCol);
        TableViewSupport.fitHeightToRows(historyTable, 4);
        section.getChildren().addAll(title, historyTable);
        return section;
    }

    public void clearHistory() {
        historyRows.clear();
    }

    public void appendSubmissionHistory(String title, String timestamp, String verdict, String sourceCode) {
        appendSubmissionHistory(title, timestamp, verdict, sourceCode, false);
    }

    public void appendSubmissionHistory(String title, String timestamp, String verdict, String sourceCode,
            boolean isZip) {
        historyRows.add(new SubmissionHistoryRow(title, formatTimestamp(timestamp), verdict, sourceCode, isZip));
    }

    public void addSubmissionHistory(String title, String timestamp, String verdict, String sourceCode) {
        addSubmissionHistory(title, timestamp, verdict, sourceCode, false);
    }

    public void addSubmissionHistory(String title, String timestamp, String verdict, String sourceCode, boolean isZip) {
        historyRows.add(0, new SubmissionHistoryRow(title, formatTimestamp(timestamp), verdict, sourceCode, isZip));
    }

    private void showSubmittedCode(SubmissionHistoryRow row) {
        Stage owner = getScene() != null ? (Stage) getScene().getWindow() : null;
        SourceCodeViewerDialog viewer = new SourceCodeViewerDialog(
                owner,
                row.title(),
                row.timestamp(),
                row.sourceCode(),
                row.isZip());
        viewer.showAndWait();
    }

    private String formatTimestamp(String timestamp) {
        return timestamp != null && timestamp.length() > 19 ? timestamp.substring(0, 19)
                : (timestamp != null ? timestamp : "");
    }

    private String verdictToBadge(String v) {
        return com.grading.client.model.Submission.formatVerdict(v);
    }

    // =========================================================================
    // 5. THANH THAO TÁC (TẢI FILE LÊN & NỘP BÀI ĐI LIỀN VỚI NHAU, CÙNG MÀU XANH)
    // =========================================================================

    private BorderPane buildSubmissionBar() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(6, 0, 6, 0));

        // Bên trái chỉ hiển thị giới hạn chấm bài.
        HBox leftBox = new HBox(10);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.getChildren().add(metaConstraintsLabel);
        pane.setLeft(leftBox);
        pane.setAlignment(leftBox, Pos.TOP_LEFT);

        final double BTN_WIDTH = 200;
        final double BTN_HEIGHT = 36;

        // Khối thao tác bên phải: Tải file lên và Nộp bài đi liền với nhau
        VBox rightActions = new VBox(8);
        rightActions.setPrefWidth(480);
        rightActions.setAlignment(Pos.CENTER_RIGHT);

        HBox compilerBox = new HBox(8);
        compilerBox.setAlignment(Pos.CENTER_RIGHT);
        Label compilerLabel = new Label("Trình biên dịch:");
        compilerLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-size: 14px;");
        compilerLabel.setMinWidth(105);
        languageComboBox = new ComboBox<>(FXCollections.observableArrayList("C", "C++", "Java", "Python 3"));
        languageComboBox.getSelectionModel().select("C");
        languageComboBox.setPrefWidth(130);
        lockIndicatorLabel = new Label();
        lockIndicatorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #10b981; -fx-font-weight: bold;");
        lockIndicatorLabel.setVisible(false);
        lockIndicatorLabel.setManaged(false);
        compilerBox.getChildren().addAll(compilerLabel, languageComboBox, lockIndicatorLabel);

        // 1. Nút Tải file lên (kích thước chuẩn 200x36)
        uploadDropZone = new HBox(8);
        uploadDropZone.getStyleClass().add("upload-drop-zone");
        uploadDropZone.setAlignment(Pos.CENTER);
        uploadDropZone.setPrefSize(BTN_WIDTH, BTN_HEIGHT);
        uploadDropZone.setMinSize(BTN_WIDTH, BTN_HEIGHT);
        uploadDropZone.setMaxSize(BTN_WIDTH, BTN_HEIGHT);

        Label uploadText = new Label("Tải file lên");
        uploadText.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-primary; -fx-font-weight: normal;");

        uploadDropZone.getChildren().addAll(uploadText);

        // Chọn trình biên dịch nằm cùng hàng, bên trái nút tải file.
        HBox uploadRow = new HBox(10, compilerBox, uploadDropZone);
        uploadRow.setAlignment(Pos.CENTER_RIGHT);

        // 1.5. Hiển thị file đã chọn (nếu có)
        selectedFileBox = new HBox(6);
        selectedFileBox.setAlignment(Pos.CENTER_LEFT);
        selectedFileBox.setPrefWidth(BTN_WIDTH);
        selectedFileBox.setMaxWidth(BTN_WIDTH);
        selectedFileBox.setVisible(false);
        selectedFileBox.setManaged(false);

        selectedFileLabel = new Label("📄 Chưa chọn file");
        selectedFileLabel.getStyleClass().add("upload-file-pill");
        HBox.setHgrow(selectedFileLabel, Priority.ALWAYS);

        changeFileBtn = new Button("Đổi");
        changeFileBtn.getStyleClass().addAll("button", "btn-secondary");
        changeFileBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6 2 6;");

        removeFileBtn = new Button("✕");
        removeFileBtn.getStyleClass().addAll("button", "btn-secondary");
        removeFileBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-text-fill: -status-wa;");

        selectedFileBox.getChildren().addAll(selectedFileLabel, changeFileBtn, removeFileBtn);

        // 2. Thanh tiến độ khi đang chấm
        progressBar = new ProgressBar(0.0);
        progressBar.getStyleClass().add("progress-bar");
        progressBar.setPrefWidth(BTN_WIDTH);
        progressBar.setMaxWidth(BTN_WIDTH);
        progressBar.setPrefHeight(12);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        // 3. Nút Nộp bài (kích thước chuẩn 200x36, cùng kiểu với Nộp bài nhanh)
        submitButton = new Button("Nộp bài");
        submitButton.getStyleClass().add("btn-paste-submit");
        submitButton.setPrefSize(BTN_WIDTH, BTN_HEIGHT);
        submitButton.setMinSize(BTN_WIDTH, BTN_HEIGHT);
        submitButton.setMaxSize(BTN_WIDTH, BTN_HEIGHT);

        rightActions.getChildren().addAll(uploadRow, selectedFileBox, progressBar, submitButton);
        pane.setRight(rightActions);

        return pane;
    }

    // =========================================================================
    // 7. TẢI FILE LÊN (FILE CHOOSER & DRAG AND DROP)
    // =========================================================================

    private void setupFileUploadAndDragDrop() {
        uploadDropZone.setOnMouseClicked(e -> openFileChooser());
        changeFileBtn.setOnAction(e -> openFileChooser());
        removeFileBtn.setOnAction(e -> clearSelectedFile());

        uploadDropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != uploadDropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                uploadDropZone.getStyleClass().add("upload-drop-zone-dragover");
            }
            event.consume();
        });

        uploadDropZone.setOnDragExited(event -> {
            uploadDropZone.getStyleClass().remove("upload-drop-zone-dragover");
            event.consume();
        });

        uploadDropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    loadSelectedFile(files.get(0));
                    success = true;
                }
            }
            event.setDropCompleted(success);
            uploadDropZone.getStyleClass().remove("upload-drop-zone-dragover");
            event.consume();
        });
    }

    private void openFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file mã nguồn hoặc gói ZIP để nộp bài");

        if (lastDirectory != null && lastDirectory.exists() && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }

        if (currentProblem != null && currentProblem.isJavaOop()) {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Java OOP: Gói ZIP hoặc file Java (*.zip, *.java)", "*.zip",
                            "*.java"),
                    new FileChooser.ExtensionFilter("Gói nén ZIP (*.zip)", "*.zip"),
                    new FileChooser.ExtensionFilter("Java Files (*.java)", "*.java"),
                    new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
        } else if (languageComboBox != null && languageComboBox.isDisable()) {
            String selected = languageComboBox.getValue();
            if ("Java".equalsIgnoreCase(selected)) {
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Java: File .java hoặc gói .zip (*.java, *.zip)", "*.java",
                                "*.zip"),
                        new FileChooser.ExtensionFilter("Java Source (*.java)", "*.java"),
                        new FileChooser.ExtensionFilter("Gói nén ZIP (*.zip)", "*.zip"),
                        new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
            } else if ("Python 3".equalsIgnoreCase(selected)) {
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Python: File .py (*.py)", "*.py"),
                        new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
            } else if ("C++".equalsIgnoreCase(selected) || "C".equalsIgnoreCase(selected)) {
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("C/C++: File .cpp, .c (*.cpp, *.c, *.cc)", "*.cpp", "*.c",
                                "*.cc"),
                        new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
            } else {
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Tất cả mã nguồn (*.cpp, *.c, *.java, *.py, *.zip)", "*.cpp",
                                "*.c", "*.java",
                                "*.py", "*.zip", "*.txt"),
                        new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
            }
        } else {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Tất cả mã nguồn (*.cpp, *.c, *.java, *.py, *.zip)", "*.cpp", "*.c",
                            "*.java",
                            "*.py", "*.zip", "*.txt"),
                    new FileChooser.ExtensionFilter("C / C++ Files (*.cpp, *.c)", "*.cpp", "*.c"),
                    new FileChooser.ExtensionFilter("Java Files (*.java)", "*.java"),
                    new FileChooser.ExtensionFilter("Python Files (*.py)", "*.py"),
                    new FileChooser.ExtensionFilter("Gói nén ZIP (*.zip)", "*.zip"),
                    new FileChooser.ExtensionFilter("Mọi file (*.*)", "*.*"));
        }

        choosingFile = true;
        try {
            File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
            if (file != null) {
                lastDirectory = file.getParentFile();
                loadSelectedFile(file);
            }
        } finally {
            choosingFile = false;
        }
    }

    public void loadSelectedFile(File file) {
        if (file == null || !file.exists())
            return;
        this.selectedFile = file;

        try {
            String fileName = file.getName().toLowerCase();
            long sizeKb = Math.max(1, file.length() / 1024);

            if (fileName.endsWith(".zip")) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                this.fileCodeContent = java.util.Base64.getEncoder().encodeToString(bytes);
                this.activeSubmissionCode = this.fileCodeContent;
                this.isZipSubmission = true;
                selectLanguageByName("Java");
                selectedFileLabel.setText(String.format("📦 Gói ZIP: %s (%d KB)", file.getName(), sizeKb));
            } else {
                this.isZipSubmission = false;
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                this.fileCodeContent = content;
                this.activeSubmissionCode = content;

                // Tự động nhận diện ngôn ngữ theo phần mở rộng file (chỉ khi chưa bị khóa theo
                // lớp học)
                if (!languageComboBox.isDisable()) {
                    if (fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx")) {
                        selectLanguageByName("C++");
                    } else if (fileName.endsWith(".c")) {
                        selectLanguageByName("C");
                    } else if (fileName.endsWith(".java")) {
                        selectLanguageByName("Java");
                    } else if (fileName.endsWith(".py")) {
                        selectLanguageByName("Python 3");
                    }
                    selectedFileLabel.setText(String.format("📄 %s (%d KB)", file.getName(), sizeKb));
                } else {
                    selectedFileLabel.setText(String.format("📄 %s (%d KB) — [Chấm bằng: %s]", file.getName(), sizeKb,
                            languageComboBox.getValue()));
                }
            }

            selectedFileBox.setVisible(true);
            selectedFileBox.setManaged(true);
            submitButton.setDisable(false);

        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi đọc file");
            alert.setHeaderText(null);
            alert.setContentText("Không thể đọc file: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private void selectLanguageByName(String name) {
        if (languageComboBox.isDisable()) {
            return; // Ngôn ngữ đã bị khóa theo lớp học, không tự ý chuyển
        }
        for (String item : languageComboBox.getItems()) {
            if (item.equalsIgnoreCase(name) || item.startsWith(name)) {
                languageComboBox.getSelectionModel().select(item);
                break;
            }
        }
    }

    private void clearSelectedFile() {
        this.selectedFile = null;
        this.fileCodeContent = null;
        this.activeSubmissionCode = null;
        this.isZipSubmission = false;
        selectedFileBox.setVisible(false);
        selectedFileBox.setManaged(false);
    }

    // =========================================================================
    // 8. CẤU HÌNH EVENT BINDINGS & PHÍM TẮT
    // =========================================================================

    private void setupEventBindings() {
        backButton.setOnAction(e -> {
            if (onBackToDashboardHandler != null)
                onBackToDashboardHandler.run();
        });

        // Nút Nộp bài: nộp file mã nguồn đã chọn
        submitButton.setOnAction(e -> {
            if (fileCodeContent == null || fileCodeContent.trim().isEmpty()) {
                showWarning("Vui lòng chọn hoặc kéo thả file mã nguồn trước khi nhấn 'Nộp bài'.");
                return;
            }
            this.activeSubmissionCode = fileCodeContent;
            triggerSubmit();
        });

        languageComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (onLanguageChangeHandler != null && newVal != null) {
                onLanguageChangeHandler.accept(newVal);
            }
        });
    }

    private void triggerSubmit() {
        String source = getCode();
        String code = source == null ? "" : source.trim();
        if (code.isEmpty()) {
            showWarning("Vui lòng tải file mã nguồn lên hoặc dán code vào ô trước khi nộp bài.");
            return;
        }
        if (onSubmitCodeHandler != null) {
            onSubmitCodeHandler.accept(this);
        }
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // =========================================================================
    // 9. LOAD BÀI TẬP & SAMPLE CASES
    // =========================================================================

    public void loadProblem(Problem problem) {
        refreshProblemMetadata(problem);
        if (problem == null) return;
        clearSelectedFile();
        updateProctorStatus(0, "INFO", null);
        clearHistory();
    }

    /** Apply a teacher's edit without discarding the student's selected source file. */
    public void refreshProblemMetadata(Problem problem) {
        String selectedLanguage = languageComboBox.getValue();
        this.currentProblem = problem;
        if (problem == null)
            return;

        problemTitleHeader.setText(problem.getTitle().toUpperCase());

        metaConstraintsLabel.setText(String.format("Giới hạn thời gian: %s  |  Giới hạn bộ nhớ: %s",
                problem.getTimeLimitFormatted(), problem.getMemoryLimitFormatted()));

        if (problem.getDescription() != null) {
            descriptionLabel.setText(problem.getDescription());
        }

        boolean isOop = problem.isJavaOop();
        if (oopInfoBanner != null) {
            oopInfoBanner.setVisible(isOop);
            oopInfoBanner.setManaged(isOop);
        }

        if (problem.getAllowedLanguages() != null && !problem.getAllowedLanguages().isBlank()) {
            this.allowedLanguagesSetting = problem.getAllowedLanguages();
        }

        updateLanguageOptions();
        if (selectedLanguage != null && languageComboBox.getItems().contains(selectedLanguage)) {
            languageComboBox.getSelectionModel().select(selectedLanguage);
        }
    }

    public void setAllowedLanguages(String allowedLanguages) {
        this.allowedLanguagesSetting = allowedLanguages;
        updateLanguageOptions();
    }

    private void updateLanguageOptions() {
        if (currentProblem != null && currentProblem.isJavaOop()) {
            languageComboBox.setItems(FXCollections.observableArrayList("Java"));
            languageComboBox.getSelectionModel().select("Java");
            languageComboBox.setDisable(true);
            if (lockIndicatorLabel != null) {
                lockIndicatorLabel.setText("🔒 Khóa Java OOP");
                lockIndicatorLabel.setVisible(true);
                lockIndicatorLabel.setManaged(true);
            }
            return;
        }

        List<String> defaultLangs = List.of("C", "C++", "Java", "Python 3");
        if (allowedLanguagesSetting != null && !allowedLanguagesSetting.isBlank()) {
            List<String> filtered = new ArrayList<>();
            String[] tokens = allowedLanguagesSetting.split(",");
            for (String t : tokens) {
                String trimmed = t.trim().toLowerCase();
                if (trimmed.equals("c") && !filtered.contains("C"))
                    filtered.add("C");
                else if (trimmed.equals("cpp") && !filtered.contains("C++"))
                    filtered.add("C++");
                else if (trimmed.equals("java") && !filtered.contains("Java"))
                    filtered.add("Java");
                else if (trimmed.equals("python") && !filtered.contains("Python 3"))
                    filtered.add("Python 3");
            }
            if (!filtered.isEmpty()) {
                languageComboBox.setItems(FXCollections.observableArrayList(filtered));
                languageComboBox.getSelectionModel().select(0);
                boolean singleLanguage = (filtered.size() == 1);
                languageComboBox.setDisable(singleLanguage);
                if (lockIndicatorLabel != null) {
                    if (singleLanguage) {
                        lockIndicatorLabel.setText("🔒 Khóa theo lớp: " + filtered.get(0));
                        lockIndicatorLabel.setVisible(true);
                        lockIndicatorLabel.setManaged(true);
                    } else {
                        lockIndicatorLabel.setText("");
                        lockIndicatorLabel.setVisible(false);
                        lockIndicatorLabel.setManaged(false);
                    }
                }
                return;
            }
        }

        languageComboBox.setItems(FXCollections.observableArrayList(defaultLangs));
        languageComboBox.getSelectionModel().select("C");
        languageComboBox.setDisable(false);
        if (lockIndicatorLabel != null) {
            lockIndicatorLabel.setVisible(false);
            lockIndicatorLabel.setManaged(false);
        }
    }

    public void setProblemDetails(String description, String inputFormat, String outputFormat,
            String constraints, String sampleInput, String sampleOutput) {
        if (description != null) {
            descriptionLabel.setText(description);
        }
        if (inputFormat != null) {
            inputFormatLabel.setText(inputFormat);
        }
        if (outputFormat != null) {
            outputFormatLabel.setText(outputFormat);
        }
        if (constraints != null) {
            metaConstraintsLabel.setText(constraints);
        }

        sampleCases.clear();
        sampleTableContainer.getChildren().clear();

        if (sampleInput != null || sampleOutput != null) {
            addSampleTestCase("Ví dụ", sampleInput != null ? sampleInput : "",
                    sampleOutput != null ? sampleOutput : "");
        }
    }

    public void addSampleTestCase(String title, String input, String output) {
        sampleCases.add(new SampleCaseData(title, input, output));
        renderSampleTable();
    }

    private void renderSampleTable() {
        sampleTableContainer.getChildren().clear();

        for (int i = 0; i < sampleCases.size(); i++) {
            SampleCaseData sc = sampleCases.get(i);

            VBox box = new VBox();
            box.getStyleClass().add("sample-table-container");

            // Header row: Input | Output
            GridPane grid = new GridPane();
            grid.setHgap(0);
            grid.setVgap(0);

            ColumnConstraints col1 = new ColumnConstraints();
            col1.setPercentWidth(50);
            ColumnConstraints col2 = new ColumnConstraints();
            col2.setPercentWidth(50);
            grid.getColumnConstraints().addAll(col1, col2);

            Label inHeader = new Label("Input");
            inHeader.getStyleClass().add("sample-table-header-cell");
            inHeader.setMaxWidth(Double.MAX_VALUE);
            inHeader.setStyle(
                    "-fx-border-color: transparent -border-subtle transparent transparent; -fx-border-width: 0 1 0 0;");

            Label outHeader = new Label("Output");
            outHeader.getStyleClass().add("sample-table-header-cell");
            outHeader.setMaxWidth(Double.MAX_VALUE);

            grid.add(inHeader, 0, 0);
            grid.add(outHeader, 1, 0);

            // Data row: Input content + Copy | Output content + Copy
            HBox inCell = new HBox(8);
            inCell.getStyleClass().add("sample-table-data-cell");
            inCell.setAlignment(Pos.CENTER_LEFT);
            inCell.setStyle(
                    "-fx-border-color: transparent -border-subtle transparent transparent; -fx-border-width: 0 1 0 0;");
            Label inData = new Label(sc.input.trim());
            inData.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-text-fill: -text-primary;");
            Region sp1 = new Region();
            HBox.setHgrow(sp1, Priority.ALWAYS);
            Button copyIn = createCopyButton(sc.input.trim());
            inCell.getChildren().addAll(inData, sp1, copyIn);

            HBox outCell = new HBox(8);
            outCell.getStyleClass().add("sample-table-data-cell");
            outCell.setAlignment(Pos.CENTER_LEFT);
            Label outData = new Label(sc.output.trim());
            outData.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-text-fill: -text-primary;");
            Region sp2 = new Region();
            HBox.setHgrow(sp2, Priority.ALWAYS);
            Button copyOut = createCopyButton(sc.output.trim());
            outCell.getChildren().addAll(outData, sp2, copyOut);

            grid.add(inCell, 0, 1);
            grid.add(outCell, 1, 1);

            box.getChildren().add(grid);
            sampleTableContainer.getChildren().add(box);
        }
    }

    private Button createCopyButton(String contentToCopy) {
        Button btn = new Button("Copy");
        btn.getStyleClass().add("btn-copy");
        btn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(contentToCopy);
            clipboard.setContent(content);

            btn.setText("Copied");
            PauseTransition pause = new PauseTransition(Duration.seconds(1.2));
            pause.setOnFinished(ev -> btn.setText("Copy"));
            pause.play();
        });
        return btn;
    }

    // =========================================================================
    // 10. XỬ LÝ VÀ HIỂN THỊ KẾT QUẢ CHẤM (JUDGE RESULT)
    // =========================================================================

    public void showJudgingResult(String verdict, int score, int passedTests, int totalTests,
            long timeMs, long memKb, String compileError) {
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String probTitle = (currentProblem != null) ? currentProblem.getTitle() : "BÀI TẬP";

        addSubmissionHistory(probTitle, nowStr, verdict, getCode());

        // Nếu có lỗi biên dịch, hiển thị popup thông báo chi tiết
        if (compileError != null && !compileError.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (compileError.contains("[OOP_VIOLATION]")) {
                alert.setTitle("Lỗi vi phạm cấu trúc OOP");
                alert.setHeaderText(
                        "❌ KHÔNG ĐẠT CẤU TRÚC HƯỚNG ĐỐI TƯỢNG (CE)\n(Bài làm không được đưa vào chạy test case)");
            } else {
                alert.setTitle("Lỗi biên dịch");
                alert.setHeaderText("Biên dịch thất bại: " + verdict);
            }
            TextArea area = new TextArea(compileError);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(12);
            area.setPrefWidth(600);
            area.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
            alert.getDialogPane().setContent(area);
            alert.show();
        }
    }

    public void setBusy(boolean isBusy, String waitingMessage) {
        submitButton.setDisable(isBusy);
        uploadDropZone.setDisable(isBusy);

        if (isBusy) {
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        } else {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            progressBar.setProgress(0.0);
        }
    }

    public void showExecutionResult(String output, String verdict, boolean isError) {
        if (isError && output != null && !output.trim().isEmpty()) {
            showWarning(output);
        }
    }

    private void applyPillStyle(Label pill, String verdict) {
        if (pill == null)
            return;
        pill.getStyleClass().removeAll("pill-ac", "pill-wa", "pill-tle", "pill-ce", "pill-running", "pill-neutral");

        if (verdict == null || verdict.equals("—") || verdict.equals("--")) {
            pill.setText("Chưa nộp bài");
            pill.getStyleClass().add("pill-neutral");
            return;
        }

        String formatted = com.grading.client.model.Submission.formatVerdict(verdict);
        pill.setText(formatted);
        pill.setMinWidth(Region.USE_PREF_SIZE);

        String v = verdict.toUpperCase();
        if (v.equals("ACCEPTED") || v.equals("AC") || v.equals("PASSED")) {
            pill.getStyleClass().add("pill-ac");
        } else if (v.contains("WRONG") || v.equals("WA") || v.equals("FAILED")) {
            pill.getStyleClass().add("pill-wa");
        } else if (v.contains("TLE") || v.contains("TIME_LIMIT")) {
            pill.getStyleClass().add("pill-tle");
        } else if (v.contains("CE") || v.contains("COMPILE")) {
            pill.getStyleClass().add("pill-ce");
        } else if (v.contains("PENDING") || v.contains("JUDGING") || v.contains("RUNNING")) {
            pill.getStyleClass().add("pill-running");
        } else {
            pill.getStyleClass().add("pill-neutral");
        }
    }

    // Getters & Handlers
    public String getCode() {
        return activeSubmissionCode != null ? activeSubmissionCode : fileCodeContent;
    }

    public void setCode(String code) {
        this.activeSubmissionCode = code;
    }

    public String getSelectedLanguage() {
        String sel = languageComboBox.getSelectionModel().getSelectedItem();
        if (sel == null)
            return "c";
        if (sel.startsWith("C++"))
            return "cpp";
        if (sel.startsWith("C"))
            return "c";
        if (sel.startsWith("Java"))
            return "java";
        if (sel.startsWith("Python"))
            return "python";
        return "c";
    }

    public Problem getCurrentProblem() {
        return currentProblem;
    }

    public boolean isZipSubmission() {
        return isZipSubmission;
    }

    public boolean isChoosingFile() {
        return choosingFile;
    }

    public void updateProctorStatus(int violationCount, String severity, String detail) {
        if (proctorBadge == null)
            return;
        if (violationCount <= 0) {
            proctorBadge.setText("Giám sát: Bình thường");
            proctorBadge.setStyle(
                    "-fx-background-color: rgba(34, 197, 94, 0.15); -fx-text-fill: #16a34a; -fx-padding: 4 12; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: rgba(34, 197, 94, 0.3); -fx-border-radius: 12px;");
        } else {
            proctorBadge.setText("🚨 Phát hiện AI (" + violationCount + " lần)");
            proctorBadge.setStyle(
                    "-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #dc2626; -fx-padding: 4 12; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: #ef4444; -fx-border-radius: 12px;");
        }
    }

    private Alert activeProctorAlert = null;

    public void showProctorWarningToast(String message, String severity) {
        String msg = (message != null && !message.isBlank()) ? message
                : "Nghiêm cấm mở công cụ AI trong quá trình làm bài.";
        if (activeProctorAlert != null && activeProctorAlert.isShowing()) {
            activeProctorAlert.setContentText(msg);
            if (activeProctorAlert.getDialogPane() != null && activeProctorAlert.getDialogPane().getScene() != null) {
                javafx.stage.Window win = activeProctorAlert.getDialogPane().getScene().getWindow();
                if (win instanceof javafx.stage.Stage stage) {
                    stage.toFront();
                    stage.requestFocus();
                }
            }
            return;
        }
        Alert alert = new Alert(
                "CRITICAL".equalsIgnoreCase(severity) ? Alert.AlertType.ERROR : Alert.AlertType.WARNING);
        alert.setTitle("Cảnh Báo Giám Sát Chống Gian Lận");
        alert.setHeaderText("Phát hiện mở công cụ AI!");
        alert.setContentText(msg);
        activeProctorAlert = alert;
        alert.setOnHidden(e -> {
            if (activeProctorAlert == alert) {
                activeProctorAlert = null;
            }
        });
        alert.show();
    }

    public void dismissProctorAlert() {
        if (activeProctorAlert != null && activeProctorAlert.isShowing()) {
            activeProctorAlert.close();
            activeProctorAlert = null;
        }
    }

    public void setCountdownTimerVisible(boolean visible) {
        if (countdownTimerBadge != null) {
            countdownTimerBadge.setVisible(visible);
            countdownTimerBadge.setManaged(visible);
        }
    }

    public void setAssessmentMode(boolean active) {
        completeAssessmentButton.setVisible(active);
        completeAssessmentButton.setManaged(active);
        setCountdownTimerVisible(active);
    }

    public void setOnCompleteAssessment(Runnable handler) {
        this.onCompleteAssessmentHandler = handler;
    }

    public void updateCountdownTimer(long remainingSeconds, String formattedTime) {
        if (countdownTimerBadge == null)
            return;
        if (remainingSeconds <= 0) {
            countdownTimerBadge.setText("Hết giờ!");
            countdownTimerBadge.setStyle(
                    "-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #dc2626; -fx-padding: 4 14; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #ef4444; -fx-border-radius: 12px;");
        } else if (remainingSeconds <= 300) {
            countdownTimerBadge.setText(formattedTime);
            countdownTimerBadge.setStyle(
                    "-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #dc2626; -fx-padding: 4 14; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: rgba(239, 68, 68, 0.4); -fx-border-radius: 12px;");
        } else {
            countdownTimerBadge.setText(formattedTime);
            countdownTimerBadge.setStyle(
                    "-fx-background-color: rgba(14, 165, 233, 0.12); -fx-text-fill: #0284c7; -fx-padding: 4 14; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: rgba(14, 165, 233, 0.35); -fx-border-radius: 12px;");
        }
    }

    public void showAiWarningAlert(String warningMessage) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Cảnh Báo Chống Gian Lận AI");
        alert.setHeaderText("Phát hiện mã nguồn có dấu hiệu do AI sinh ra!");
        alert.setContentText(warningMessage);
        alert.show();
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

    public void navigateBackToDashboard() {
        if (onBackToDashboardHandler != null)
            onBackToDashboardHandler.run();
    }

    // =========================================================================
    // INNER CLASS: SampleCaseData
    // =========================================================================

    private static class SampleCaseData {
        final String title;
        final String input;
        final String output;

        SampleCaseData(String title, String input, String output) {
            this.title = title;
            this.input = input;
            this.output = output;
        }
    }

    private record SubmissionHistoryRow(String title, String timestamp, String verdict, String sourceCode,
            boolean isZip) {
    }
}
