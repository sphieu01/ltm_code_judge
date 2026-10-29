package com.grading.client.view;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Submission;
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

import java.util.ArrayList;
import java.util.List;

/**
 * StudentHistoryView - Màn hình hiển thị toàn bộ lịch sử nộp bài của sinh viên.
 * Hiển thị trực tiếp trong cửa sổ chính và có nút Back quay về Dashboard.
 */
public class StudentHistoryView extends BorderPane {

    private final Stage stage;
    private TableView<Submission> table;
    private final ObservableList<Submission> submissions = FXCollections.observableArrayList();
    private Label totalSubmissionsLabel;
    private Runnable onBack;

    public StudentHistoryView(Stage stage) {
        this.stage = stage;
        buildLayout();
        loadSubmissions();
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

        Button backBtn = new Button("⬅ Danh sách bài");
        backBtn.getStyleClass().addAll("button", "btn-secondary");
        backBtn.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        Label titleLabel = new Label("Lịch sử nộp bài cá nhân");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        totalSubmissionsLabel = new Label("Tổng cộng: 0 lượt nộp");
        totalSubmissionsLabel.getStyleClass().add("meta-item");
        totalSubmissionsLabel.setStyle("-fx-font-size: 12.5px; -fx-padding: 0 10 0 0;");

        Button themeToggleBtn = new Button(ThemeManager.getInstance().getToggleIconText());
        themeToggleBtn.getStyleClass().addAll("button", "btn-theme");
        themeToggleBtn.setOnAction(e -> ThemeManager.getInstance().toggleTheme());
        ThemeManager.getInstance().addThemeListener(theme -> {
            themeToggleBtn.setText(ThemeManager.getInstance().getToggleIconText());
        });

        User user = SessionManager.getInstance().getCurrentUser();
        Button avatarBtn = new Button(user != null ? user.getAvatarText() : "SV");
        avatarBtn.getStyleClass().add("avatar-btn");

        topBar.getChildren().addAll(backBtn, titleLabel, spacer, totalSubmissionsLabel, themeToggleBtn, avatarBtn);
        setTop(topBar);

        // 2. CENTER CONTENT
        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(16, 20, 20, 20));

        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setPlaceholder(new Label("Chưa có lượt nộp bài nào trong quá khứ"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Submission, String> colTitle = new TableColumn<>("Bài tập");
        colTitle.setCellValueFactory(c -> {
            String t = c.getValue().getProblemTitle();
            return new SimpleStringProperty(t != null && !t.isBlank() ? t : "Bài #" + c.getValue().getProblemId());
        });
        colTitle.setMinWidth(240);
        colTitle.setPrefWidth(300);

        TableColumn<Submission, String> colLang = new TableColumn<>("Ngôn ngữ");
        colLang.setCellValueFactory(new PropertyValueFactory<>("language"));
        colLang.setMinWidth(95);
        colLang.setPrefWidth(105);
        colLang.setMaxWidth(130);
        colLang.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colVerdict = new TableColumn<>("Kết quả");
        colVerdict.setCellValueFactory(c -> new SimpleStringProperty(Submission.formatVerdict(c.getValue().getVerdict())));
        colVerdict.setMinWidth(170);
        colVerdict.setPrefWidth(190);
        colVerdict.setMaxWidth(225);
        colVerdict.setStyle("-fx-alignment: CENTER;");
        colVerdict.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank() || "—".equals(item) || "-".equals(item)) {
                    setGraphic(null);
                    setText("-");
                } else {
                    String formatted = Submission.formatVerdict(item);
                    Label pill = new Label(formatted);
                    pill.setMinWidth(Region.USE_PREF_SIZE);
                    String u = item.toUpperCase();
                    if (u.equals("AC") || u.equals("ACCEPTED") || u.equals("PASSED")) {
                        pill.getStyleClass().add("pill-ac");
                    } else if (u.equals("WA") || u.equals("WRONG_ANSWER") || u.contains("WRONG")) {
                        pill.getStyleClass().add("pill-wa");
                    } else if (u.equals("TLE") || u.equals("TIME_LIMIT_EXCEEDED") || u.contains("TIME_LIMIT")) {
                        pill.getStyleClass().add("pill-tle");
                    } else if (u.equals("CE") || u.equals("COMPILE_ERROR") || u.contains("COMPILE")) {
                        pill.getStyleClass().add("pill-ce");
                    } else {
                        pill.getStyleClass().add("pill-running");
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        TableColumn<Submission, String> colPass = new TableColumn<>("Test đạt");
        colPass.setCellValueFactory(c -> new SimpleStringProperty(formatTestResult(c.getValue())));
        colPass.setMinWidth(90);
        colPass.setPrefWidth(105);
        colPass.setMaxWidth(120);
        colPass.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colTime = new TableColumn<>("Thời gian");
        colTime.setCellValueFactory(c -> new SimpleStringProperty(formatExecutionTime(c.getValue().getExecTimeMs())));
        colTime.setMinWidth(105);
        colTime.setPrefWidth(120);
        colTime.setMaxWidth(140);
        colTime.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colDate = new TableColumn<>("Thời điểm nộp");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(formatSubmittedAt(c.getValue().getSubmittedAt())));
        colDate.setMinWidth(165);
        colDate.setPrefWidth(190);

        table.getColumns().addAll(colTitle, colLang, colVerdict, colPass, colTime, colDate);
        table.setItems(submissions);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setRowFactory(view -> {
            TableRow<Submission> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 1) {
                    showSourceCode(row.getItem());
                }
            });
            return row;
        });

        contentBox.getChildren().add(table);
        setCenter(contentBox);
    }

    private void loadSubmissions() {
        SocketClient client = SessionManager.getInstance().getSocketClient();

        TableViewSupport.load(table, client, done -> client.getHistory(done), response -> {
            if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                List<Submission> list = new ArrayList<>();
                for (JsonElement el : response.getData().getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    Submission s = new Submission();
                    if (obj.has("id")) s.setId(obj.get("id").getAsInt());
                    if (obj.has("problemId")) s.setProblemId(obj.get("problemId").getAsInt());
                    if (obj.has("problemTitle") && !obj.get("problemTitle").isJsonNull()) {
                        s.setProblemTitle(obj.get("problemTitle").getAsString());
                    }
                    if (obj.has("language")) s.setLanguage(obj.get("language").getAsString());
                    if (obj.has("sourceCode") && !obj.get("sourceCode").isJsonNull()) {
                        s.setSourceCode(obj.get("sourceCode").getAsString());
                    }
                    if (obj.has("isZip") && !obj.get("isZip").isJsonNull()) {
                        s.setZip(obj.get("isZip").getAsBoolean());
                    }
                    if (obj.has("verdict")) s.setVerdict(obj.get("verdict").getAsString());
                    if (obj.has("score")) s.setScore(obj.get("score").getAsInt());
                    if (obj.has("passedTests")) s.setPassedTests(obj.get("passedTests").getAsInt());
                    else if (obj.has("passedTestCases")) s.setPassedTests(obj.get("passedTestCases").getAsInt());
                    if (obj.has("totalTests")) s.setTotalTests(obj.get("totalTests").getAsInt());
                    else if (obj.has("totalTestCases")) s.setTotalTests(obj.get("totalTestCases").getAsInt());
                    if (obj.has("execTimeMs")) s.setExecTimeMs(obj.get("execTimeMs").getAsInt());
                    else if (obj.has("executionTimeMs")) s.setExecTimeMs(obj.get("executionTimeMs").getAsInt());
                    if (obj.has("memoryUsedKb")) s.setMemoryUsedKb(obj.get("memoryUsedKb").getAsInt());
                    if (obj.has("submittedAt") && !obj.get("submittedAt").isJsonNull()) s.setSubmittedAt(obj.get("submittedAt").getAsString());
                    else if (obj.has("createdAt")) s.setSubmittedAt(obj.get("createdAt").getAsString());

                    list.add(s);
                }
                submissions.setAll(list);
                totalSubmissionsLabel.setText("Tổng cộng: " + list.size() + " lượt nộp");
            }
        });
    }

    private String formatExecutionTime(int executionTimeMs) {
        return executionTimeMs > 0 ? executionTimeMs + " ms" : "-";
    }

    private String formatTestResult(Submission submission) {
        return submission.getTotalTests() > 0
                ? submission.getPassedTests() + "/" + submission.getTotalTests()
                : "-";
    }

    private String formatSubmittedAt(String submittedAt) {
        return submittedAt != null && submittedAt.length() > 19
                ? submittedAt.substring(0, 19) : (submittedAt == null ? "-" : submittedAt);
    }

    private void showSourceCode(Submission submission) {
        if (submission == null) return;
        SourceCodeViewerDialog viewer = new SourceCodeViewerDialog(
                stage,
                submission.getProblemTitle(),
                formatSubmittedAt(submission.getSubmittedAt()),
                submission.getSourceCode(),
                submission.isZip()
        );
        viewer.showAndWait();
    }
}
