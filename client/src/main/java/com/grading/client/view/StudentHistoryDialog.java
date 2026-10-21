package com.grading.client.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Submission;
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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentHistoryDialog — Hộp thoại hiển thị toàn bộ lịch sử nộp bài trong quá khứ của sinh viên.
 * Tuân thủ quy chuẩn DESIGN.md Component 9.
 */
public class StudentHistoryDialog extends Dialog<Void> {

    private TableView<Submission> table;
    private ObservableList<Submission> submissions = FXCollections.observableArrayList();
    private Label totalSubmissionsLabel;

    public StudentHistoryDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Lịch Sử Nộp Bài Trong Quá Khứ");

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(1000);
        pane.setPrefHeight(510);

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(16));

        // Header info
        HBox topInfo = new HBox(12);
        topInfo.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Lịch sử nộp bài cá nhân");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        totalSubmissionsLabel = new Label("Tổng cộng: 0 lượt nộp");
        totalSubmissionsLabel.getStyleClass().add("meta-item");

        topInfo.getChildren().addAll(titleLabel, spacer, totalSubmissionsLabel);

        // Bảng lịch sử nộp bài
        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setPlaceholder(new Label("Chưa có lượt nộp bài nào trong quá khứ"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Submission, Integer> colId = new TableColumn<>("#ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(65);
        colId.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colTitle = new TableColumn<>("Bài tập");
        colTitle.setCellValueFactory(c -> {
            String t = c.getValue().getProblemTitle();
            return new SimpleStringProperty(t != null && !t.isBlank() ? t : "Bài #" + c.getValue().getProblemId());
        });
        colTitle.setPrefWidth(260);

        TableColumn<Submission, String> colLang = new TableColumn<>("Ngôn ngữ");
        colLang.setCellValueFactory(new PropertyValueFactory<>("language"));
        colLang.setPrefWidth(95);
        colLang.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colVerdict = new TableColumn<>("Kết quả");
        colVerdict.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        colVerdict.setPrefWidth(105);
        colVerdict.setStyle("-fx-alignment: CENTER;");
        colVerdict.setCellFactory(col -> new TableCell<Submission, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    String u = item.toUpperCase();
                    if (u.equals("AC") || u.equals("ACCEPTED") || u.equals("PASSED")) {
                        pill.getStyleClass().add("pill-ac");
                    } else if (u.equals("WA") || u.equals("WRONG_ANSWER")) {
                        pill.getStyleClass().add("pill-wa");
                    } else if (u.equals("TLE") || u.equals("TIME_LIMIT_EXCEEDED")) {
                        pill.getStyleClass().add("pill-tle");
                    } else if (u.equals("CE") || u.equals("COMPILE_ERROR")) {
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
        colPass.setPrefWidth(90);
        colPass.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colTime = new TableColumn<>("Thời gian");
        colTime.setCellValueFactory(c -> new SimpleStringProperty(formatExecutionTime(c.getValue().getExecTimeMs())));
        colTime.setPrefWidth(105);
        colTime.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colDate = new TableColumn<>("Thời điểm nộp");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(formatSubmittedAt(c.getValue().getSubmittedAt())));
        colDate.setPrefWidth(180);

        table.getColumns().addAll(colId, colTitle, colLang, colVerdict, colPass, colTime, colDate);
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

        contentBox.getChildren().addAll(topInfo, table);
        pane.setContent(contentBox);

        ButtonType closeBtnType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().add(closeBtnType);

        Button closeBtn = (Button) pane.lookupButton(closeBtnType);
        closeBtn.getStyleClass().addAll("button", "btn-secondary");

        loadSubmissions();
    }

    private void loadSubmissions() {
        SocketClient client = SessionManager.getInstance().getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.getHistory(response -> {
            Platform.runLater(() -> {
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
        });
    }

    private String formatExecutionTime(int executionTimeMs) {
        return executionTimeMs > 0 ? executionTimeMs + " ms" : "—";
    }

    private String formatTestResult(Submission submission) {
        return submission.getTotalTests() > 0
                ? submission.getPassedTests() + "/" + submission.getTotalTests()
                : "—";
    }

    private String formatSubmittedAt(String submittedAt) {
        return submittedAt != null && submittedAt.length() > 19
                ? submittedAt.substring(0, 19) : (submittedAt == null ? "—" : submittedAt);
    }

    private void showSourceCode(Submission submission) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Mã nguồn đã nộp");
        dialog.setHeaderText(submission.getProblemTitle() + " — " + submission.getSubmittedAt());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea sourceArea = new TextArea(submission.getSourceCode() == null || submission.getSourceCode().isBlank()
                ? "Không có mã nguồn để hiển thị." : submission.getSourceCode());
        sourceArea.setEditable(false);
        sourceArea.setWrapText(false);
        sourceArea.getStyleClass().add("code-editor");
        sourceArea.setPrefSize(760, 450);

        dialog.getDialogPane().setContent(sourceArea);
        dialog.getDialogPane().setPrefSize(820, 550);
        dialog.showAndWait();
    }
}
