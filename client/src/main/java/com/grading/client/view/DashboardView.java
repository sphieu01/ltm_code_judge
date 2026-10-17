package com.grading.client.view;

import com.grading.client.model.Problem;
import com.grading.client.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * DashboardView — Màn hình Danh sách bài tập & Chọn bài làm.
 * Tự động hiển thị nút "Thêm bài tập" nếu người dùng có role là TEACHER.
 */
public class DashboardView extends BorderPane {

    // Top
    private Label userGreetingLabel;
    private Label roleBadgeLabel;
    private Button teacherAddProblemBtn;
    private Button logoutBtn;

    // Filter bar
    private TextField searchField;
    private ComboBox<String> difficultyFilterCombo;
    private Button refreshBtn;

    // Table
    private TableView<Problem> problemTable;
    private ObservableList<Problem> problemList = FXCollections.observableArrayList();
    private FilteredList<Problem> filteredProblems;

    // Handlers
    private Consumer<Problem> onSelectProblemHandler;
    private Runnable onTeacherAddProblemHandler;
    private Runnable onLogoutHandler;
    private Runnable onRefreshHandler;

    public DashboardView() {
        buildLayout();
        setupFiltering();
    }

    private void buildLayout() {
        getStyleClass().add("main-pane");

        // 1. Top Header
        HBox topBar = new HBox(14);
        topBar.getStyleClass().add("top-toolbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 16, 8, 16));

        Label brandLabel = new Label("Auto Grader - Danh sách bài tập");
        brandLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        userGreetingLabel = new Label("Xin chào, Khách");
        userGreetingLabel.setStyle("-fx-text-fill: -text-primary; -fx-font-weight: bold;");

        roleBadgeLabel = new Label("STUDENT");
        roleBadgeLabel.getStyleClass().add("badge-role");

        // Nút chuyển đổi Theme Sáng / Tối
        Button themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        // Nút thêm bài chỉ dành cho giáo viên (dùng btn-primary #007ACC)
        teacherAddProblemBtn = new Button("+ Thêm bài tập");
        teacherAddProblemBtn.getStyleClass().addAll("button", "btn-primary");
        teacherAddProblemBtn.setVisible(false);
        teacherAddProblemBtn.setManaged(false);

        logoutBtn = new Button("Đăng xuất");
        logoutBtn.getStyleClass().addAll("button", "btn-secondary");

        topBar.getChildren().addAll(brandLabel, spacer, userGreetingLabel, roleBadgeLabel, themeToggleBtn,
                teacherAddProblemBtn, logoutBtn);
        setTop(topBar);

        // 2. Center Content
        VBox centerBox = new VBox(12);
        centerBox.setPadding(new Insets(14, 20, 16, 20));

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Tìm kiếm bài tập theo tên hoặc ID...");
        searchField.setPrefWidth(300);

        difficultyFilterCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Tất cả độ khó", "EASY", "MEDIUM", "HARD"));
        difficultyFilterCombo.getSelectionModel().select(0);

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        refreshBtn = new Button("Làm mới");
        refreshBtn.getStyleClass().addAll("button", "btn-secondary");

        filterBar.getChildren().addAll(searchField, difficultyFilterCombo, filterSpacer, refreshBtn);

        // Table View
        problemTable = buildProblemTable();
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        centerBox.getChildren().addAll(filterBar, problemTable);
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

        Label hintLabel = new Label("Nhấp đúp chuột vào bài tập để mở trình soạn thảo");
        hintLabel.getStyleClass().add("meta-item");

        bottomBar.getChildren().addAll(srvDot, srvText, sp, totalLabel, bSpacer, hintLabel);
        setBottom(bottomBar);

        // Event hooks
        logoutBtn.setOnAction(e -> {
            if (onLogoutHandler != null)
                onLogoutHandler.run();
        });

        teacherAddProblemBtn.setOnAction(e -> {
            if (onTeacherAddProblemHandler != null)
                onTeacherAddProblemHandler.run();
        });

        refreshBtn.setOnAction(e -> {
            if (onRefreshHandler != null)
                onRefreshHandler.run();
        });
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
        TableColumn<Problem, String> titleCol = new TableColumn<>("Tiêu đề");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setMinWidth(280);
        titleCol.setPrefWidth(420);

        // 3. Cột Độ khó
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

        // 4. Cột Trạng thái (nếu từng AC thì là AC, còn chưa từng AC thì để trạng thái gần nhất, chưa làm thì để trống)
        TableColumn<Problem, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        statusCol.setMinWidth(120);
        statusCol.setPrefWidth(140);
        statusCol.setMaxWidth(170);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<Problem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label();
                    pill.setStyle("-fx-padding: 3 12 3 12; -fx-font-size: 11.5px;");
                    applyStatusPillStyle(pill, item);
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        // 5. Cột Thao tác (Nút Làm bài)
        TableColumn<Problem, Void> actionCol = new TableColumn<>("Thao tác");
        actionCol.setMinWidth(130);
        actionCol.setPrefWidth(150);
        actionCol.setMaxWidth(180);
        actionCol.setStyle("-fx-alignment: CENTER;");
        actionCol.setCellFactory(col -> new TableCell<Problem, Void>() {
            private final Button doProblemBtn = new Button("Làm bài ▶");
            {
                doProblemBtn.getStyleClass().addAll("button", "btn-primary");
                doProblemBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5 14 5 14;");
                doProblemBtn.setOnAction(e -> {
                    Problem p = getTableView().getItems().get(getIndex());
                    if (onSelectProblemHandler != null) {
                        onSelectProblemHandler.accept(p);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : doProblemBtn);
            }
        });

        table.getColumns().addAll(idCol, titleCol, diffCol, statusCol, actionCol);

        // Tắt hiệu ứng focus và bôi màu chọn dòng khi bấm vào hàng
        table.setFocusTraversable(false);
        table.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.intValue() >= 0) {
                javafx.application.Platform.runLater(() -> table.getSelectionModel().clearSelection());
            }
        });
        table.itemsProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                javafx.application.Platform.runLater(() -> table.getFocusModel().focus(-1));
            }
        });

        // Double click vào hàng để làm bài
        table.setRowFactory(tv -> {
            TableRow<Problem> row = new TableRow<>();
            row.setFocusTraversable(false);
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Problem rowData = row.getItem();
                    if (onSelectProblemHandler != null) {
                        onSelectProblemHandler.accept(rowData);
                    }
                }
            });
            return row;
        });

        return table;
    }

    private void applyStatusPillStyle(Label pill, String verdict) {
        if (pill == null || verdict == null)
            return;
        pill.getStyleClass().removeAll("pill-ac", "pill-wa", "pill-tle", "pill-ce", "pill-running", "pill-neutral");
        String v = verdict.toUpperCase().trim();
        if (v.equals("ACCEPTED") || v.equals("AC") || v.equals("PASSED")) {
            pill.setText("AC");
            pill.getStyleClass().add("pill-ac");
        } else if (v.contains("WRONG") || v.equals("WA") || v.equals("FAILED")) {
            pill.setText("WA");
            pill.getStyleClass().add("pill-wa");
        } else if (v.contains("TLE") || v.contains("TIME_LIMIT")) {
            pill.setText("TLE");
            pill.getStyleClass().add("pill-tle");
        } else if (v.contains("CE") || v.contains("COMPILE")) {
            pill.setText("CE");
            pill.getStyleClass().add("pill-ce");
        } else if (v.contains("RUNTIME") || v.equals("RE")) {
            pill.setText("RE");
            pill.getStyleClass().add("pill-wa");
        } else {
            pill.setText(v);
            pill.getStyleClass().add("pill-neutral");
        }
    }

    private void setupFiltering() {
        filteredProblems = new FilteredList<>(problemList, p -> true);

        Runnable updateFilter = () -> {
            String query = searchField.getText().toLowerCase().trim();
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
    }

    public void setUser(User user) {
        if (user != null) {
            userGreetingLabel
                    .setText("Xin chào, " + (user.getFullName() != null ? user.getFullName() : user.getUsername()));
            roleBadgeLabel.setText(user.getRole() != null ? user.getRole() : "STUDENT");

            // Hiển thị nút Thêm bài nếu là Giáo viên
            boolean isTeacher = user.isTeacher();
            teacherAddProblemBtn.setVisible(isTeacher);
            teacherAddProblemBtn.setManaged(isTeacher);
        } else {
            userGreetingLabel.setText("Chế độ Khách (Offline)");
            roleBadgeLabel.setText("GUEST");
            teacherAddProblemBtn.setVisible(true); // Để tiện thử nghiệm cả chức năng của giáo viên
            teacherAddProblemBtn.setManaged(true);
        }
    }

    public void setProblems(List<Problem> list) {
        problemList.setAll(list);
    }

    public void addProblem(Problem p) {
        problemList.add(p);
    }

    public void setOnSelectProblem(Consumer<Problem> handler) {
        this.onSelectProblemHandler = handler;
    }

    public void setOnTeacherAddProblem(Runnable handler) {
        this.onTeacherAddProblemHandler = handler;
    }

    public void setOnLogout(Runnable handler) {
        this.onLogoutHandler = handler;
    }

    public void setOnRefresh(Runnable handler) {
        this.onRefreshHandler = handler;
    }
}
