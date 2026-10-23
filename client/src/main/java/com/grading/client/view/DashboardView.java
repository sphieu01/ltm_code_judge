package com.grading.client.view;

import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * DashboardView — Màn hình Danh sách bài tập dành cho Sinh viên.
 *
 * Tính năng theo yêu cầu & DESIGN.md:
 * 1. Header có Avatar cá nhân: bấm vào mở ProfilePopup chứa thông tin sinh viên và nút Đăng xuất.
 * 2. ComboBox chọn Lớp tín chỉ (lớp học được giáo viên thêm vào) để làm bài riêng biệt theo lớp.
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
    private boolean assessmentMode;

    // Table
    private TableView<Problem> problemTable;
    private ObservableList<Problem> problemList = FXCollections.observableArrayList();
    private FilteredList<Problem> filteredProblems;

    // Handlers
    private Consumer<Problem> onSelectProblemHandler;
    private Consumer<CreditClass> onSelectClassHandler;
    private Runnable onLogoutHandler;

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
                "Tất cả độ khó", "EASY", "MEDIUM", "HARD"));
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
                themeToggleBtn,
                avatarBtn
        );
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

        problemTable = buildProblemTable();
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        centerBox.getChildren().addAll(assessmentBanner, problemTable);
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

        Label totalLabel = new Label("Tổng số: 0 bài tập");
        totalLabel.getStyleClass().add("status-bar-text");
        problemList.addListener((javafx.collections.ListChangeListener<Problem>) c -> {
            totalLabel.setText("Tổng số: " + problemList.size() + " bài tập");
        });

        Region bSpacer = new Region();
        HBox.setHgrow(bSpacer, Priority.ALWAYS);

        Label hintLabel = new Label("Nhấp đúp chuột vào bài tập để mở trình soạn thảo code");
        hintLabel.getStyleClass().add("meta-item");

        bottomBar.getChildren().addAll(srvDot, srvText, sp, totalLabel, bSpacer, hintLabel);
        setBottom(bottomBar);

        // Event hooks
        classFilterCombo.setOnAction(e -> {
            if (!assessmentMode && onSelectClassHandler != null) {
                onSelectClassHandler.accept(classFilterCombo.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void showHistoryDialog() {
        javafx.stage.Window win = getScene() != null ? getScene().getWindow() : null;
        Stage stage = win instanceof Stage ? (Stage) win : null;
        StudentHistoryDialog dialog = new StudentHistoryDialog(stage);
        dialog.showAndWait();
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
                    Label badge = new Label(item);
                    badge.setStyle("-fx-padding: 3 10 3 10; -fx-font-size: 11.5px;");
                    if ("EASY".equalsIgnoreCase(item))
                        badge.getStyleClass().add("badge-easy");
                    else if ("MEDIUM".equalsIgnoreCase(item))
                        badge.getStyleClass().add("badge-medium");
                    else
                        badge.getStyleClass().add("badge-hard");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // 4. Cột Trạng thái làm bài
        TableColumn<Problem, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setMinWidth(120);
        statusCol.setPrefWidth(140);
        statusCol.setMaxWidth(160);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<Problem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setGraphic(null);
                    setText("—");
                    setStyle("-fx-text-fill: -text-secondary; -fx-alignment: CENTER;");
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("badge-role");
                    if ("AC".equalsIgnoreCase(item)) {
                        pill.setStyle("-fx-text-fill: -status-ac; -fx-border-color: -status-ac; -fx-background-color: rgba(46, 204, 113, 0.15);");
                    } else if ("WA".equalsIgnoreCase(item)) {
                        pill.setStyle("-fx-text-fill: -status-wa; -fx-border-color: -status-wa; -fx-background-color: rgba(231, 76, 60, 0.15);");
                    } else {
                        pill.setStyle("-fx-text-fill: -status-tle; -fx-border-color: -status-tle; -fx-background-color: rgba(230, 126, 34, 0.15);");
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

    private void setupFiltering() {
        filteredProblems = new FilteredList<>(problemList, p -> true);

        Runnable updateFilter = () -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            String diff = difficultyFilterCombo.getSelectionModel().getSelectedItem();

            filteredProblems.setPredicate(problem -> {
                boolean matchesQuery = true;
                if (!query.isEmpty()) {
                    matchesQuery = problem.getTitle().toLowerCase().contains(query)
                            || String.valueOf(problem.getId()).contains(query);
                }

                boolean matchesDiff = true;
                if (diff != null && !diff.equals("Tất cả độ khó")) {
                    matchesDiff = diff.equalsIgnoreCase(problem.getDifficulty());
                }

                return matchesQuery && matchesDiff;
            });
        };

        searchField.textProperty().addListener((obs, o, n) -> updateFilter.run());
        difficultyFilterCombo.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateFilter.run());

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
        if (current != null) {
            classFilterCombo.getSelectionModel().select(current);
        } else if (!list.isEmpty()) {
            classFilterCombo.getSelectionModel().select(0);
        }
    }

    public CreditClass getSelectedClass() {
        return classFilterCombo.getSelectionModel().getSelectedItem();
    }

    public void setProblems(List<Problem> list) {
        problemList.setAll(list == null ? List.of() : list.stream()
                .filter(problem -> problem != null && problem.getTitle() != null && !problem.getTitle().isBlank())
                .toList());
    }

    public void setAssessmentMode(boolean active, String title, String classCode,
                                  String closesAt, int durationMinutes) {
        assessmentMode = active;
        for (Control control : List.of(classFilterCombo, difficultyFilterCombo, searchField, historyBtn)) {
            control.setVisible(!active);
            control.setManaged(!active);
        }
        assessmentBanner.setVisible(active);
        assessmentBanner.setManaged(active);
        if (active) {
            brandLabel.setText("Bài kiểm tra đang diễn ra");
            assessmentBanner.setText((title == null ? "Bài kiểm tra" : title)
                    + (classCode == null ? "" : " — " + classCode)
                    + "\nThời lượng: " + durationMinutes + " phút · Kết thúc: " + formatAssessmentTime(closesAt)
                    + "\nChỉ các bài do giáo viên giao được hiển thị trong thời gian này.");
        } else {
            brandLabel.setText("Auto Grader");
            assessmentBanner.setText("");
        }
    }

    private String formatAssessmentTime(String value) {
        if (value == null || value.isBlank()) return "—";
        try {
            return java.time.LocalDateTime.parse(value)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
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
}
