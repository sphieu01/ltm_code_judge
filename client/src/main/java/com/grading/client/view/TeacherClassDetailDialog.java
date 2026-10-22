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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
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
    private final Set<Integer> studentIds = new HashSet<>();
    private final Map<Integer, Integer> solvedCounts = new HashMap<>();

    private TableView<User.UserDTO> studentTable;
    private TableView<Problem> problemTable;
    private TableView<AlertRow> alertTable;
    private Tab studentsTab;

    public TeacherClassDetailDialog(Stage owner, CreditClass creditClass) {
        this.owner = owner;
        this.creditClass = creditClass;
        initOwner(owner);
        setTitle("Quản lý lớp tín chỉ");
        build();
        loadStudents();
        loadProblems();
    }

    private void build() {
        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        pane.setPrefSize(1180, 780);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        Label heading = new Label(creditClass.getClassCode() + " — " + creditClass.getName());
        heading.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label subtitle = new Label("Quản lý sinh viên, kiểm tra theo lớp và các cảnh báo giám sát.");
        subtitle.getStyleClass().add("meta-item");

        ButtonType createAssessmentType = new ButtonType("Tạo bài kiểm tra", ButtonBar.ButtonData.APPLY);
        ButtonType closeType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(createAssessmentType, closeType);
        Button createAssessmentButton = (Button) pane.lookupButton(createAssessmentType);
        createAssessmentButton.getStyleClass().addAll("button", "btn-primary");

        TabPane tabs = new TabPane();
        Tab assessmentTab = buildAssessmentTab(createAssessmentButton);
        tabs.getTabs().addAll(buildStudentsTab(), assessmentTab, buildAlertsTab());
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
        TableViewSupport.fitHeightToRows(studentTable, 10);
        VBox.setVgrow(studentTable, Priority.NEVER);
        box.getChildren().addAll(importBtn, studentTable);
        studentsTab = new Tab("Sinh viên (" + creditClass.getStudentCount() + ")", box);
        return studentsTab;
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
        LocalDateTime initialStart = LocalDate.now().atStartOfDay();
        LocalDateTime initialClose = LocalDate.now().plusDays(1).atStartOfDay();
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
        TableViewSupport.fitHeightToRows(problemTable, 8);
        VBox.setVgrow(problemTable, Priority.NEVER);

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
                            Alert notice = new Alert(Alert.AlertType.INFORMATION, response.getMessage(), ButtonType.OK);
                            notice.initOwner(owner);
                            notice.showAndWait();
                        } else {
                            validation.setText(response.getMessage() != null ? response.getMessage() : "Không thể lưu bài kiểm tra.");
                        }
                    }));
        });
        box.getChildren().addAll(config, validation, problemTable);
        return new Tab("Kiểm tra", box);
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
        alertTable.getColumns().addAll(column("Thời điểm", AlertRow::time), column("Sinh viên", AlertRow::student),
                column("Sự kiện", AlertRow::event), column("Mức độ", AlertRow::severity), column("Bài tập", AlertRow::problem));
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableViewSupport.fitHeightToRows(alertTable, 10);
        VBox.setVgrow(alertTable, Priority.NEVER);
        box.getChildren().addAll(bar, alertTable);
        return new Tab("Cảnh báo", box);
    }

    private TableColumn<AlertRow, String> column(String name, java.util.function.Function<AlertRow, String> value) {
        TableColumn<AlertRow, String> col = new TableColumn<>(name);
        col.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        switch (name) {
            case "Thời điểm" -> { col.setMinWidth(155); col.setPrefWidth(175); col.setMaxWidth(200); }
            case "Sinh viên" -> { col.setMinWidth(180); col.setPrefWidth(240); }
            case "Sự kiện" -> { col.setMinWidth(180); col.setPrefWidth(250); }
            case "Mức độ" -> { col.setMinWidth(90); col.setPrefWidth(105); col.setMaxWidth(125); col.setStyle("-fx-alignment: CENTER;"); }
            case "Bài tập" -> { col.setMinWidth(210); col.setPrefWidth(300); }
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
            loadStats(); loadAlerts();
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
        client.getProblems(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            List<Problem> list = new ArrayList<>();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); Problem p = new Problem();
                if (o.has("id")) p.setId(o.get("id").getAsInt());
                if (o.has("title")) p.setTitle(o.get("title").getAsString());
                if (o.has("difficulty")) p.setDifficulty(o.get("difficulty").getAsString());
                // Assessment selection always starts empty; the teacher explicitly chooses each problem.
                p.setActive(false); list.add(p);
            }
            problems.setAll(list);
        }));
    }

    private void loadAlerts() {
        if (client == null || !client.isConnected() || studentIds.isEmpty()) return;
        client.getProctorLogs(response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            List<AlertRow> list = new ArrayList<>();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); int userId = o.has("userId") ? o.get("userId").getAsInt() : -1;
                if (!studentIds.contains(userId)) continue;
                list.add(new AlertRow(value(o, "formattedTime"), value(o, "fullName"), value(o, "eventType"), value(o, "severity"), value(o, "problemTitle")));
            }
            alerts.setAll(list);
        }));
    }

    private String value(JsonObject object, String field) { return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "—"; }

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

    private record AlertRow(String time, String student, String event, String severity, String problem) {}
}
