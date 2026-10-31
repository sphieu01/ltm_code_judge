package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

/** Màn hình giáo viên theo dõi trực tiếp một bài kiểm tra. */
public class TeacherAssessmentMonitorDialog extends Dialog<Void> {
    private final int assessmentId;
    private final SocketClient client = SessionManager.getInstance().getSocketClient();
    private final Label summary = new Label("Đang tải dữ liệu...");
    private final ObservableList<ProgressRow> progress = FXCollections.observableArrayList();
    private final ObservableList<SubmissionRow> submissions = FXCollections.observableArrayList();
    private final ObservableList<AlertRow> alerts = FXCollections.observableArrayList();
    private TableView<ProgressRow> progressTable;
    private TableView<SubmissionRow> submissionTable;
    private TableView<AlertRow> alertTable;
    private Timeline poller;
    private final AssessmentMonitorRefresh refresher;

    public TeacherAssessmentMonitorDialog(Stage owner, int assessmentId, String title) {
        this.assessmentId = assessmentId;
        initOwner(owner);
        setTitle("Theo dõi bài kiểm tra - " + title);
        setResizable(true);
        build();
        refresher = new AssessmentMonitorRefresh(List.of(progressTable, submissionTable, alertTable),
                client, assessmentId, summary, this::applyData);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(visualBounds.getWidth(), visualBounds.getHeight());

        setOnShowing(event -> {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.setX(visualBounds.getMinX());
            stage.setY(visualBounds.getMinY());
            stage.setWidth(visualBounds.getWidth());
            stage.setHeight(visualBounds.getHeight());
            stage.setMaximized(true);
        });

        setOnShown(e -> {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.setMaximized(true);
            Platform.runLater(() -> stage.setMaximized(true));
            startPolling();
        });
        setOnHidden(e -> stopPolling());
    }

    private void build() {
        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        String css = ThemeManager.getInstance().isDark() ? "/css/dark.css" : "/css/light.css";
        var resource = getClass().getResource(css);
        if (resource != null) pane.getStylesheets().add(resource.toExternalForm());
        pane.getButtonTypes().add(new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));

        summary.setWrapText(true);
        summary.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 4px;");

        progressTable = new TableView<>(progress);
        progressTable.setPlaceholder(new Label("Chưa có sinh viên trong lớp."));
        progressTable.getColumns().addAll(
                col("Tài khoản", ProgressRow::username, 150),
                col("Họ và tên", ProgressRow::fullName, 220),
                col("Đã nộp", ProgressRow::submitted, 100),
                col("Làm đúng", ProgressRow::solved, 100),
                col("Trạng thái", ProgressRow::status, 120),
                col("Lần nộp cuối", ProgressRow::lastSubmission, 170));
        progressTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        submissionTable = new TableView<>(submissions);
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

        alertTable = new TableView<>(alerts);
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
        VBox root = new VBox(10, summary, tabs);
        root.setPadding(new Insets(14));
        VBox.setVgrow(root, Priority.ALWAYS);
        pane.setContent(root);
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

    private void startPolling() {
        refresh();
        poller = new Timeline(new KeyFrame(Duration.seconds(3), e -> refresh()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
    }

    private void stopPolling() {
        refresher.stop();
        if (poller != null) poller.stop();
    }

    private void refresh() {
        refresher.refresh();
    }

    private void applyData(JsonObject data) {
        List<ProgressRow> nextProgress = parseProgress(data.getAsJsonArray("progress"));
        List<SubmissionRow> nextSubmissions = parseSubmissions(data.getAsJsonArray("submissions"));
        List<AlertRow> nextAlerts = parseAlerts(data.getAsJsonArray("alerts"));
        String title = value(data, "title");
        String status = value(data, "status");
        summary.setText(title + "  •  " + statusText(status) + "  •  "
                + format(value(data, "opensAt")) + " → " + format(value(data, "closesAt")));
        TableViewSupport.updateItems(progressTable, nextProgress, ProgressRow::username);
        TableViewSupport.updateItems(submissionTable, nextSubmissions, SubmissionRow::id);
        TableViewSupport.updateItems(alertTable, nextAlerts, AlertRow::id);
    }

    private List<ProgressRow> parseProgress(JsonArray array) {
        List<ProgressRow> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            int submitted = number(o, "submittedCount");
            int total = number(o, "totalProblems");
            boolean started = bool(o, "started");
            rows.add(new ProgressRow(value(o, "username"), value(o, "fullName"),
                    submitted + "/" + total, number(o, "solvedCount") + "/" + total,
                    bool(o, "completed") ? "Đã hoàn thành" : started ? "Đang làm" : "Chưa bắt đầu",
                    format(value(o, "lastSubmissionAt"))));
        }
        return rows;
    }

    private List<SubmissionRow> parseSubmissions(JsonArray array) {
        List<SubmissionRow> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            rows.add(new SubmissionRow(format(value(o, "submittedAt")),
                    value(o, "fullName") + " (" + value(o, "username") + ")",
                    value(o, "problemTitle"), value(o, "language"),
                    com.grading.client.model.Submission.formatVerdict(value(o, "verdict")),
                    String.valueOf(number(o, "score")), number(o, "passedTests") + "/" + number(o, "totalTests"),
                    value(o, "sourceCode"), number(o, "submissionId")));
        }
        return rows;
    }

    private List<AlertRow> parseAlerts(JsonArray array) {
        List<AlertRow> rows = new ArrayList<>();
        if (array == null) return rows;
        for (JsonElement element : array) {
            JsonObject o = element.getAsJsonObject();
            String event = value(o, "eventType");
            if ("FOCUS_LOSS".equalsIgnoreCase(event)) continue;
            rows.add(new AlertRow(value(o, "formattedTime"), value(o, "fullName"), event,
                    value(o, "detail"), value(o, "severity"), value(o, "problemTitle"), number(o, "id")));
        }
        return rows;
    }

    private void showSource(SubmissionRow row) {
        if (row == null) return;
        Stage owner = (Stage) getDialogPane().getScene().getWindow();
        SourceCodeViewerDialog viewer = new SourceCodeViewerDialog(
                owner,
                row.student() + " - " + row.problem(),
                "",
                row.sourceCode(),
                false
        );
        viewer.showAndWait();
    }

    private String value(JsonObject object, String field) {
        return object != null && object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "-";
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
        return value == null || value.equals("—") || value.equals("-") ? "-" : value.replace('T', ' ');
    }

    private String statusText(String status) {
        return switch (status) {
            case "ACTIVE" -> "Đang diễn ra";
            case "UPCOMING" -> "Sắp diễn ra";
            case "EXPIRED" -> "Đã kết thúc";
            default -> status;
        };
    }

    private record ProgressRow(String username, String fullName, String submitted, String solved,
                               String status, String lastSubmission) {}
    private record SubmissionRow(String time, String student, String problem, String language,
                                 String verdict, String score, String tests, String sourceCode, int id) {}
    private record AlertRow(String time, String student, String event, String detail,
                            String severity, String problem, int id) {}
}
