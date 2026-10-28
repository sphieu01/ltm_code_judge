package com.grading.client.view;

import com.grading.client.model.CreditClass;
import com.grading.client.model.AssessmentSchedule;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * DashboardView — Màn hình Danh sách bài tập dành cho Sinh viên.
 *
 * Tính năng theo yêu cầu & DESIGN.md:
 * 1. Header có Avatar cá nhân: bấm vào mở ProfilePopup chứa thông tin sinh viên
 * và nút Đăng xuất.
 * 2. ComboBox chọn Lớp tín chỉ (lớp học được giáo viên thêm vào) để làm bài
 * riêng biệt theo lớp.
 * 3. Bộ lọc tìm kiếm và độ khó.
 */
public class DashboardView extends BorderPane {

    private User currentUser;

    // Header Controls
    private Label brandLabel;
    private ComboBox<CreditClass> classFilterCombo;
    private ComboBox<String> difficultyFilterCombo;
    private TextField searchField;
    private Button historyBtn;
    private Button themeToggleBtn;
    private Button avatarBtn;
    private ProfilePopup profilePopup;
    private Label assessmentBanner;
    private VBox assessmentOverview;
    private TableView<AssessmentSummary> assessmentTable;
    private Button completeAssessmentButton;
    private Label assessmentCountdownBadge;
    private Label totalLabel;
    private Label problemLoadStatus;
    private boolean assessmentMode;
    private boolean assessmentLobby;

    // Table
    private TableView<Problem> problemTable;
    private ObservableList<Problem> problemList = FXCollections.observableArrayList();
    private FilteredList<Problem> filteredProblems;
    private Runnable updateProblemFilter;

    // Handlers
    private Consumer<Problem> onSelectProblemHandler;
    private Consumer<CreditClass> onSelectClassHandler;
    private Runnable onLogoutHandler;
    private Consumer<Integer> onOpenAssessmentHandler;
    private Runnable onCompleteAssessmentHandler;

    public record AssessmentSummary(int id, String title, String classCode, String status,
            String opensAt, String closesAt, int durationMinutes,
            int submittedCount, int problemCount, boolean started) {
    }

    public DashboardView() {
        buildLayout();
        setupFiltering();
    }

    private void buildLayout() {
        getStyleClass().add("main-pane");

        // 1. Unified Top Header (DESIGN.md Component 9)
        HBox topBar = new HBox(10);
        topBar.getStyleClass().add("top-toolbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 16, 8, 16));

        brandLabel = new Label("Auto Grader");
        brandLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        // Lựa chọn lớp học
        classFilterCombo = new ComboBox<>();
        classFilterCombo.setPromptText("Lớp tín chỉ...");
        classFilterCombo.setPrefWidth(270);

        // Lọc theo độ khó
        difficultyFilterCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Tất cả mức độ", "Mức 1", "Mức 2", "Mức 3", "Mức 4", "Mức 5", "Mức 6"));
        difficultyFilterCombo.getSelectionModel().select(0);
        difficultyFilterCombo.setPrefWidth(175);

        // Tìm kiếm theo tên bài
        searchField = new TextField();
        searchField.setPromptText("🔍 Tìm theo tên hoặc ID...");
        searchField.setPrefWidth(260);

        // Nút Lịch sử nộp bài
        historyBtn = new Button("📜 Lịch sử nộp bài");
        historyBtn.getStyleClass().addAll("button", "btn-secondary");
        historyBtn.setPrefWidth(180);
        historyBtn.setOnAction(e -> showHistoryDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Nút chuyển đổi Theme Sáng / Tối
        themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        // Đồng hồ đếm ngược lúc thi ở góc trên Dashboard
        assessmentCountdownBadge = new Label("");
        assessmentCountdownBadge.setStyle(
                "-fx-background-color: rgba(14, 165, 233, 0.12); -fx-text-fill: #0284c7; -fx-padding: 4 14; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: rgba(14, 165, 233, 0.35); -fx-border-radius: 12px;");
        assessmentCountdownBadge.setVisible(false);
        assessmentCountdownBadge.setManaged(false);

        completeAssessmentButton = new Button("Kết thúc bài thi");
        completeAssessmentButton.setId("complete-assessment-button");
        completeAssessmentButton.getStyleClass().addAll("button", "btn-primary");
        completeAssessmentButton.setVisible(false);
        completeAssessmentButton.setManaged(false);
        completeAssessmentButton.setOnAction(e -> {
            if (onCompleteAssessmentHandler != null)
                onCompleteAssessmentHandler.run();
        });

        // Avatar Button theo chuẩn DESIGN.md Component 5
        avatarBtn = new Button("SV");
        avatarBtn.getStyleClass().add("avatar-btn");
        avatarBtn.setOnAction(e -> showProfilePopup());

        topBar.getChildren().addAll(
                brandLabel,
                classFilterCombo,
                difficultyFilterCombo,
                searchField,
                historyBtn,
                spacer,
                assessmentCountdownBadge,
                completeAssessmentButton,
                themeToggleBtn,
                avatarBtn);
        setTop(topBar);

        // 2. Center Content: Table View toàn diện
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(12, 16, 12, 16));

        assessmentBanner = new Label();
        assessmentBanner.setWrapText(true);
        assessmentBanner.setMaxWidth(Double.MAX_VALUE);
        assessmentBanner.setStyle("-fx-padding: 12 16; -fx-font-size: 14px; -fx-font-weight: bold; " +
                "-fx-background-color: rgba(239, 68, 68, 0.14); -fx-border-color: #ef4444; -fx-border-radius: 6; -fx-background-radius: 6;");
        assessmentBanner.setVisible(false);
        assessmentBanner.setManaged(false);

        assessmentOverview = new VBox(8);
        assessmentOverview.setId("assessment-overview");
        assessmentOverview.setPadding(new Insets(12));
        assessmentOverview.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; " +
                "-fx-border-radius: 7; -fx-background-radius: 7;");
        Label assessmentTitle = new Label("Phòng chờ bài kiểm tra");
        assessmentTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label assessmentDescription = new Label(
                "Bài kiểm tra sắp bắt đầu. Khi đồng hồ đếm ngược kết thúc, bấm “Làm bài ngay” để vào làm và nộp bài.");
        assessmentDescription.setWrapText(true);
        assessmentDescription.getStyleClass().add("meta-item");
        assessmentTable = buildAssessmentTable();
        assessmentTable.setMinHeight(200);
        VBox.setVgrow(assessmentTable, Priority.ALWAYS);
        VBox.setVgrow(assessmentOverview, Priority.ALWAYS);
        assessmentOverview.getChildren().addAll(assessmentTitle, assessmentDescription, assessmentTable);
        assessmentOverview.setVisible(false);
        assessmentOverview.setManaged(false);

        problemTable = buildProblemTable();
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        centerBox.getChildren().addAll(assessmentOverview, assessmentBanner, problemTable);
        setCenter(centerBox);

        // 3. Bottom Status Bar
        HBox bottomBar = new HBox(12);
        bottomBar.getStyleClass().add("status-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        Label srvDot = new Label("●");
        srvDot.getStyleClass().add("status-dot-connected");
        Label srvText = new Label("Máy chấm: Sẵn sàng");
        srvText.getStyleClass().add("status-bar-text");

        Separator sp = new Separator(Orientation.VERTICAL);

        totalLabel = new Label("Tổng số: 0 bài tập");
        totalLabel.getStyleClass().add("status-bar-text");
        problemList.addListener((javafx.collections.ListChangeListener<Problem>) c -> {
            updateTotalLabel();
        });

        Region bSpacer = new Region();
        HBox.setHgrow(bSpacer, Priority.ALWAYS);

        problemLoadStatus = new Label("");
        problemLoadStatus.getStyleClass().add("meta-item");

        bottomBar.getChildren().addAll(srvDot, srvText, sp, totalLabel, bSpacer, problemLoadStatus);
        setBottom(bottomBar);

        // Event hooks
        classFilterCombo.setOnAction(e -> {
            if (!assessmentMode && !assessmentLobby && onSelectClassHandler != null) {
                onSelectClassHandler.accept(classFilterCombo.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void showHistoryDialog() {
        javafx.scene.Scene scene = getScene();
        if (scene == null) return;
        javafx.stage.Window win = scene.getWindow();
        Stage stage = win instanceof Stage ? (Stage) win : null;
        javafx.scene.Parent previousRoot = scene.getRoot();

        StudentHistoryView historyView = new StudentHistoryView(stage);
        historyView.setOnBack(() -> {
            scene.setRoot(previousRoot);
            ThemeManager.getInstance().applyTheme(scene);
        });

        scene.setRoot(historyView);
        ThemeManager.getInstance().applyTheme(scene);
    }

    private void showProfilePopup() {
        if (profilePopup == null || !profilePopup.isShowing()) {
            profilePopup = new ProfilePopup(currentUser);
            profilePopup.setOnLogout(onLogoutHandler);
            javafx.geometry.Point2D point = avatarBtn.localToScreen(0, avatarBtn.getHeight() + 4);
            if (point != null) {
                profilePopup.show(avatarBtn, point.getX() - 240, point.getY());
            }
        } else {
            profilePopup.hide();
        }
    }

    private TableView<Problem> buildProblemTable() {
        TableView<Problem> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 1. Cột Mã bài
        TableColumn<Problem, Integer> idCol = new TableColumn<>("Mã bài");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMinWidth(70);
        idCol.setPrefWidth(85);
        idCol.setMaxWidth(100);
        idCol.setStyle("-fx-alignment: CENTER;");

        // 2. Cột Tiêu đề
        TableColumn<Problem, String> titleCol = new TableColumn<>("Tiêu đề bài tập");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setMinWidth(280);
        titleCol.setPrefWidth(420);

        // 3. Cột Độ khó. Lớp đang chọn đã hiển thị trên thanh trên.
        TableColumn<Problem, String> diffCol = new TableColumn<>("Độ khó");
        diffCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        diffCol.setMinWidth(110);
        diffCol.setPrefWidth(130);
        diffCol.setMaxWidth(160);
        diffCol.setStyle("-fx-alignment: CENTER;");
        diffCol.setCellFactory(col -> new TableCell<Problem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    renderDifficultyBadge(this, item);








                    setText(null);
                }
            }
        });

        // 4. Cột Trạng thái làm bài
        TableColumn<Problem, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setMinWidth(170);
        statusCol.setPrefWidth(190);
        statusCol.setMaxWidth(225);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<Problem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty() || "—".equals(item) || "-".equals(item)) {
                    setGraphic(null);
                    setText("-");
                    setStyle("-fx-text-fill: -text-secondary; -fx-alignment: CENTER;");
                } else {
                    String display = com.grading.client.model.Submission.formatVerdict(item);
                    Label pill = new Label(display);
                    pill.getStyleClass().add("badge-role");
                    pill.setMinWidth(Region.USE_PREF_SIZE);
                    pill.setStyle(
                            "-fx-padding: 3 10 3 10; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-alignment: CENTER;");
                    String u = item.toUpperCase();
                    if (u.equals("AC") || u.equals("ACCEPTED") || u.equals("PASSED")) {
                        pill.setStyle(pill.getStyle()
                                + "-fx-text-fill: -status-ac; -fx-border-color: -status-ac; -fx-background-color: rgba(46, 204, 113, 0.15);");
                    } else if (u.contains("WRONG") || u.equals("WA") || u.equals("FAILED")) {
                        pill.setStyle(pill.getStyle()
                                + "-fx-text-fill: -status-wa; -fx-border-color: -status-wa; -fx-background-color: rgba(231, 76, 60, 0.15);");
                    } else if (u.contains("TLE") || u.contains("TIME_LIMIT")) {
                        pill.setStyle(pill.getStyle()
                                + "-fx-text-fill: -status-tle; -fx-border-color: -status-tle; -fx-background-color: rgba(230, 126, 34, 0.15);");
                    } else if (u.contains("CE") || u.contains("COMPILE")) {
                        pill.setStyle(pill.getStyle()
                                + "-fx-text-fill: -status-ce; -fx-border-color: -status-ce; -fx-background-color: rgba(155, 89, 182, 0.15);");
                    } else {
                        pill.setStyle(pill.getStyle()
                                + "-fx-text-fill: -status-running; -fx-border-color: -status-running; -fx-background-color: rgba(52, 152, 219, 0.15);");
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        // 5. Cột Thao tác
        TableColumn<Problem, Void> actionCol = new TableColumn<>("Thao tác");
        actionCol.setMinWidth(100);
        actionCol.setPrefWidth(110);
        actionCol.setMaxWidth(130);
        actionCol.setStyle("-fx-alignment: CENTER;");
        actionCol.setCellFactory(col -> new TableCell<Problem, Void>() {
            private final Button btn = new Button("Làm bài");
            {
                btn.getStyleClass().addAll("button", "btn-primary");
                btn.setStyle("-fx-font-size: 12px; -fx-padding: 4 13 4 13;");
                btn.setOnAction(e -> {
                    Problem p = getTableView().getItems().get(getIndex());
                    if (onSelectProblemHandler != null && p != null) {
                        onSelectProblemHandler.accept(p);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(idCol, titleCol, diffCol, statusCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Nhấp đúp chuột để mở bài
        table.setRowFactory(tv -> {
            TableRow<Problem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Problem rowData = row.getItem();
                    if (onSelectProblemHandler != null && rowData != null) {
                        onSelectProblemHandler.accept(rowData);
                    }
                }
            });
            return row;
        });

        return table;
    }

    private TableView<AssessmentSummary> buildAssessmentTable() {
        TableView<AssessmentSummary> table = new TableView<>();
        table.setId("assessment-table");
        table.setPlaceholder(new Label("Chưa có bài kiểm tra nào."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AssessmentSummary, String> title = new TableColumn<>("Bài kiểm tra");
        title.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().title()));
        title.setPrefWidth(240);
        title.setMinWidth(150);
        TableColumn<AssessmentSummary, String> classCode = new TableColumn<>("Lớp");
        classCode.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().classCode()));
        classCode.setPrefWidth(100);
        classCode.setMinWidth(75);
        TableColumn<AssessmentSummary, String> time = new TableColumn<>("Bắt đầu");
        time.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                formatAssessmentTime(d.getValue().opensAt())));
        time.setPrefWidth(175);
        time.setMinWidth(150);
        TableColumn<AssessmentSummary, String> duration = new TableColumn<>("Thời lượng");
        duration.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().durationMinutes() + " phút"));
        duration.setPrefWidth(95);
        duration.setMinWidth(85);
        TableColumn<AssessmentSummary, String> questionCount = new TableColumn<>("Số bài");
        questionCount.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(d.getValue().problemCount())));
        questionCount.setPrefWidth(75);
        questionCount.setMinWidth(60);
        TableColumn<AssessmentSummary, String> status = new TableColumn<>("Trạng thái");
        status.setCellValueFactory(
                d -> new javafx.beans.property.SimpleStringProperty(assessmentStatusText(d.getValue())));
        status.setPrefWidth(150);
        status.setMinWidth(110);
        TableColumn<AssessmentSummary, String> countdown = new TableColumn<>("Đếm ngược");
        countdown.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                assessmentCountdownText(d.getValue())));
        countdown.setPrefWidth(135);
        countdown.setMinWidth(110);
        countdown.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");
        TableColumn<AssessmentSummary, Void> action = new TableColumn<>("Thao tác");
        action.setPrefWidth(155);
        action.setMinWidth(145);
        action.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button();
            {
                button.getStyleClass().addAll("button", "btn-primary");
                button.setOnAction(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size())
                        return;
                    AssessmentSummary row = getTableView().getItems().get(getIndex());
                    if (canStartAssessment(row) && onOpenAssessmentHandler != null)
                        onOpenAssessmentHandler.accept(row.id());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                AssessmentSummary row = getTableView().getItems().get(getIndex());
                button.setText(row.started() ? "Tiếp tục làm bài" : "Làm bài ngay");
                setGraphic(canStartAssessment(row) ? button : null);
                setAlignment(Pos.CENTER);
            }
        });
        table.getColumns().addAll(title, classCode, time, duration, questionCount, status, countdown, action);
        return table;
    }

    private boolean canStartAssessment(AssessmentSummary assessment) {
        return !"COMPLETED".equalsIgnoreCase(assessment.status())
                && !"EXPIRED".equalsIgnoreCase(assessment.status())
                && AssessmentSchedule.from(assessment.opensAt(), assessment.closesAt())
                        .canStart(SessionManager.getInstance().getAssessmentNowEpochMs());
    }

    private String assessmentCountdownText(AssessmentSummary assessment) {
        AssessmentSchedule schedule = AssessmentSchedule.from(assessment.opensAt(), assessment.closesAt());
        if (!schedule.isValid())
            return "-";
        long remaining = schedule.secondsUntilOpen(SessionManager.getInstance().getAssessmentNowEpochMs());
        return remaining > 0 ? AssessmentSchedule.formatCountdown(remaining)
                : canStartAssessment(assessment) ? "Đã đến giờ" : "Đã kết thúc";
    }

    public void refreshAssessmentCountdowns() {
        if (assessmentLobby && !assessmentMode)
            assessmentTable.refresh();
    }

    private void setupFiltering() {
        filteredProblems = new FilteredList<>(problemList, p -> true);

        updateProblemFilter = () -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            String diff = difficultyFilterCombo.getSelectionModel().getSelectedItem();

            filteredProblems.setPredicate(problem -> {
                if (assessmentMode)
                    return true;
                boolean matchesQuery = true;
                if (!query.isEmpty()) {
                    matchesQuery = problem.getTitle().toLowerCase().contains(query)
                            || String.valueOf(problem.getId()).contains(query);
                }

                boolean matchesDiff = true;
                if (diff != null && !diff.equals("Tất cả mức độ") && !diff.equals("Tất cả độ khó")) {
                    String selectedLevel = diff.replaceAll("[^0-9]", "");
                    String probDiff = problem.getDifficulty() == null ? "" : problem.getDifficulty();
                    String probLevel = probDiff.replaceAll("[^0-9]", "");
                    if (probLevel.isEmpty()) {
                        if ("EASY".equalsIgnoreCase(probDiff)) probLevel = "1";
                        else if ("MEDIUM".equalsIgnoreCase(probDiff)) probLevel = "3";
                        else if ("HARD".equalsIgnoreCase(probDiff)) probLevel = "5";
                    }
                    matchesDiff = selectedLevel.equals(probLevel);
                }

                return matchesQuery && matchesDiff;
            });
        };

        searchField.textProperty().addListener((obs, o, n) -> updateProblemFilter.run());
        difficultyFilterCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateProblemFilter.run());

        problemTable.setItems(filteredProblems);
        TableViewSupport.fitHeightToRows(problemTable, 12);
    }

    public void setUser(User user) {
        this.currentUser = user;
        if (avatarBtn != null && user != null) {
            avatarBtn.setText(user.getAvatarText());
        }
    }

    public void setClasses(List<CreditClass> list) {
        CreditClass current = classFilterCombo.getSelectionModel().getSelectedItem();
        classFilterCombo.setItems(FXCollections.observableArrayList(list));
        CreditClass retained = current == null ? null : list.stream()
                .filter(item -> item.getId() == current.getId()).findFirst().orElse(null);
        if (retained != null) {
            classFilterCombo.getSelectionModel().select(retained);
        } else if (!list.isEmpty()) {
            classFilterCombo.getSelectionModel().select(0);
        }
    }

    public CreditClass getSelectedClass() {
        return classFilterCombo.getSelectionModel().getSelectedItem();
    }

    public void selectClass(CreditClass cc) {
        if (cc == null)
            return;
        for (CreditClass item : classFilterCombo.getItems()) {
            if (item != null && item.getId() == cc.getId()) {
                classFilterCombo.getSelectionModel().select(item);
                return;
            }
        }
    }

    public void setProblems(List<Problem> list) {
        problemList.setAll(list == null ? List.of()
                : list.stream()
                        .filter(problem -> problem != null && problem.getTitle() != null
                                && !problem.getTitle().isBlank())
                        .toList());
    }

    public void setProblemsLoading(boolean loading) {
        problemLoadStatus.setText(loading ? "Đang tải bài luyện tập…" : "");
        problemTable.setPlaceholder(new Label(loading ? "Đang tải bài luyện tập…" : "Chưa có bài tập nào."));
    }

    public void showProblemLoadError(String message) {
        problemLoadStatus.setText(message);
        problemTable.setPlaceholder(new Label(message));
    }

    public void setAssessmentMode(boolean active, String title, String classCode,
            String closesAt, int durationMinutes) {
        assessmentMode = active;
        if (active) setProblemsLoading(false);
        assessmentBanner.setVisible(active);
        assessmentBanner.setManaged(active);
        setAssessmentCountdownVisible(active);
        if (active) {
            assessmentBanner.setText((title == null ? "Bài kiểm tra" : title)
                    + (classCode == null ? "" : " - " + classCode)
                    + "\nThời lượng: " + durationMinutes + " phút · Kết thúc: " + formatAssessmentTime(closesAt)
                    + "\nChỉ các bài do giáo viên giao được hiển thị trong thời gian này.");
        } else {
            assessmentBanner.setText("");
        }
        updateAssessmentLayout();
    }

    public void setAssessmentCountdownVisible(boolean visible) {
        if (assessmentCountdownBadge != null) {
            assessmentCountdownBadge.setVisible(visible);
            assessmentCountdownBadge.setManaged(visible);
        }
    }

    public void updateAssessmentCountdown(long remainingSeconds, String formattedTime) {
        if (assessmentCountdownBadge == null)
            return;
        if (remainingSeconds <= 0) {
            assessmentCountdownBadge.setText("Hết giờ!");
            assessmentCountdownBadge.setStyle(
                    "-fx-background-color: rgba(239, 68, 68, 0.2); " +
                            "-fx-text-fill: #dc2626; " +
                            "-fx-padding: 4 14; " +
                            "-fx-background-radius: 12px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 13px; " +
                            "-fx-border-color: #ef4444; " +
                            "-fx-border-radius: 12px;");
        } else if (remainingSeconds <= 300) {
            assessmentCountdownBadge.setText(formattedTime);
            assessmentCountdownBadge.setStyle(
                    "-fx-background-color: rgba(239, 68, 68, 0.15); " +
                            "-fx-text-fill: #dc2626; " +
                            "-fx-padding: 4 14; " +
                            "-fx-background-radius: 12px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 13px; " +
                            "-fx-border-color: rgba(239, 68, 68, 0.4); " +
                            "-fx-border-radius: 12px;");
        } else {
            assessmentCountdownBadge.setText(formattedTime);
            assessmentCountdownBadge.setStyle(
                    "-fx-background-color: rgba(14, 165, 233, 0.12); " +
                            "-fx-text-fill: #0284c7; " +
                            "-fx-padding: 4 14; " +
                            "-fx-background-radius: 12px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 13px; " +
                            "-fx-border-color: rgba(14, 165, 233, 0.35); " +
                            "-fx-border-radius: 12px;");
        }
    }

    public void setAssessmentLobby(boolean locked) {
        assessmentLobby = locked;
        if (locked) setProblemsLoading(false);
        updateAssessmentLayout();
    }

    private void updateAssessmentLayout() {
        boolean locked = assessmentMode || assessmentLobby;
        boolean waiting = assessmentLobby && !assessmentMode;
        for (Control control : List.of(classFilterCombo, difficultyFilterCombo, searchField, historyBtn)) {
            control.setVisible(!locked);
            control.setManaged(!locked);
        }
        problemTable.setVisible(!assessmentLobby || assessmentMode);
        problemTable.setManaged(!assessmentLobby || assessmentMode);
        assessmentOverview.setVisible(waiting);
        assessmentOverview.setManaged(waiting);
        completeAssessmentButton.setVisible(assessmentMode);
        completeAssessmentButton.setManaged(assessmentMode);
        brandLabel.setText(assessmentMode ? "Bài kiểm tra đang diễn ra"
                : waiting ? "Phòng chờ bài kiểm tra" : "Auto Grader");
        if (updateProblemFilter != null)
            updateProblemFilter.run();
        updateTotalLabel();
    }

    public void setAssessments(List<AssessmentSummary> assessments) {
        assessmentTable.setItems(FXCollections.observableArrayList(assessments == null ? List.of()
                : assessments.stream().filter(assessment -> assessment != null
                        && !"COMPLETED".equalsIgnoreCase(assessment.status())
                        && !"EXPIRED".equalsIgnoreCase(assessment.status())).toList()));
        updateAssessmentLayout();
    }

    private void updateTotalLabel() {
        if (totalLabel == null)
            return;
        totalLabel.setText(assessmentLobby && !assessmentMode
                ? "Bài kiểm tra: " + assessmentTable.getItems().size()
                : assessmentMode ? "Bài thi: " + problemList.size() + " câu"
                        : "Tổng số: " + problemList.size() + " bài tập");
    }

    private String assessmentStatusText(AssessmentSummary assessment) {
        if ("COMPLETED".equalsIgnoreCase(assessment.status())) {
            return "Đã hoàn thành";
        }
        if ("EXPIRED".equalsIgnoreCase(assessment.status()))
            return "Đã kết thúc";
        if (canStartAssessment(assessment))
            return assessment.started() ? "Đang làm bài" : "Chưa làm bài";
        return "Sắp bắt đầu";
    }

    private String formatAssessmentTime(String value) {
        if (value == null || value.isBlank())
            return "-";
        String normalized = value.trim().replace(" ", "T");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        try {
            return OffsetDateTime.parse(normalized).atZoneSameInstant(ZoneId.systemDefault()).format(format);
        } catch (Exception ignored) {
            // Database timestamps without an offset use the local classroom time.
        }
        try {
            return LocalDateTime.parse(normalized).format(format);
        } catch (Exception ignored) {
            return value;
        }
    }

    public void addProblem(Problem p) {
        problemList.add(p);
    }

    public void setOnSelectProblem(Consumer<Problem> handler) {
        this.onSelectProblemHandler = handler;
    }

    public void setOnSelectClass(Consumer<CreditClass> handler) {
        this.onSelectClassHandler = handler;
    }

    public void setOnLogout(Runnable handler) {
        this.onLogoutHandler = handler;
    }

    public void setOnOpenAssessment(Consumer<Integer> handler) {
        this.onOpenAssessmentHandler = handler;
    }

    public void setOnCompleteAssessment(Runnable handler) {
        this.onCompleteAssessmentHandler = handler;
    }

    private void renderDifficultyBadge(TableCell<Problem, String> cell, String item) {
        String level = (item == null) ? "1" : item.replaceAll("[^0-9]", "");
        if (level.isEmpty()) {
            if ("EASY".equalsIgnoreCase(item)) level = "1";
            else if ("MEDIUM".equalsIgnoreCase(item)) level = "3";
            else if ("HARD".equalsIgnoreCase(item)) level = "5";
            else level = "1";
        }
        Label badge = new Label("Mức " + level);
        badge.setStyle("-fx-padding: 3 10 3 10; -fx-font-size: 11.5px;");
        int lvl = 1;
        try { lvl = Integer.parseInt(level); } catch (Exception ignored) {}
        if (lvl <= 2) {
            badge.getStyleClass().add("badge-easy");
        } else if (lvl <= 4) {
            badge.getStyleClass().add("badge-medium");
        } else {
            badge.getStyleClass().add("badge-hard");
        }
        cell.setGraphic(badge);
        cell.setText(null);
    }
}
