package com.grading.client.view;

import com.grading.client.model.Problem;
import com.grading.client.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
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
        topBar.setPadding(new Insets(10, 20, 10, 20));

        Label brandLabel = new Label("⚡ Auto Grader — Danh Sách Bài Tập");
        brandLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        userGreetingLabel = new Label("Xin chào, Khách");
        userGreetingLabel.setStyle("-fx-text-fill: -text-main; -fx-font-weight: bold;");

        roleBadgeLabel = new Label("STUDENT");
        roleBadgeLabel.getStyleClass().add("badge-role");

        // Nút thêm bài chỉ dành cho giáo viên
        teacherAddProblemBtn = new Button("➕ Thêm bài tập");
        teacherAddProblemBtn.getStyleClass().addAll("button", "btn-submit");
        teacherAddProblemBtn.setVisible(false);
        teacherAddProblemBtn.setManaged(false);

        logoutBtn = new Button("Đăng xuất");
        logoutBtn.getStyleClass().addAll("button", "btn-secondary");

        topBar.getChildren().addAll(brandLabel, spacer, userGreetingLabel, roleBadgeLabel, teacherAddProblemBtn, logoutBtn);
        setTop(topBar);

        // 2. Center Content
        VBox centerBox = new VBox(14);
        centerBox.setPadding(new Insets(18, 24, 20, 24));

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Tìm kiếm bài tập theo tên hoặc ID...");
        searchField.setPrefWidth(320);

        difficultyFilterCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Tất cả độ khó", "EASY", "MEDIUM", "HARD"
        ));
        difficultyFilterCombo.getSelectionModel().select(0);

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        refreshBtn = new Button("🔄 Làm mới");
        refreshBtn.getStyleClass().addAll("button", "btn-secondary");

        filterBar.getChildren().addAll(searchField, difficultyFilterCombo, filterSpacer, refreshBtn);

        // Table View
        problemTable = buildProblemTable();
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        centerBox.getChildren().addAll(filterBar, problemTable);
        setCenter(centerBox);

        // Event hooks
        logoutBtn.setOnAction(e -> {
            if (onLogoutHandler != null) onLogoutHandler.run();
        });

        teacherAddProblemBtn.setOnAction(e -> {
            if (onTeacherAddProblemHandler != null) onTeacherAddProblemHandler.run();
        });

        refreshBtn.setOnAction(e -> {
            if (onRefreshHandler != null) onRefreshHandler.run();
        });
    }

    private TableView<Problem> buildProblemTable() {
        TableView<Problem> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Cột ID
        TableColumn<Problem, Integer> idCol = new TableColumn<>("#");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);
        idCol.setMaxWidth(70);

        // Cột Tiêu đề
        TableColumn<Problem, String> titleCol = new TableColumn<>("Tiêu đề bài tập");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(300);

        // Cột Độ khó
        TableColumn<Problem, String> diffCol = new TableColumn<>("Độ khó");
        diffCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        diffCol.setPrefWidth(100);
        diffCol.setCellFactory(col -> new TableCell<Problem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("EASY".equalsIgnoreCase(item)) badge.getStyleClass().add("badge-easy");
                    else if ("MEDIUM".equalsIgnoreCase(item)) badge.getStyleClass().add("badge-medium");
                    else badge.getStyleClass().add("badge-hard");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Cột Thời gian
        TableColumn<Problem, String> timeCol = new TableColumn<>("Thời gian");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTimeLimitFormatted()));
        timeCol.setPrefWidth(100);

        // Cột Bộ nhớ
        TableColumn<Problem, String> memCol = new TableColumn<>("Bộ nhớ");
        memCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMemoryLimitFormatted()));
        memCol.setPrefWidth(100);

        // Cột Thao tác (Nút Làm bài)
        TableColumn<Problem, Void> actionCol = new TableColumn<>("Thao tác");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<Problem, Void>() {
            private final Button doProblemBtn = new Button("Làm bài ▶");
            {
                doProblemBtn.getStyleClass().addAll("button", "btn-submit");
                doProblemBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 10 3 10;");
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

        table.getColumns().addAll(idCol, titleCol, diffCol, timeCol, memCol, actionCol);

        // Double click vào hàng để làm bài
        table.setRowFactory(tv -> {
            TableRow<Problem> row = new TableRow<>();
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
            userGreetingLabel.setText("Xin chào, " + (user.getFullName() != null ? user.getFullName() : user.getUsername()));
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
