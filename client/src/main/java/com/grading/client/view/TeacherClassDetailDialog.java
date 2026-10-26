package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/** Trang chi tiết lớp tín chỉ: sinh viên, cấu hình kiểm tra và cảnh báo giám sát. */
public class TeacherClassDetailDialog extends Dialog<Void> {
    private final Stage owner;
    private final CreditClass creditClass;
    private final SocketClient client = SessionManager.getInstance().getSocketClient();
    private final ObservableList<User.UserDTO> students = FXCollections.observableArrayList();
    private final ObservableList<Problem> problems = FXCollections.observableArrayList();
    private final ObservableList<AlertRow> alerts = FXCollections.observableArrayList();
    private final ObservableList<AssessmentRow> assessments = FXCollections.observableArrayList();
    private final Set<Integer> studentIds = new HashSet<>();
    private final Map<Integer, Integer> solvedCounts = new HashMap<>();

    private TableView<User.UserDTO> studentTable;
    private TableView<Problem> problemTable;
    private TableView<AlertRow> alertTable;
    private TableView<AssessmentRow> assessmentTable;
    private Tab studentsTab;
    private Tab classProblemsTab;
    private TableView<Problem> classProblemTable;

    public TeacherClassDetailDialog(Stage owner, CreditClass creditClass) {
        this.owner = owner;
        this.creditClass = creditClass;
        initOwner(owner);
        setTitle("Quản lý lớp tín chỉ");
        build();
        loadStudents();
        loadProblems();
        loadAssessments();
    }

    private void build() {
        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }

        setResizable(true);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        pane.setPrefSize(visualBounds.getWidth(), visualBounds.getHeight());

        setOnShowing(event -> {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.setX(visualBounds.getMinX());
            stage.setY(visualBounds.getMinY());
            stage.setWidth(visualBounds.getWidth());
            stage.setHeight(visualBounds.getHeight());
            stage.setMaximized(true);
        });

        setOnShown(event -> {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.setMaximized(true);
            Platform.runLater(() -> stage.setMaximized(true));
        });

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        Label heading = new Label(creditClass.getClassCode() + " - " + creditClass.getName());
        heading.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label subtitle = new Label("Quản lý sinh viên, bài tập của lớp, bài kiểm tra và theo dõi tiến độ theo thời gian thực.");
        subtitle.getStyleClass().add("meta-item");

        ButtonType createAssessmentType = new ButtonType("Tạo bài kiểm tra", ButtonBar.ButtonData.APPLY);
        ButtonType closeType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(createAssessmentType, closeType);
        Button createAssessmentButton = (Button) pane.lookupButton(createAssessmentType);
        createAssessmentButton.getStyleClass().addAll("button", "btn-primary");

        TabPane tabs = new TabPane();
        Tab assessmentTab = buildAssessmentTab(createAssessmentButton);
        Tab monitorTab = buildAssessmentMonitorTab();
        tabs.getTabs().addAll(buildStudentsTab(), buildClassProblemsTab(), assessmentTab, monitorTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Runnable updateAssessmentButton = () -> {
            boolean assessmentSelected = tabs.getSelectionModel().getSelectedItem() == assessmentTab;
            createAssessmentButton.setVisible(assessmentSelected);
            createAssessmentButton.setManaged(assessmentSelected);
        };
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateAssessmentButton.run());
        updateAssessmentButton.run();
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(heading, subtitle, tabs);
        VBox.setVgrow(root, Priority.ALWAYS);
        pane.setContent(root);
    }

    private Tab buildStudentsTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        Button importBtn = new Button("Nhập file thông tin sinh viên");
        importBtn.getStyleClass().addAll("button", "btn-primary");
        importBtn.setOnAction(e -> importStudents());

        studentTable = new TableView<>(students);
        studentTable.getStyleClass().add("table-view");
        studentTable.setPlaceholder(new Label("Lớp chưa có sinh viên."));
        TableColumn<User.UserDTO, String> username = new TableColumn<>("Tên đăng nhập");
        username.setCellValueFactory(new PropertyValueFactory<>("username"));
        username.setMinWidth(180);
        username.setPrefWidth(230);
        username.setMaxWidth(280);
        TableColumn<User.UserDTO, String> name = new TableColumn<>("Họ và tên");
        name.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        name.setMinWidth(260);
        name.setPrefWidth(390);
        TableColumn<User.UserDTO, String> solved = new TableColumn<>("Bài làm đúng");
        solved.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(solvedCounts.getOrDefault(data.getValue().getId(), 0))));
        solved.setMinWidth(120);
        solved.setPrefWidth(140);
        solved.setMaxWidth(160);
        solved.setStyle("-fx-alignment: CENTER;");
        TableColumn<User.UserDTO, Void> actions = new TableColumn<>("Thao tác");
        actions.setMinWidth(170);
        actions.setPrefWidth(180);
        actions.setMaxWidth(200);
        actions.setStyle("-fx-alignment: CENTER;");
        actions.setCellFactory(col -> new TableCell<>() {
            private final Button edit = new Button("Sửa");
            private final Button delete = new Button("Xóa");
            private final HBox buttons = new HBox(8, edit, delete);
            {
                buttons.setAlignment(Pos.CENTER);
                edit.getStyleClass().addAll("button", "btn-secondary");
                delete.getStyleClass().addAll("button", "btn-danger");
                edit.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                delete.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                edit.setOnAction(e -> editStudent(getTableView().getItems().get(getIndex())));
                delete.setOnAction(e -> deleteStudent(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
                setAlignment(Pos.CENTER);
            }
        });
        studentTable.getColumns().addAll(username, name, solved, actions);
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(studentTable, Priority.ALWAYS);
        box.getChildren().addAll(importBtn, studentTable);
        VBox.setVgrow(box, Priority.ALWAYS);
        studentsTab = new Tab("Sinh viên (" + creditClass.getStudentCount() + ")", box);
        return studentsTab;
    }

    private Tab buildClassProblemsTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label countLabel = new Label("Danh sách bài tập được gán vào lớp");
        countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button assignBtn = new Button("➕ Gán bài tập vào lớp");
        assignBtn.getStyleClass().addAll("button", "btn-primary");
        assignBtn.setOnAction(e -> showAssignProblemsDialog());

        topBar.getChildren().addAll(countLabel, spacer, assignBtn);

        classProblemTable = new TableView<>(problems);
        classProblemTable.getStyleClass().add("table-view");
        classProblemTable.setPlaceholder(new Label("Lớp chưa được gán bài tập nào. Hãy bấm 'Gán bài tập vào lớp' để thêm từ kho chung."));

        TableColumn<Problem, Number> idCol = new TableColumn<>("#");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMinWidth(60);
        idCol.setMaxWidth(80);
        idCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> titleCol = new TableColumn<>("Tiêu đề bài toán");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setMinWidth(250);

        TableColumn<Problem, String> typeCol = new TableColumn<>("Loại bài");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isJavaOop() ? "Java OOP (Soi cấu trúc)" : "I/O Matching (Chuẩn)"
        ));
        typeCol.setMinWidth(160);
        typeCol.setMaxWidth(200);
        typeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> diffCol = new TableColumn<>("Độ khó");
        diffCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        diffCol.setMinWidth(100);
        diffCol.setMaxWidth(130);
        diffCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, Void> actionCol = new TableColumn<>("Thao tác");
        actionCol.setMinWidth(140);
        actionCol.setMaxWidth(160);
        actionCol.setStyle("-fx-alignment: CENTER;");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Gỡ khỏi lớp");
            {
                removeBtn.getStyleClass().addAll("button", "btn-danger");
                removeBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 10px;");
                removeBtn.setOnAction(e -> {
                    Problem p = getTableView().getItems().get(getIndex());
                    handleRemoveProblemFromClass(p);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
                setAlignment(Pos.CENTER);
            }
        });

        classProblemTable.getColumns().addAll(idCol, titleCol, typeCol, diffCol, actionCol);
        classProblemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(classProblemTable, Priority.ALWAYS);

        box.getChildren().addAll(topBar, classProblemTable);
        VBox.setVgrow(box, Priority.ALWAYS);

        classProblemsTab = new Tab("Bài tập của lớp (" + problems.size() + ")", box);
        return classProblemsTab;
    }

    private void handleRemoveProblemFromClass(Problem problem) {
        if (problem == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("Xác nhận gỡ bài");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc chắn muốn gỡ bài '" + problem.getTitle() + "' khỏi lớp tín chỉ này không?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                client.removeClassProblem(creditClass.getId(), problem.getId(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            loadProblems();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(owner);
                            alert.setTitle("Lỗi");
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Không thể gỡ bài tập khỏi lớp.");
                            alert.showAndWait();
                        }
                    });
                });
            }
        });
    }

    private void showAssignProblemsDialog() {
        Dialog<List<Integer>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Gán bài tập từ kho chung vào lớp");
        dialog.getDialogPane().getStyleClass().add("card");
        ThemeManager.getInstance().applyCurrentZoom(dialog.getDialogPane());
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) dialog.getDialogPane().getStylesheets().setAll(res.toExternalForm());
        dialog.getDialogPane().setPrefSize(700, 500);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label info = new Label("Chọn các bài tập từ kho chung để gán vào lớp '" + creditClass.getName() + "':");
        info.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-bright;");

        ObservableList<Problem> allProblems = FXCollections.observableArrayList();
        Set<Integer> currentProblemIds = new HashSet<>();
        for (Problem p : problems) currentProblemIds.add(p.getId());

        TableView<Problem> table = new TableView<>(allProblems);
        table.setPlaceholder(new Label("Đang tải danh sách bài tập..."));

        TableColumn<Problem, Boolean> checkCol = new TableColumn<>("Chọn");
        checkCol.setMinWidth(70);
        checkCol.setMaxWidth(80);
        checkCol.setStyle("-fx-alignment: CENTER;");
        checkCol.setCellValueFactory(data -> new javafx.beans.property.SimpleBooleanProperty(data.getValue().isActive()));
        checkCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(e -> getTableView().getItems().get(getIndex()).setActive(check.isSelected()));
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    check.setSelected(Boolean.TRUE.equals(item));
                    setGraphic(check);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Problem, Number> idCol = new TableColumn<>("#");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setMinWidth(50);
        idCol.setMaxWidth(70);
        idCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> titleCol = new TableColumn<>("Tiêu đề");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setMinWidth(250);

        TableColumn<Problem, String> typeCol = new TableColumn<>("Loại bài");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isJavaOop() ? "Java OOP" : "I/O Matching"
        ));
        typeCol.setMinWidth(120);
        typeCol.setMaxWidth(140);
        typeCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, String> diffCol = new TableColumn<>("Độ khó");
        diffCol.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        diffCol.setMinWidth(90);
        diffCol.setMaxWidth(110);
        diffCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(checkCol, idCol, titleCol, typeCol, diffCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(info, table);
        dialog.getDialogPane().setContent(content);

        ButtonType assignType = new ButtonType("Gán vào lớp", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignType, ButtonType.CANCEL);
        Button assignBtn = (Button) dialog.getDialogPane().lookupButton(assignType);
        assignBtn.getStyleClass().addAll("button", "btn-primary");

        client.getProblems(0, response -> Platform.runLater(() -> {
            if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
                List<Problem> list = new ArrayList<>();
                for (JsonElement el : response.getData().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    int pid = o.has("id") ? o.get("id").getAsInt() : 0;
                    if (currentProblemIds.contains(pid)) continue;
                    Problem p = new Problem();
                    p.setId(pid);
                    if (o.has("title")) p.setTitle(o.get("title").getAsString());
                    if (o.has("difficulty")) p.setDifficulty(o.get("difficulty").getAsString());
                    if (o.has("problemType")) p.setProblemType(o.get("problemType").getAsString());
                    p.setActive(false);
                    list.add(p);
                }
                allProblems.setAll(list);
                if (list.isEmpty()) {
                    table.setPlaceholder(new Label("Tất cả bài tập trong kho đã được gán vào lớp này."));
                }
            }
        }));

        dialog.setResultConverter(btn -> {
            if (btn == assignType) {
                List<Integer> selectedIds = new ArrayList<>();
                for (Problem p : allProblems) {
                    if (p.isActive()) selectedIds.add(p.getId());
                }
                return selectedIds;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selectedIds -> {
            if (!selectedIds.isEmpty()) {
                client.assignClassProblems(creditClass.getId(), selectedIds, response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            loadProblems();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(owner);
                            alert.setTitle("Lỗi");
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Không thể gán bài tập vào lớp.");
                            alert.showAndWait();
                        }
                    });
                });
            }
        });
    }

    private Tab buildAssessmentTab(Button createAssessmentButton) {
        VBox box = new VBox(16);
        box.setPadding(new Insets(16));
        GridPane config = new GridPane();
        config.setHgap(18);
        config.setVgap(14);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(115);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        config.getColumnConstraints().addAll(labelColumn, fieldColumn, labelColumn, fieldColumn);

        TextField title = new TextField();
        title.setPromptText("Nhập tên bài kiểm tra");
        LocalDateTime initialStart = LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0);
        LocalDateTime initialClose = initialStart.plusMinutes(60);
        DatePicker openDate = new DatePicker(initialStart.toLocalDate());
        DatePicker closeDate = new DatePicker(initialClose.toLocalDate());
        Spinner<Integer> openHour = new Spinner<>(0, 23, initialStart.getHour());
        Spinner<Integer> openMinute = new Spinner<>(0, 59, initialStart.getMinute());
        Spinner<Integer> closeHour = new Spinner<>(0, 23, initialClose.getHour());
        Spinner<Integer> closeMinute = new Spinner<>(0, 59, initialClose.getMinute());
        Spinner<Integer> duration = new Spinner<>(5, 300, 60, 5);
        for (Spinner<Integer> spinner : List.of(openHour, openMinute, closeHour, closeMinute, duration)) spinner.setEditable(true);
        config.add(new Label("Tên kiểm tra:"), 0, 0);
        config.add(title, 1, 0, 3, 1);
        config.add(new Label("Bắt đầu:"), 0, 1);
        config.add(dateTimeBox(openDate, openHour, openMinute), 1, 1);
        config.add(new Label("Kết thúc:"), 2, 1);
        config.add(dateTimeBox(closeDate, closeHour, closeMinute), 3, 1);
        config.add(new Label("Thời lượng:"), 0, 2);
        HBox durationBox = new HBox(8, duration, new Label("phút"));
        durationBox.setAlignment(Pos.CENTER_LEFT);
        config.add(durationBox, 1, 2);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        problemTable = new TableView<>(problems);
        problemTable.getStyleClass().add("table-view");
        TableColumn<Problem, String> problemTitle = new TableColumn<>("Bài tập");
        problemTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        problemTitle.setMinWidth(360);
        problemTitle.setPrefWidth(520);
        TableColumn<Problem, String> difficulty = new TableColumn<>("Độ khó");
        difficulty.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        difficulty.setMinWidth(110);
        difficulty.setMaxWidth(150);
        difficulty.setStyle("-fx-alignment: CENTER;");
        TableColumn<Problem, Void> view = new TableColumn<>("Xem");
        view.setMinWidth(90);
        view.setMaxWidth(110);
        view.setStyle("-fx-alignment: CENTER;");
        view.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("Xem đề");
            {
                button.getStyleClass().addAll("button", "btn-secondary");
                button.setStyle("-fx-font-size: 12px; -fx-padding: 4px 11px;");
                button.setOnAction(e -> showProblem(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
                setAlignment(Pos.CENTER);
            }
        });
        TableColumn<Problem, Boolean> choose = new TableColumn<>("Chọn bài");
        choose.setMinWidth(90);
        choose.setMaxWidth(110);
        choose.setStyle("-fx-alignment: CENTER;");
        choose.setCellValueFactory(data -> new javafx.beans.property.SimpleBooleanProperty(data.getValue().isActive()));
        choose.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            { check.setOnAction(e -> getTableView().getItems().get(getIndex()).setActive(check.isSelected())); }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else { check.setSelected(Boolean.TRUE.equals(item)); setGraphic(check); setAlignment(Pos.CENTER); }
            }
        });
        problemTable.getColumns().addAll(problemTitle, difficulty, view, choose);
        problemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        problemTable.setRowFactory(table -> {
            TableRow<Problem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) showProblem(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        createAssessmentButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            String assessmentTitle = title.getText() == null ? "" : title.getText().trim();
            if (assessmentTitle.isEmpty()) {
                validation.setText("Tên bài kiểm tra không được để trống.");
                title.requestFocus();
                return;
            }
            LocalDateTime opensAt = LocalDateTime.of(openDate.getValue(), LocalTime.of(openHour.getValue(), openMinute.getValue()));
            LocalDateTime closesAt = LocalDateTime.of(closeDate.getValue(), LocalTime.of(closeHour.getValue(), closeMinute.getValue()));
            if (!closesAt.isAfter(opensAt)) {
                validation.setText("Thời gian kết thúc phải sau thời gian bắt đầu.");
                return;
            }
            if (Duration.between(opensAt, closesAt).toMinutes() < duration.getValue()) {
                validation.setText("Khoảng mở bài phải dài ít nhất bằng thời lượng kiểm tra.");
                return;
            }
            JsonArray selectedIds = new JsonArray();
            problems.stream().filter(Problem::isActive).forEach(p -> selectedIds.add(p.getId()));
            if (selectedIds.isEmpty()) {
                validation.setText("Hãy chọn ít nhất một bài tập.");
                return;
            }
            validation.setText("");
            createAssessmentButton.setDisable(true);
            client.createAssessment(creditClass.getId(), assessmentTitle, opensAt.toString(), closesAt.toString(),
                    duration.getValue(), selectedIds, response -> Platform.runLater(() -> {
                        createAssessmentButton.setDisable(false);
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            loadAssessments();
                            Alert notice = new Alert(Alert.AlertType.INFORMATION, response.getMessage(), ButtonType.OK);
                            notice.initOwner(owner);
                            notice.showAndWait();
                        } else {
                            validation.setText(response.getMessage() != null ? response.getMessage() : "Không thể lưu bài kiểm tra.");
                        }
                    }));
        });
        box.getChildren().addAll(config, validation, problemTable);
        VBox.setVgrow(problemTable, Priority.ALWAYS);
        return new Tab("Tạo bài kiểm tra", box);
    }

    private Tab buildAssessmentMonitorTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        Label existingTitle = new Label("Các bài kiểm tra đã tạo");
        existingTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label hint = new Label("Nhấp đúp vào một bài kiểm tra hoặc dùng nút bên phải để mở trang giám sát và lịch sử.");
        hint.getStyleClass().add("meta-item");
        assessmentTable = new TableView<>(assessments);
        assessmentTable.setPlaceholder(new Label("Chưa có bài kiểm tra nào."));
        assessmentTable.getColumns().addAll(
                assessmentColumn("Tên bài kiểm tra", AssessmentRow::title, 250),
                assessmentColumn("Trạng thái", AssessmentRow::status, 120),
                assessmentColumn("Thời gian", AssessmentRow::window, 270),
                assessmentColumn("Tiến độ", AssessmentRow::progress, 160),
                assessmentActionColumn());
        assessmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        assessmentTable.setRowFactory(table -> {
            TableRow<AssessmentRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openAssessmentMonitor(row.getItem());
            });
            return row;
        });
        Button refresh = new Button("Làm mới");
        refresh.getStyleClass().addAll("button", "btn-secondary");
        refresh.setOnAction(e -> loadAssessments());
        HBox header = new HBox(10, existingTitle, new Region(), refresh);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(header, hint, assessmentTable);
        VBox.setVgrow(assessmentTable, Priority.ALWAYS);
        return new Tab("Giám sát kiểm tra", box);
    }

    private TableColumn<AssessmentRow, String> assessmentColumn(String title,
            java.util.function.Function<AssessmentRow, String> getter, double width) {
        TableColumn<AssessmentRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<AssessmentRow, Void> assessmentActionColumn() {
        TableColumn<AssessmentRow, Void> column = new TableColumn<>("Trạng thái / Thao tác");
        column.setPrefWidth(255);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button stateButton = new Button();
            private final Button actionButton = new Button();
            private final HBox buttons = new HBox(8, stateButton, actionButton);
            {
                buttons.setAlignment(Pos.CENTER);
                stateButton.getStyleClass().addAll("button", "btn-secondary");
                stateButton.setDisable(true);
                actionButton.getStyleClass().addAll("button", "btn-primary");
                actionButton.setOnAction(e -> openAssessmentMonitor(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    AssessmentRow row = getTableView().getItems().get(getIndex());
                    boolean active = "ACTIVE".equalsIgnoreCase(row.rawStatus());
                    boolean expired = "EXPIRED".equalsIgnoreCase(row.rawStatus());
                    stateButton.setText(active ? "Đang diễn ra" : expired ? "Đã kết thúc" : "Sắp diễn ra");
                    actionButton.setText(expired ? "Xem lịch sử" : "Mở giám sát");
                }
                setGraphic(empty ? null : buttons);
                setAlignment(Pos.CENTER);
            }
        });
        return column;
    }

    private void openAssessmentMonitor(AssessmentRow row) {
        if (row == null) return;
        new TeacherAssessmentMonitorDialog(owner, row.id(), row.title()).showAndWait();
        loadAssessments();
    }

    private HBox dateTimeBox(DatePicker date, Spinner<Integer> hour, Spinner<Integer> minute) {
        date.setPrefWidth(145);
        hour.setPrefWidth(72);
        minute.setPrefWidth(72);
        HBox box = new HBox(7, date, hour, new Label(":"), minute);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void showProblem(Problem problem) {
        if (problem == null || client == null) return;
        client.getProblemDetail(problem.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            JsonObject data = response.getData().getAsJsonObject();
            Dialog<Void> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("Đề bài: " + (data.has("title") ? data.get("title").getAsString() : problem.getTitle()));
            TextArea statement = new TextArea(data.has("description") ? data.get("description").getAsString() : "");
            statement.setEditable(false);
            statement.setWrapText(true);
            statement.setPrefSize(680, 430);
            Label meta = new Label("Độ khó: " + problem.getDifficulty());
            dialog.getDialogPane().setContent(new VBox(10, meta, statement));
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        }));
    }

    private Tab buildAlertsTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        HBox bar = new HBox(10);
        Label help = new Label("Các hành vi bất thường của sinh viên trong lớp: rời cửa sổ, mở công cụ AI, v.v.");
        help.getStyleClass().add("meta-item");
        Button refresh = new Button("Làm mới");
        refresh.getStyleClass().addAll("button", "btn-secondary");
        refresh.setOnAction(e -> loadAlerts());
        bar.getChildren().addAll(help, new Region(), refresh);
        HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);

        alertTable = new TableView<>(alerts);
        alertTable.getStyleClass().add("table-view");
        alertTable.setPlaceholder(new Label("Chưa có cảnh báo nào trong lớp."));
        alertTable.getColumns().addAll(
                column("Thời điểm", AlertRow::time),
                column("Sinh viên", AlertRow::student),
                column("Sự kiện", AlertRow::event),
                column("Công cụ / Chi tiết", AlertRow::tool),
                column("Mức độ", AlertRow::severity),
                column("Bài tập", AlertRow::problem));
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(alertTable, Priority.ALWAYS);
        box.getChildren().addAll(bar, alertTable);
        VBox.setVgrow(box, Priority.ALWAYS);
        return new Tab("Cảnh báo", box);
    }

    private TableColumn<AlertRow, String> column(String name, java.util.function.Function<AlertRow, String> value) {
        TableColumn<AlertRow, String> col = new TableColumn<>(name);
        col.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        switch (name) {
            case "Thời điểm" -> { col.setMinWidth(155); col.setPrefWidth(175); col.setMaxWidth(200); }
            case "Sinh viên" -> { col.setMinWidth(160); col.setPrefWidth(210); }
            case "Sự kiện" -> { col.setMinWidth(130); col.setPrefWidth(160); }
            case "Công cụ / Chi tiết" -> { col.setMinWidth(200); col.setPrefWidth(260); }
            case "Mức độ" -> { col.setMinWidth(80); col.setPrefWidth(95); col.setMaxWidth(115); col.setStyle("-fx-alignment: CENTER;"); }
            case "Bài tập" -> { col.setMinWidth(160); col.setPrefWidth(210); }
            default -> col.setPrefWidth(160);
        }
        return col;
    }

    private void loadStudents() {
        if (client == null || !client.isConnected()) return;
        client.getStudents(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null
                    || !response.getData().isJsonArray()) {
                students.clear();
                studentIds.clear();
                studentTable.setPlaceholder(new Label(response.getMessage() == null
                        ? "Không thể tải danh sách sinh viên." : response.getMessage()));
                if (studentsTab != null) studentsTab.setText("Sinh viên (0)");
                return;
            }
            List<User.UserDTO> list = new ArrayList<>(); studentIds.clear();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); User.UserDTO user = new User.UserDTO();
                if (o.has("id")) user.setId(o.get("id").getAsInt());
                if (o.has("username")) user.setUsername(o.get("username").getAsString());
                if (o.has("fullName")) user.setFullName(o.get("fullName").getAsString());
                if (o.has("createdAt")) user.setCreatedAt(o.get("createdAt").getAsString());
                list.add(user); studentIds.add(user.getId());
            }
            students.setAll(list);
            creditClass.setStudentCount(list.size());
            if (studentsTab != null) studentsTab.setText("Sinh viên (" + list.size() + ")");
            loadStats();
        }));
    }

    private void loadStats() {
        if (client == null || !client.isConnected()) return;
        client.getClassStudentStats(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            solvedCounts.clear();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("studentId") && o.has("solvedCount")) solvedCounts.put(o.get("studentId").getAsInt(), o.get("solvedCount").getAsInt());
            }
            studentTable.refresh();
        }));
    }

    private void editStudent(User.UserDTO student) {
        TextInputDialog dialog = new TextInputDialog(student.getFullName());
        dialog.initOwner(owner);
        dialog.setTitle("Sửa sinh viên");
        dialog.setHeaderText(student.getUsername());
        dialog.setContentText("Họ và tên:");
        dialog.showAndWait().ifPresent(name -> client.updateStudent(student.getId(), name, creditClass.getId(), null,
                response -> Platform.runLater(() -> { if ("SUCCESS".equalsIgnoreCase(response.getStatus())) loadStudents(); })));
    }

    private void deleteStudent(User.UserDTO student) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Gỡ sinh viên " + student.getFullName() + " khỏi lớp " + creditClass.getClassCode() + "?\n" +
                "Tài khoản và dữ liệu ở các lớp khác vẫn được giữ nguyên.", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(owner);
        confirm.setHeaderText("Xác nhận gỡ sinh viên khỏi lớp");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            client.deleteStudent(student.getId(), creditClass.getId(),
                    response -> Platform.runLater(() -> { if ("SUCCESS".equalsIgnoreCase(response.getStatus())) loadStudents(); }));
        }
    }

    private void loadProblems() {
        if (client == null || !client.isConnected()) return;
        client.getClassProblems(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            List<Problem> list = new ArrayList<>();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                Problem p = new Problem();
                if (o.has("id")) p.setId(o.get("id").getAsInt());
                if (o.has("title")) p.setTitle(o.get("title").getAsString());
                if (o.has("difficulty")) p.setDifficulty(o.get("difficulty").getAsString());
                if (o.has("problemType")) p.setProblemType(o.get("problemType").getAsString());
                if (o.has("oopSpec") && !o.get("oopSpec").isJsonNull()) p.setOopSpec(o.get("oopSpec").getAsString());
                p.setActive(false);
                list.add(p);
            }
            problems.setAll(list);
            if (classProblemsTab != null) {
                classProblemsTab.setText("Bài tập của lớp (" + list.size() + ")");
            }
        }));
    }

    private void loadAssessments() {
        if (client == null || !client.isConnected()) return;
        client.getClassAssessments(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null
                    || !response.getData().isJsonArray()) {
                assessments.clear();
                return;
            }
            List<AssessmentRow> rows = new ArrayList<>();
            for (JsonElement element : response.getData().getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                int studentCount = intValue(o, "studentCount");
                int startedCount = intValue(o, "startedCount");
                int completedCount = intValue(o, "completedCount");
                rows.add(new AssessmentRow(
                        intValue(o, "id"),
                        value(o, "title"),
                        assessmentStatus(value(o, "status")),
                        compactTime(value(o, "opensAt")) + " → " + compactTime(value(o, "closesAt")),
                        "Đã làm " + startedCount + "/" + studentCount + " • Xong " + completedCount,
                        value(o, "status")));
            }
            assessments.setAll(rows);
        }));
    }

    private int intValue(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }

    private String compactTime(String value) {
        return value == null || value.equals("—") || value.equals("-") ? "-" : value.replace('T', ' ');
    }

    private String assessmentStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "Đang diễn ra";
            case "UPCOMING" -> "Sắp diễn ra";
            case "EXPIRED" -> "Đã kết thúc";
            default -> status;
        };
    }

    private void loadAlerts() {
        if (client == null || !client.isConnected() || studentIds.isEmpty()) return;
        client.getProctorLogs(response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            List<AlertRow> list = new ArrayList<>();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); int userId = o.has("userId") ? o.get("userId").getAsInt() : -1;
                if (!studentIds.contains(userId)) continue;
                String event = value(o, "eventType");
                if ("FOCUS_LOSS".equalsIgnoreCase(event)) continue;
                list.add(new AlertRow(
                        value(o, "formattedTime"),
                        value(o, "fullName"),
                        event,
                        value(o, "detail"),
                        value(o, "severity"),
                        value(o, "problemTitle")));
            }
            alerts.setAll(list);
        }));
    }

    private String value(JsonObject object, String field) { return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "-"; }

    private void importStudents() {
        TeacherImportStudentsDialog dialog = new TeacherImportStudentsDialog(owner, List.of(creditClass), creditClass);
        dialog.showAndWait().ifPresent(payload -> client.importStudents(payload.classId, payload.studentsArray,
                response -> Platform.runLater(() -> {
                    boolean success = "SUCCESS".equalsIgnoreCase(response.getStatus());
                    if (success) loadStudents();
                    Alert result = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                            response.getMessage() != null ? response.getMessage()
                                    : (success ? "Đã lưu danh sách sinh viên." : "Không thể lưu danh sách sinh viên."),
                            ButtonType.OK);
                    result.initOwner(owner);
                    result.setHeaderText(success ? "Import thành công" : "Import thất bại");
                    result.showAndWait();
                })));
    }

    private record AlertRow(String time, String student, String event, String tool, String severity, String problem) {}
    private record AssessmentRow(int id, String title, String status, String window,
                                 String progress, String rawStatus) {}
}
