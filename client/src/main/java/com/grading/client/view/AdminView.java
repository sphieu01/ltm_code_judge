package com.grading.client.view;

import com.grading.client.model.Course;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * AdminView — Giao diện Cổng Quản Trị dành riêng cho Giáo viên (Admin).
 * Tuân thủ quy chuẩn thiết kế Minimalist Flat trong DESIGN.md.
 *
 * Chức năng:
 * 1. Quản lý Môn học (Courses: C++, Java, Python...)
 * 2. Quản lý Lớp tín chỉ (Classes)
 * 3. Quản lý Sinh viên & Nạp file dữ liệu để cấp tài khoản
 * 4. Quản lý Bài tập riêng biệt theo từng Lớp tín chỉ
 */
public class AdminView extends BorderPane {

    private User currentUser;

    // Header Controls
    private Label brandLabel;
    private Button themeToggleBtn;
    private Button avatarBtn;
    private ProfilePopup profilePopup;

    // Navigation Tab Buttons
    private Button navCoursesBtn;
    private Button navClassesBtn;
    private Button navProblemsBtn;

    // Stack container for Tab contents
    private StackPane contentStack;
    private VBox coursesPane;
    private VBox classesPane;
    private VBox studentsPane;
    private VBox problemsPane;

    private static final int PAGE_SIZE = 15;

    // ─── TAB 1: COURSES ───
    private TableView<Course> courseTable;
    private ObservableList<Course> courseList = FXCollections.observableArrayList();
    private Button addCourseBtn;
    private Consumer<Course> onEditCourse;
    private Consumer<Course> onDeleteCourse;

    // ─── TAB 2: CLASSES ───
    private TableView<CreditClass> classTable;
    private ObservableList<CreditClass> classList = FXCollections.observableArrayList();
    private Button addClassBtn;
    private Consumer<CreditClass> onEditClass;
    private Consumer<CreditClass> onDeleteClass;
    private Consumer<CreditClass> onViewClass;

    // ─── TAB 3: STUDENTS ───
    private ComboBox<CreditClass> studentFilterClassCombo;
    private TableView<User.UserDTO> studentTable;
    private ObservableList<User.UserDTO> studentList = FXCollections.observableArrayList();
    private List<User.UserDTO> rawStudentList = new java.util.ArrayList<>();
    private int currentStudentPage = 0;
    private Button importStudentsBtn;
    private Button studentPrevBtn;
    private Button studentNextBtn;
    private Label studentPageLbl;
    private Consumer<User.UserDTO> onEditStudent;
    private Consumer<User.UserDTO> onDeleteStudent;

    // ─── TAB 4: PROBLEMS ───
    private ComboBox<CreditClass> problemFilterClassCombo;
    private TableView<Problem> problemTable;
    private ObservableList<Problem> problemList = FXCollections.observableArrayList();
    private List<Problem> rawProblemList = new java.util.ArrayList<>();
    private int currentProblemPage = 0;
    private Button addProblemBtn;
    private Button problemPrevBtn;
    private Button problemNextBtn;
    private Label problemPageLbl;
    private java.util.function.BiConsumer<Problem, Boolean> onToggleProblemActive;
    private Consumer<Problem> onEditProblem;
    private Consumer<Problem> onDeleteProblem;

    // Callbacks
    private Runnable onLogout;

    public AdminView() {
        buildLayout();
    }

    public void setUser(User user) {
        this.currentUser = user;
        if (avatarBtn != null && user != null) {
            avatarBtn.setText(user.getAvatarText());
        }
    }

    private void buildLayout() {
        getStyleClass().add("main-pane");
        setStyle("-fx-font-size: 14px;");

        // 1. TOP HEADER
        HBox topBar = new HBox(12);
        topBar.getStyleClass().add("top-toolbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 20, 8, 20));

        brandLabel = new Label("Auto Grader - Cổng Quản Trị Giáo Viên");
        brandLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Label adminBadge = new Label("ADMIN / GIÁO VIÊN");
        adminBadge.getStyleClass().add("badge-role");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        // Avatar Button theo chuẩn DESIGN.md Component 5
        avatarBtn = new Button("GV");
        avatarBtn.getStyleClass().add("avatar-btn");
        avatarBtn.setOnAction(e -> showProfilePopup());

        topBar.getChildren().addAll(brandLabel, adminBadge, spacer, themeToggleBtn, avatarBtn);
        setTop(topBar);

        // 2. NAVIGATION BAR
        HBox navBar = new HBox(0);
        navBar.setStyle("-fx-background-color: -bg-surface; -fx-border-color: transparent transparent -border-subtle transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 20 0 20;");
        navBar.setAlignment(Pos.CENTER_LEFT);

        navCoursesBtn = createNavButton("Môn học");
        navClassesBtn = createNavButton("Lớp tín chỉ");
        navProblemsBtn = createNavButton("Bài tập");

        navBar.getChildren().addAll(navCoursesBtn, navClassesBtn, navProblemsBtn);

        // 3. CENTER CONTENT STACK
        contentStack = new StackPane();
        contentStack.setPadding(new Insets(14, 20, 16, 20));

        coursesPane = buildCoursesPane();
        classesPane = buildClassesPane();
        studentsPane = buildStudentsPane();
        problemsPane = buildProblemsPane();

        contentStack.getChildren().addAll(coursesPane, classesPane, problemsPane);

        VBox centerContainer = new VBox(navBar, contentStack);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        setCenter(centerContainer);

        // Mặc định chọn tab Môn học
        switchTab(0);

        navCoursesBtn.setOnAction(e -> switchTab(0));
        navClassesBtn.setOnAction(e -> switchTab(1));
        navProblemsBtn.setOnAction(e -> switchTab(2));
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("admin-nav-btn");
        return btn;
    }

    private void switchTab(int index) {
        navCoursesBtn.getStyleClass().remove("admin-nav-btn-active");
        navClassesBtn.getStyleClass().remove("admin-nav-btn-active");
        navProblemsBtn.getStyleClass().remove("admin-nav-btn-active");

        coursesPane.setVisible(false);
        coursesPane.setManaged(false);
        classesPane.setVisible(false);
        classesPane.setManaged(false);
        problemsPane.setVisible(false);
        problemsPane.setManaged(false);

        switch (index) {
            case 0 -> {
                navCoursesBtn.getStyleClass().add("admin-nav-btn-active");
                coursesPane.setVisible(true);
                coursesPane.setManaged(true);
            }
            case 1 -> {
                navClassesBtn.getStyleClass().add("admin-nav-btn-active");
                classesPane.setVisible(true);
                classesPane.setManaged(true);
            }
            case 2 -> {
                navProblemsBtn.getStyleClass().add("admin-nav-btn-active");
                problemsPane.setVisible(true);
                problemsPane.setManaged(true);
            }
        }
    }

    // ─── TAB BUILDERS ────────────────────────────────────────────────────────

    private VBox buildCoursesPane() {
        VBox pane = new VBox(12);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        addCourseBtn = new Button("+ Thêm môn học");
        addCourseBtn.getStyleClass().addAll("button", "btn-primary");

        toolbar.getChildren().addAll(addCourseBtn);

        courseTable = new TableView<>();
        courseTable.getStyleClass().add("table-view");
        courseTable.setPlaceholder(new Label("Chưa có môn học nào"));

        TableColumn<Course, String> colCode = new TableColumn<>("Mã môn");
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colCode.setMinWidth(100);
        colCode.setPrefWidth(120);
        colCode.setMaxWidth(145);

        TableColumn<Course, String> colName = new TableColumn<>("Tên môn học");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setMinWidth(175);
        colName.setPrefWidth(210);

        TableColumn<Course, String> colLang = new TableColumn<>("Ngôn ngữ chính");
        colLang.setCellValueFactory(new PropertyValueFactory<>("language"));
        colLang.setMinWidth(140);
        colLang.setPrefWidth(170);
        colLang.setMaxWidth(200);

        TableColumn<Course, String> colDesc = new TableColumn<>("Mô tả");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colDesc.setMinWidth(250);
        colDesc.setPrefWidth(360);

        TableColumn<Course, Void> colActions = new TableColumn<>("Thao tác");
        colActions.setMinWidth(130);
        colActions.setPrefWidth(140);
        colActions.setMaxWidth(155);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Sửa");
            private final Button deleteBtn = new Button("Xóa");
            private final HBox actions = new HBox(6, editBtn, deleteBtn);
            { actions.setAlignment(Pos.CENTER); editBtn.getStyleClass().addAll("button", "btn-secondary"); deleteBtn.getStyleClass().addAll("button", "btn-danger");
              editBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;"); deleteBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
              editBtn.setOnAction(e -> { Course item = getTableRow() == null ? null : getTableRow().getItem(); if (onEditCourse != null && item != null) onEditCourse.accept(item); });
              deleteBtn.setOnAction(e -> { Course item = getTableRow() == null ? null : getTableRow().getItem(); if (onDeleteCourse != null && item != null) onDeleteCourse.accept(item); }); }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : actions); setAlignment(Pos.CENTER); }
        });
        courseTable.getColumns().addAll(colCode, colName, colLang, colDesc, colActions);
        courseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        courseTable.setItems(courseList);
        TableViewSupport.fitHeightToRows(courseTable, 15);
        VBox.setVgrow(courseTable, Priority.NEVER);

        pane.getChildren().addAll(toolbar, courseTable);
        return pane;
    }

    private VBox buildClassesPane() {
        VBox pane = new VBox(12);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        addClassBtn = new Button("+ Thêm lớp tín chỉ");
        addClassBtn.getStyleClass().addAll("button", "btn-primary");

        toolbar.getChildren().addAll(addClassBtn);

        classTable = new TableView<>();
        classTable.getStyleClass().add("table-view");
        classTable.setPlaceholder(new Label("Chưa có lớp tín chỉ nào"));

        TableColumn<CreditClass, String> colCode = new TableColumn<>("Mã lớp");
        colCode.setCellValueFactory(new PropertyValueFactory<>("classCode"));
        colCode.setMinWidth(105);
        colCode.setPrefWidth(125);
        colCode.setMaxWidth(150);

        TableColumn<CreditClass, String> colName = new TableColumn<>("Tên lớp tín chỉ");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setMinWidth(210);
        colName.setPrefWidth(280);

        TableColumn<CreditClass, String> colCourse = new TableColumn<>("Môn học");
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseName"));
        colCourse.setMinWidth(170);
        colCourse.setPrefWidth(230);

        TableColumn<CreditClass, Integer> colCount = new TableColumn<>("Sĩ số sinh viên");
        colCount.setCellValueFactory(new PropertyValueFactory<>("studentCount"));
        colCount.setMinWidth(100);
        colCount.setPrefWidth(125);
        colCount.setMaxWidth(145);
        colCount.setStyle("-fx-alignment: CENTER;");

        TableColumn<CreditClass, Void> colActions = new TableColumn<>("Thao tác");
        colActions.setMinWidth(175);
        colActions.setPrefWidth(185);
        colActions.setMaxWidth(200);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellFactory(col -> new TableCell<CreditClass, Void>() {
            private final Button viewBtn = new Button("Xem");
            private final Button editBtn = new Button("Sửa");
            private final Button delBtn = new Button("Xóa");
            private final HBox box = new HBox(5, viewBtn, editBtn, delBtn);
            {
                box.setAlignment(Pos.CENTER);
                viewBtn.getStyleClass().addAll("button", "btn-primary");
                viewBtn.setStyle("-fx-padding: 4px 11px; -fx-font-size: 12px;");
                editBtn.getStyleClass().addAll("button", "btn-secondary");
                editBtn.setStyle("-fx-padding: 4px 11px; -fx-font-size: 12px;");
                delBtn.getStyleClass().addAll("button", "btn-secondary");
                delBtn.setStyle("-fx-padding: 4px 11px; -fx-font-size: 12px; -fx-text-fill: #F85149; -fx-border-color: #F85149;");

                viewBtn.setOnAction(e -> {
                    e.consume();
                    CreditClass item = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (item != null && onViewClass != null) onViewClass.accept(item);
                });

                editBtn.setOnAction(e -> {
                    CreditClass item = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (item != null && onEditClass != null) {
                        onEditClass.accept(item);
                    }
                });

                delBtn.setOnAction(e -> {
                    CreditClass item = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (item != null && onDeleteClass != null) {
                        onDeleteClass.accept(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        classTable.getColumns().addAll(colCode, colName, colCourse, colCount, colActions);
        classTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        classTable.setItems(classList);
        TableViewSupport.fitHeightToRows(classTable, 15);
        VBox.setVgrow(classTable, Priority.NEVER);
        classTable.setRowFactory(table -> {
            TableRow<CreditClass> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2 && onViewClass != null) {
                    onViewClass.accept(row.getItem());
                }
            });
            return row;
        });

        pane.getChildren().addAll(toolbar, classTable);
        return pane;
    }

    private VBox buildStudentsPane() {
        VBox pane = new VBox(12);

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label filterLbl = new Label("Lớp tín chỉ:");
        filterLbl.getStyleClass().add("meta-item");

        studentFilterClassCombo = new ComboBox<>();
        studentFilterClassCombo.setPromptText("Tất cả sinh viên");
        studentFilterClassCombo.setPrefWidth(260);

        importStudentsBtn = new Button("Nhập file thông tin sinh viên");
        importStudentsBtn.getStyleClass().addAll("button", "btn-primary");

        toolbar.getChildren().addAll(filterLbl, studentFilterClassCombo, importStudentsBtn);

        studentTable = new TableView<>();
        studentTable.getStyleClass().add("table-view");
        studentTable.setPlaceholder(new Label("Chưa có sinh viên nào trong danh sách"));

        TableColumn<User.UserDTO, String> colUser = new TableColumn<>("Tên đăng nhập (Username)");
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUser.setMinWidth(165);
        colUser.setPrefWidth(190);
        colUser.setMaxWidth(225);

        TableColumn<User.UserDTO, String> colName = new TableColumn<>("Họ và tên sinh viên");
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colName.setMinWidth(230);
        colName.setPrefWidth(300);

        TableColumn<User.UserDTO, String> colClass = new TableColumn<>("Lớp tín chỉ");
        colClass.setCellValueFactory(new PropertyValueFactory<>("className"));
        colClass.setMinWidth(165);
        colClass.setPrefWidth(220);

        TableColumn<User.UserDTO, String> colDate = new TableColumn<>("Ngày cấp tài khoản");
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colDate.setMinWidth(150);
        colDate.setPrefWidth(175);
        colDate.setMaxWidth(200);

        // Cột Thao tác (Sửa, Xóa) cho sinh viên
        TableColumn<User.UserDTO, Void> colActions = new TableColumn<>("Thao tác");
        colActions.setMinWidth(130);
        colActions.setMaxWidth(160);
        colActions.setCellFactory(col -> new TableCell<User.UserDTO, Void>() {
            private final Button editBtn = new Button("Sửa");
            private final Button delBtn = new Button("Xóa");
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                box.setAlignment(Pos.CENTER);
                editBtn.getStyleClass().addAll("button", "btn-secondary");
                editBtn.setStyle("-fx-padding: 4px 11px; -fx-font-size: 12px;");
                delBtn.getStyleClass().addAll("button", "btn-secondary");
                delBtn.setStyle("-fx-padding: 4px 11px; -fx-font-size: 12px; -fx-text-fill: #F85149; -fx-border-color: #F85149;");

                editBtn.setOnAction(e -> {
                    User.UserDTO item = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (item != null && onEditStudent != null) {
                        onEditStudent.accept(item);
                    }
                });

                delBtn.setOnAction(e -> {
                    User.UserDTO item = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (item != null && onDeleteStudent != null) {
                        onDeleteStudent.accept(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        studentTable.getColumns().addAll(colUser, colName, colClass, colDate, colActions);
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        studentTable.setItems(studentList);
        TableViewSupport.fitHeightToRows(studentTable, 15);
        VBox.setVgrow(studentTable, Priority.NEVER);

        // Thanh phân trang Sinh viên
        HBox studentPaginationBar = new HBox(12);
        studentPaginationBar.setAlignment(Pos.CENTER_RIGHT);
        studentPaginationBar.setPadding(new Insets(6, 0, 0, 0));

        studentPrevBtn = new Button("◀ Trang trước");
        studentPrevBtn.getStyleClass().addAll("button", "btn-secondary");
        studentPrevBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        studentPrevBtn.setOnAction(e -> {
            if (currentStudentPage > 0) {
                currentStudentPage--;
                updateStudentPageView();
            }
        });

        studentPageLbl = new Label("Trang 1 / 1 (Tổng số: 0 sinh viên)");
        studentPageLbl.setStyle("-fx-text-fill: #858585; -fx-font-size: 12px;");

        studentNextBtn = new Button("Trang sau ▶");
        studentNextBtn.getStyleClass().addAll("button", "btn-secondary");
        studentNextBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        studentNextBtn.setOnAction(e -> {
            int totalPages = Math.max(1, (int) Math.ceil((double) rawStudentList.size() / PAGE_SIZE));
            if (currentStudentPage + 1 < totalPages) {
                currentStudentPage++;
                updateStudentPageView();
            }
        });

        studentPaginationBar.getChildren().addAll(studentPageLbl, studentPrevBtn, studentNextBtn);

        pane.getChildren().addAll(toolbar, studentTable, studentPaginationBar);
        return pane;
    }

    private VBox buildProblemsPane() {
        VBox pane = new VBox(12);

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        addProblemBtn = new Button("+ Thêm bài tập mới");
        addProblemBtn.getStyleClass().addAll("button", "btn-primary");

        toolbar.getChildren().addAll(addProblemBtn);

        problemTable = new TableView<>();
        problemTable.getStyleClass().add("table-view");
        problemTable.setPlaceholder(new Label("Chưa có bài tập nào trong kho bài tập"));

        TableColumn<Problem, String> colTitle = new TableColumn<>("Tiêu đề bài tập");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setMinWidth(240);
        colTitle.setPrefWidth(330);

        TableColumn<Problem, String> colType = new TableColumn<>("Dạng bài");
        colType.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isJavaOop() ? "Java OOP" : "Thuật toán I/O"
        ));
        colType.setMinWidth(115);
        colType.setPrefWidth(130);
        colType.setMaxWidth(155);
        colType.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> colDiff = new TableColumn<>("Độ khó");
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colDiff.setMinWidth(90);
        colDiff.setPrefWidth(105);
        colDiff.setMaxWidth(125);
        colDiff.setStyle("-fx-alignment: CENTER;");


        // Cột Trạng thái có ô tick CheckBox cho từng bài tập
        TableColumn<Problem, Boolean> colStatus = new TableColumn<>("Trạng thái");
        colStatus.setMinWidth(110);
        colStatus.setMaxWidth(140);
        colStatus.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isActive()));
        colStatus.setCellFactory(col -> new TableCell<Problem, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    Problem p = (getTableRow() != null) ? getTableRow().getItem() : null;
                    if (p != null) {
                        boolean selected = checkBox.isSelected();
                        p.setActive(selected);
                        updateStyle(selected);
                        if (onToggleProblemActive != null) {
                            onToggleProblemActive.accept(p, selected);
                        }
                    }
                });
            }

            private void updateStyle(boolean active) {
                if (active) {
                    checkBox.setText("Đang mở");
                    checkBox.setStyle("-fx-cursor: hand; -fx-text-fill: #2ECC71; -fx-font-weight: bold; -fx-font-size: 12px;");
                } else {
                    checkBox.setText("Đã ẩn");
                    checkBox.setStyle("-fx-cursor: hand; -fx-text-fill: #858585; -fx-font-size: 12px;");
                }
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Problem p = getTableRow().getItem();
                    checkBox.setSelected(p.isActive());
                    updateStyle(p.isActive());
                    setGraphic(checkBox);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Problem, String> colTime = new TableColumn<>("Thời gian chạy");
        colTime.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTimeLimitFormatted()));
        colTime.setMinWidth(110);
        colTime.setPrefWidth(125);
        colTime.setMaxWidth(145);
        colTime.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> colMem = new TableColumn<>("Bộ nhớ tối đa");
        colMem.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMemoryLimitFormatted()));
        colMem.setMinWidth(110);
        colMem.setPrefWidth(125);
        colMem.setMaxWidth(145);
        colMem.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, Void> colActions = new TableColumn<>("Thao tác");
        colActions.setMinWidth(135);
        colActions.setPrefWidth(145);
        colActions.setMaxWidth(160);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Sửa");
            private final Button deleteBtn = new Button("Xóa");
            private final HBox actions = new HBox(6, editBtn, deleteBtn);
            {
                actions.setAlignment(Pos.CENTER);
                editBtn.getStyleClass().addAll("button", "btn-secondary");
                deleteBtn.getStyleClass().addAll("button", "btn-danger");
                editBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                deleteBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                editBtn.setOnAction(e -> {
                    Problem problem = getTableRow() == null ? null : getTableRow().getItem();
                    if (onEditProblem != null && problem != null) onEditProblem.accept(problem);
                });
                deleteBtn.setOnAction(e -> {
                    Problem problem = getTableRow() == null ? null : getTableRow().getItem();
                    if (onDeleteProblem != null && problem != null) onDeleteProblem.accept(problem);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
                setAlignment(Pos.CENTER);
            }
        });
        problemTable.getColumns().addAll(colTitle, colType, colDiff, colStatus, colTime, colMem, colActions);
        problemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        problemTable.setItems(problemList);
        TableViewSupport.fitHeightToRows(problemTable, 15);
        VBox.setVgrow(problemTable, Priority.NEVER);

        // Thanh phân trang Bài tập
        HBox problemPaginationBar = new HBox(12);
        problemPaginationBar.setAlignment(Pos.CENTER_RIGHT);
        problemPaginationBar.setPadding(new Insets(6, 0, 0, 0));

        problemPrevBtn = new Button("◀ Trang trước");
        problemPrevBtn.getStyleClass().addAll("button", "btn-secondary");
        problemPrevBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        problemPrevBtn.setOnAction(e -> {
            if (currentProblemPage > 0) {
                currentProblemPage--;
                updateProblemPageView();
            }
        });

        problemPageLbl = new Label("Trang 1 / 1 (Tổng số: 0 bài tập)");
        problemPageLbl.setStyle("-fx-text-fill: #858585; -fx-font-size: 12px;");

        problemNextBtn = new Button("Trang sau ▶");
        problemNextBtn.getStyleClass().addAll("button", "btn-secondary");
        problemNextBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        problemNextBtn.setOnAction(e -> {
            int totalPages = Math.max(1, (int) Math.ceil((double) rawProblemList.size() / PAGE_SIZE));
            if (currentProblemPage + 1 < totalPages) {
                currentProblemPage++;
                updateProblemPageView();
            }
        });

        problemPaginationBar.getChildren().addAll(problemPageLbl, problemPrevBtn, problemNextBtn);

        pane.getChildren().addAll(toolbar, problemTable, problemPaginationBar);
        return pane;
    }

    private void showProfilePopup() {
        if (profilePopup == null || !profilePopup.isShowing()) {
            profilePopup = new ProfilePopup(currentUser);
            profilePopup.setOnLogout(onLogout);
            javafx.geometry.Point2D point = avatarBtn.localToScreen(0, avatarBtn.getHeight() + 4);
            if (point != null) {
                profilePopup.show(avatarBtn, point.getX() - 240, point.getY());
            }
        } else {
            profilePopup.hide();
        }
    }

    // ─── GETTERS & SETTERS ────────────────────────────────────────────────────

    public void setCourses(List<Course> list) {
        courseList.setAll(list);
    }

    public void setClasses(List<CreditClass> list) {
        classList.setAll(list);

        // Cập nhật ComboBox lọc sinh viên
        CreditClass currentStudentSel = studentFilterClassCombo.getSelectionModel().getSelectedItem();
        studentFilterClassCombo.setItems(FXCollections.observableArrayList(list));
        if (currentStudentSel != null) studentFilterClassCombo.getSelectionModel().select(currentStudentSel);
    }

    private void updateStudentPageView() {
        int total = rawStudentList.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (currentStudentPage >= totalPages) {
            currentStudentPage = totalPages - 1;
        }
        if (currentStudentPage < 0) {
            currentStudentPage = 0;
        }

        int fromIndex = currentStudentPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        if (fromIndex < total) {
            studentList.setAll(rawStudentList.subList(fromIndex, toIndex));
        } else {
            studentList.clear();
        }

        studentPageLbl.setText(String.format("Trang %d / %d (Tổng số: %d sinh viên)", currentStudentPage + 1, totalPages, total));
        studentPrevBtn.setDisable(currentStudentPage <= 0);
        studentNextBtn.setDisable(currentStudentPage + 1 >= totalPages);
    }

    private void updateProblemPageView() {
        int total = rawProblemList.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (currentProblemPage >= totalPages) {
            currentProblemPage = totalPages - 1;
        }
        if (currentProblemPage < 0) {
            currentProblemPage = 0;
        }

        int fromIndex = currentProblemPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        if (fromIndex < total) {
            problemList.setAll(rawProblemList.subList(fromIndex, toIndex));
        } else {
            problemList.clear();
        }

        problemPageLbl.setText(String.format("Trang %d / %d (Tổng số: %d bài tập)", currentProblemPage + 1, totalPages, total));
        problemPrevBtn.setDisable(currentProblemPage <= 0);
        problemNextBtn.setDisable(currentProblemPage + 1 >= totalPages);
    }

    public void setStudents(List<User.UserDTO> list) {
        rawStudentList = list != null ? new java.util.ArrayList<>(list) : new java.util.ArrayList<>();
        currentStudentPage = 0;
        updateStudentPageView();
    }

    public void setProblems(List<Problem> list) {
        rawProblemList = list != null ? new java.util.ArrayList<>(list) : new java.util.ArrayList<>();
        currentProblemPage = 0;
        updateProblemPageView();
    }

    public Button getAddCourseBtn() { return addCourseBtn; }

    public void setOnDeleteCourse(Consumer<Course> onDeleteCourse) { this.onDeleteCourse = onDeleteCourse; }
    public void setOnEditCourse(Consumer<Course> onEditCourse) { this.onEditCourse = onEditCourse; }

    public Button getAddClassBtn() { return addClassBtn; }

    public ComboBox<CreditClass> getStudentFilterClassCombo() { return studentFilterClassCombo; }
    public Button getImportStudentsBtn() { return importStudentsBtn; }

    public ComboBox<CreditClass> getProblemFilterClassCombo() { return problemFilterClassCombo; }
    public Button getAddProblemBtn() { return addProblemBtn; }

    public void setOnEditClass(Consumer<CreditClass> onEditClass) {
        this.onEditClass = onEditClass;
    }

    public void setOnDeleteClass(Consumer<CreditClass> onDeleteClass) {
        this.onDeleteClass = onDeleteClass;
    }

    public void setOnViewClass(Consumer<CreditClass> onViewClass) {
        this.onViewClass = onViewClass;
    }

    public void setOnEditStudent(Consumer<User.UserDTO> onEditStudent) {
        this.onEditStudent = onEditStudent;
    }

    public void setOnDeleteStudent(Consumer<User.UserDTO> onDeleteStudent) {
        this.onDeleteStudent = onDeleteStudent;
    }

    public void setOnToggleProblemActive(java.util.function.BiConsumer<Problem, Boolean> onToggleProblemActive) {
        this.onToggleProblemActive = onToggleProblemActive;
    }

    public void setOnEditProblem(Consumer<Problem> onEditProblem) { this.onEditProblem = onEditProblem; }
    public void setOnDeleteProblem(Consumer<Problem> onDeleteProblem) { this.onDeleteProblem = onDeleteProblem; }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public void showProctorAlertToast(String alertMessage) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Hệ Thống Giám Sát Phòng Thi (Proctoring)");
        alert.setHeaderText("Cảnh báo vi phạm từ sinh viên!");
        alert.setContentText(alertMessage);
        alert.show();
    }
}
