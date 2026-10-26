package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Submission;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.Function;

/**
 * TeacherAssessmentMonitorView — Màn hình giáo viên theo dõi trực tiếp một bài kiểm tra.
 * Hiển thị trực tiếp trong cửa sổ chính và có nút Back quay về Chi tiết lớp.
 */
public class TeacherAssessmentMonitorView extends BorderPane {

    private final Stage stage;
    private final int assessmentId;
    private final String title;
    private final SocketClient client = SessionManager.getInstance().getSocketClient();
    private final Label summary = new Label("Đang tải dữ liệu...");
    private final ObservableList<ProgressRow> progress = FXCollections.observableArrayList();
    private final ObservableList<SubmissionRow> submissions = FXCollections.observableArrayList();
    private final ObservableList<AlertRow> alerts = FXCollections.observableArrayList();
    private Timeline poller;
    private Runnable onBack;

    public TeacherAssessmentMonitorView(Stage stage, int assessmentId, String title) {
        this.stage = stage;
        this.assessmentId = assessmentId;
        this.title = title;
        buildLayout();
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    private void buildLayout() {
        getStyleClass().add("main-pane");

        // 1. TOP TOOLBAR
        HBox topBar = new HBox(12);
        topBar.getStyleClass().add("top-toolbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 20, 8, 20));

        Button backBtn = new Button("⬅ Chi tiết lớp");
        backBtn.getStyleClass().addAll("button", "btn-secondary");
        backBtn.setOnAction(e -> {
            stopPolling();
            if (onBack != null) {
                onBack.run();
            }
        });

        Label titleLabel = new Label("Theo dõi bài kiểm tra — " + title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        User user = SessionManager.getInstance().getCurrentUser();
        Button avatarBtn = new Button(user != null ? user.getAvatarText() : "GV");
        avatarBtn.getStyleClass().add("avatar-btn");

        topBar.getChildren().addAll(backBtn, titleLabel, spacer, themeToggleBtn, avatarBtn);
        setTop(topBar);

        // 2. CENTER CONTENT
        summary.setWrapText(true);
        summary.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-padding: 4px;");

        TableView<ProgressRow> progressTable = new TableView<>(progress);
        progressTable.getStyleClass().add("table-view");
        progressTable.setPlaceholder(new Label("Chưa có sinh viên trong lớp."));
        progressTable.getColumns().addAll(
                col("Tài khoản", ProgressRow::username, 150),
                col("Họ và tên", ProgressRow::fullName, 220),
                col("Đã nộp", ProgressRow::submitted, 100),
                col("Làm đúng", ProgressRow::solved, 100),
                col("Trạng thái", ProgressRow::status, 120),
                col("Lần nộp cuối", ProgressRow::lastSubmission, 170));
        progressTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableView<SubmissionRow> submissionTable = new TableView<>(submissions);
        submissionTable.getStyleClass().add("table-view");
        submissionTable.setPlaceholder(new Label("Chưa có lượt nộp bài."));
        submissionTable.getColumns().addAll(
                col("Thời điểm", SubmissionRow::time, 160),
                col("Sinh viên", SubmissionRow::student, 180),
                col("Bài", SubmissionRow::problem, 180),
                col("Ngôn ngữ", SubmissionRow::language, 95),
                col("Kết quả", SubmissionRow::verdict, 180),
                col("Điểm", SubmissionRow::score, 75),
                col("Test", SubmissionRow::tests, 85));
        submissionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        submissionTable.setRowFactory(table -> {
            TableRow<SubmissionRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showSource(row.getItem());
            });
            return row;
        });

        Button viewSource = new Button("Xem mã nguồn lượt nộp đã chọn");
        viewSource.getStyleClass().addAll("button", "btn-secondary");
        viewSource.disableProperty().bind(submissionTable.getSelectionModel().selectedItemProperty().isNull());
        viewSource.setOnAction(e -> showSource(submissionTable.getSelectionModel().getSelectedItem()));
        VBox submissionBox = new VBox(8, viewSource, submissionTable);
        VBox.setVgrow(submissionTable, Priority.ALWAYS);

        TableView<AlertRow> alertTable = new TableView<>(alerts);
        alertTable.getStyleClass().add("table-view");
        alertTable.setPlaceholder(new Label("Chưa có cảnh báo gian lận trong bài kiểm tra."));
        alertTable.getColumns().addAll(
                col("Thời điểm", AlertRow::time, 165),
                col("Sinh viên", AlertRow::student, 180),
                col("Sự kiện", AlertRow::event, 150),
                col("Chi tiết", AlertRow::detail, 280),
                col("Mức độ", AlertRow::severity, 100),
                col("Bài", AlertRow::problem, 180));
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TabPane tabs = new TabPane(
                tab("Tiến độ sinh viên", progressTable),
                tab("Lịch sử nộp bài", submissionBox),
                tab("Cảnh báo gian lận", alertTable));
        VBox.setVgrow(progressTable, Priority.ALWAYS);
        VBox.setVgrow(alertTable, Priority.ALWAYS);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox contentBox = new VBox(10, summary, tabs);
        contentBox.setPadding(new Insets(14, 20, 20, 20));
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        setCenter(contentBox);
    }

    private Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private <T> TableColumn<T, String> col(String title, Function<T, String> getter, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    public void startPolling() {
        refresh();
        if (poller != null) {
            poller.stop();
        }
        poller = new Timeline(new KeyFrame(Duration.seconds(3), e -> refresh()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
    }

    public void stopPolling() {
        if (poller != null) {
            poller.stop();
            poller = null;
        }
    }

    private void refresh() {
        if (client == null || !client.isConnected()) return;
        client.getAssessmentMonitor(assessmentId, response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null
                    || !response.getData().isJsonObject()) {
                summary.setText(response.getMessage() == null ? "Không thể tải dữ liệu theo dõi." : response.getMessage());
                return;
            }
            JsonObject data = response.getData().getAsJsonObject();
            String examTitle = value(data, "title");
            String status = value(data, "status");
            summary.setText(examTitle + "  •  " + statusText(status) + "  •  "
                    + format(value(data, "opensAt")) + " → " + format(value(data, "closesAt")));

            loadProgress(data.getAsJsonArray("progress"));
            loadSubmissions(data.getAsJsonArray("submissions"));
            loadAlerts(data.getAsJsonArray("alerts"));
        }));
    }

    private void loadProgress(JsonArray array) {
        progress.clear();
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            int submitted = number(o, "submittedCount");
            int total = number(o, "totalProblems");
            boolean started = bool(o, "started");
            progress.add(new ProgressRow(value(o, "username"), value(o, "fullName"),
                    submitted + "/" + total, number(o, "solvedCount") + "/" + total,
                    bool(o, "completed") ? "Đã hoàn thành" : started ? "Đang làm" : "Chưa bắt đầu",
                    format(value(o, "lastSubmissionAt"))));
        }
    }

    private void loadSubmissions(JsonArray array) {
        submissions.clear();
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            submissions.add(new SubmissionRow(format(value(o, "submittedAt")),
                    value(o, "fullName") + " (" + value(o, "username") + ")",
                    value(o, "problemTitle"), value(o, "language"),
                    Submission.formatVerdict(value(o, "verdict")),
                    String.valueOf(number(o, "score")), number(o, "passedTests") + "/" + number(o, "totalTests"),
                    value(o, "sourceCode")));
        }
    }

    private void loadAlerts(JsonArray array) {
        alerts.clear();
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            String event = value(o, "eventType");
            if ("FOCUS_LOSS".equalsIgnoreCase(event)) continue;
            alerts.add(new AlertRow(value(o, "formattedTime"), value(o, "fullName"), event,
                    value(o, "detail"), value(o, "severity"), value(o, "problemTitle")));
        }
    }

    private void showSource(SubmissionRow row) {
        if (row == null) return;
        SourceCodeViewerDialog viewer = new SourceCodeViewerDialog(
                stage,
                row.student() + " — " + row.problem(),
                "",
                row.sourceCode(),
                false
        );
        viewer.showAndWait();
    }

    private String value(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "—";
    }

    private int number(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsInt() : 0;
    }

    private boolean bool(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                && object.get(field).getAsBoolean();
    }

    private String format(String value) {
        return value == null || value.equals("—") ? "—" : value.replace('T', ' ');
    }

    private String statusText(String status) {
        return switch (status) {
            case "ACTIVE" -> "Đang diễn ra";
            case "UPCOMING" -> "Sắp diễn ra";
            case "EXPIRED" -> "Đã kết thúc";
            default -> status;
        };
    }

    public record ProgressRow(String username, String fullName, String submitted, String solved, String status, String lastSubmission) {}
    public record SubmissionRow(String time, String student, String problem, String language, String verdict, String score, String tests, String sourceCode) {}
    public record AlertRow(String time, String student, String event, String detail, String severity, String problem) {}
}
