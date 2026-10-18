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
    private TextArea codePreviewArea;
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
        pane.setPrefWidth(920);
        pane.setPrefHeight(620);

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
        table.setPrefHeight(320);

        TableColumn<Submission, Integer> colId = new TableColumn<>("#ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(65);
        colId.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colTitle = new TableColumn<>("Bài tập");
        colTitle.setCellValueFactory(c -> {
            String t = c.getValue().getProblemTitle();
            return new SimpleStringProperty(t != null && !t.isBlank() ? t : "Bài #" + c.getValue().getProblemId());
        });
        colTitle.setPrefWidth(220);

        TableColumn<Submission, String> colLang = new TableColumn<>("Ngôn ngữ");
        colLang.setCellValueFactory(new PropertyValueFactory<>("language"));
        colLang.setPrefWidth(90);
        colLang.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colVerdict = new TableColumn<>("Kết quả");
        colVerdict.setCellValueFactory(new PropertyValueFactory<>("verdict"));
        colVerdict.setPrefWidth(110);
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

        TableColumn<Submission, String> colPass = new TableColumn<>("Test pass");
        colPass.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPassedTests() + "/" + c.getValue().getTotalTests()));
        colPass.setPrefWidth(90);
        colPass.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, Integer> colScore = new TableColumn<>("Điểm");
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colScore.setPrefWidth(70);
        colScore.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colTime = new TableColumn<>("Thời gian");
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExecTimeMs() + "ms"));
        colTime.setPrefWidth(90);
        colTime.setStyle("-fx-alignment: CENTER;");

        TableColumn<Submission, String> colDate = new TableColumn<>("Thời điểm nộp");
        colDate.setCellValueFactory(new PropertyValueFactory<>("submittedAt"));
        colDate.setPrefWidth(160);

        table.getColumns().addAll(colId, colTitle, colLang, colVerdict, colPass, colScore, colTime, colDate);
        table.setItems(submissions);
        VBox.setVgrow(table, Priority.ALWAYS);

        // Preview Code khi click chọn dòng
        Label previewTitle = new Label("Mã nguồn bài nộp được chọn:");
        previewTitle.getStyleClass().add("meta-item");

        codePreviewArea = new TextArea();
        codePreviewArea.setPromptText("Nhấp chọn một lượt nộp bài ở bảng trên để xem lại mã nguồn đã nộp...");
        codePreviewArea.setEditable(false);
        codePreviewArea.setPrefRowCount(7);
        codePreviewArea.getStyleClass().add("code-editor");

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getSourceCode() != null) {
                codePreviewArea.setText(newVal.getSourceCode());
            } else {
                codePreviewArea.setText("");
            }
        });

        contentBox.getChildren().addAll(topInfo, table, previewTitle, codePreviewArea);
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
                        if (obj.has("passedTestCases")) s.setPassedTests(obj.get("passedTestCases").getAsInt());
                        if (obj.has("totalTestCases")) s.setTotalTests(obj.get("totalTestCases").getAsInt());
                        if (obj.has("executionTimeMs")) s.setExecTimeMs(obj.get("executionTimeMs").getAsInt());
                        if (obj.has("memoryUsedKb")) s.setMemoryUsedKb(obj.get("memoryUsedKb").getAsInt());
                        if (obj.has("createdAt")) s.setSubmittedAt(obj.get("createdAt").getAsString());

                        list.add(s);
                    }
                    submissions.setAll(list);
                    totalSubmissionsLabel.setText("Tổng cộng: " + list.size() + " lượt nộp");
                    if (!list.isEmpty()) {
                        table.getSelectionModel().select(0);
                    }
                }
            });
        });
    }
}
