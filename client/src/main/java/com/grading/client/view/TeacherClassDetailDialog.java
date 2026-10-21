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
        pane.getStyleClass().add("card");
        pane.setPrefSize(1080, 680);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        Label heading = new Label(creditClass.getClassCode() + " — " + creditClass.getName());
        heading.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label subtitle = new Label("Quản lý sinh viên, kiểm tra theo lớp và các cảnh báo giám sát.");
        subtitle.getStyleClass().add("meta-item");

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(buildStudentsTab(), buildAssessmentTab(), buildAlertsTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(heading, subtitle, tabs);
        pane.setContent(root);
        pane.getButtonTypes().add(ButtonType.CLOSE);
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
        TableColumn<User.UserDTO, Integer> id = new TableColumn<>("ID");
        id.setCellValueFactory(new PropertyValueFactory<>("id"));
        id.setMaxWidth(80);
        TableColumn<User.UserDTO, String> username = new TableColumn<>("Tên đăng nhập");
        username.setCellValueFactory(new PropertyValueFactory<>("username"));
        username.setPrefWidth(220);
        TableColumn<User.UserDTO, String> name = new TableColumn<>("Họ và tên");
        name.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        name.setPrefWidth(320);
        TableColumn<User.UserDTO, String> solved = new TableColumn<>("Bài làm đúng");
        solved.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(solvedCounts.getOrDefault(data.getValue().getId(), 0))));
        solved.setPrefWidth(130);
        solved.setStyle("-fx-alignment: CENTER;");
        TableColumn<User.UserDTO, Void> actions = new TableColumn<>("Thao tác");
        actions.setPrefWidth(170);
        actions.setCellFactory(col -> new TableCell<>() {
            private final Button edit = new Button("Sửa");
            private final Button delete = new Button("Xóa");
            private final HBox buttons = new HBox(8, edit, delete);
            {
                edit.getStyleClass().addAll("button", "btn-secondary");
                delete.getStyleClass().addAll("button", "btn-danger");
                edit.setOnAction(e -> editStudent(getTableView().getItems().get(getIndex())));
                delete.setOnAction(e -> deleteStudent(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : buttons); }
        });
        studentTable.getColumns().addAll(id, username, name, solved, actions);
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(studentTable, Priority.ALWAYS);
        box.getChildren().addAll(importBtn, studentTable);
        return new Tab("Sinh viên (" + creditClass.getStudentCount() + ")", box);
    }

    private Tab buildAssessmentTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        GridPane config = new GridPane();
        config.setHgap(12); config.setVgap(10);
        TextField title = new TextField("Kiểm tra " + creditClass.getClassCode());
        DatePicker openDate = new DatePicker(LocalDate.now());
        DatePicker closeDate = new DatePicker(LocalDate.now().plusDays(1));
        Spinner<Integer> duration = new Spinner<>(5, 300, 60, 5);
        config.addRow(0, new Label("Tên kiểm tra:"), title, new Label("Thời lượng (phút):"), duration);
        config.addRow(1, new Label("Mở từ:"), openDate, new Label("Đóng vào:"), closeDate);

        Label help = new Label("Chọn các bài tập thuộc lớp này để đưa vào kiểm tra.");
        help.getStyleClass().add("meta-item");
        problemTable = new TableView<>(problems);
        problemTable.getStyleClass().add("table-view");
        TableColumn<Problem, Boolean> choose = new TableColumn<>("Chọn");
        choose.setCellValueFactory(data -> new javafx.beans.property.SimpleBooleanProperty(data.getValue().isActive()));
        choose.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            { check.setOnAction(e -> getTableView().getItems().get(getIndex()).setActive(check.isSelected())); }
            @Override protected void updateItem(Boolean item, boolean empty) { super.updateItem(item, empty); if (empty) setGraphic(null); else { check.setSelected(Boolean.TRUE.equals(item)); setGraphic(check); setAlignment(Pos.CENTER); } }
        });
        TableColumn<Problem, String> problemTitle = new TableColumn<>("Bài tập");
        problemTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        problemTitle.setPrefWidth(450);
        TableColumn<Problem, String> difficulty = new TableColumn<>("Độ khó");
        difficulty.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        problemTable.getColumns().addAll(choose, problemTitle, difficulty);
        problemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        Button save = new Button("Lưu cấu hình kiểm tra");
        save.getStyleClass().addAll("button", "btn-primary");
        save.setOnAction(e -> {
            long selected = problems.stream().filter(Problem::isActive).count();
            Alert notice = new Alert(Alert.AlertType.INFORMATION);
            notice.initOwner(owner);
            notice.setTitle("Cấu hình kiểm tra");
            notice.setHeaderText(title.getText());
            notice.setContentText("Đã chọn " + selected + " bài, thời lượng " + duration.getValue()
                    + " phút; mở " + openDate.getValue() + ", đóng " + closeDate.getValue() + ".");
            notice.showAndWait();
        });
        box.getChildren().addAll(config, help, problemTable, save);
        return new Tab("Kiểm tra", box);
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
        VBox.setVgrow(alertTable, Priority.ALWAYS);
        box.getChildren().addAll(bar, alertTable);
        return new Tab("Cảnh báo", box);
    }

    private TableColumn<AlertRow, String> column(String name, java.util.function.Function<AlertRow, String> value) {
        TableColumn<AlertRow, String> col = new TableColumn<>(name);
        col.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        return col;
    }

    private void loadStudents() {
        if (client == null || !client.isConnected()) return;
        client.getStudents(creditClass.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null) return;
            List<User.UserDTO> list = new ArrayList<>(); studentIds.clear();
            for (JsonElement e : response.getData().getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject(); User.UserDTO user = new User.UserDTO();
                if (o.has("id")) user.setId(o.get("id").getAsInt());
                if (o.has("username")) user.setUsername(o.get("username").getAsString());
                if (o.has("fullName")) user.setFullName(o.get("fullName").getAsString());
                if (o.has("createdAt")) user.setCreatedAt(o.get("createdAt").getAsString());
                list.add(user); studentIds.add(user.getId());
            }
            students.setAll(list); loadStats(); loadAlerts();
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
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Xóa sinh viên " + student.getFullName() + " khỏi hệ thống?", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(owner);
        confirm.setHeaderText("Xác nhận xóa sinh viên");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            client.deleteStudent(student.getId(), response -> Platform.runLater(() -> { if ("SUCCESS".equalsIgnoreCase(response.getStatus())) loadStudents(); }));
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
                p.setActive(true); list.add(p);
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
        dialog.showAndWait().ifPresent(payload -> client.importStudents(payload.classId, payload.studentsArray, response -> Platform.runLater(() -> { if ("SUCCESS".equalsIgnoreCase(response.getStatus())) loadStudents(); })));
    }

    private record AlertRow(String time, String student, String event, String severity, String problem) {}
}
