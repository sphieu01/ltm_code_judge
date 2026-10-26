package com.grading.client.view;

import com.google.gson.JsonArray;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog chỉnh sửa bài kiểm tra cho Giáo viên.
 */
public class TeacherEditAssessmentDialog extends Dialog<Boolean> {

    private final Stage owner;
    private final TeacherClassDetailView.AssessmentRow assessment;
    private final CreditClass creditClass;
    private final ObservableList<Problem> problems = FXCollections.observableArrayList();
    private final SocketClient client = SessionManager.getInstance().getSocketClient();

    public TeacherEditAssessmentDialog(Stage owner, TeacherClassDetailView.AssessmentRow assessment, CreditClass creditClass, List<Problem> classProblems) {
        this.owner = owner;
        this.assessment = assessment;
        this.creditClass = creditClass;
        if (classProblems != null) {
            this.problems.setAll(classProblems.stream().map(Problem::new).toList());
        }

        initOwner(owner);
        setTitle("Sửa bài kiểm tra - " + assessment.title());
        setResizable(true);

        buildLayout();
        loadExistingProblems();
    }

    private void buildLayout() {
        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("dialog-pane");
        pane.setPrefSize(780, 620);

        String css = ThemeManager.getInstance().isDark() ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(css);
        if (res != null) pane.getStylesheets().add(res.toExternalForm());

        VBox box = new VBox(14);
        box.setPadding(new Insets(16));

        GridPane config = new GridPane();
        config.setHgap(18);
        config.setVgap(14);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(115);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        config.getColumnConstraints().addAll(labelColumn, fieldColumn, labelColumn, fieldColumn);

        TextField titleField = new TextField(assessment.title());
        titleField.setPromptText("Nhập tên bài kiểm tra");

        LocalDateTime initialStart = assessment.opensAt() != null ? assessment.opensAt() : LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0);
        LocalDateTime initialClose = assessment.closesAt() != null ? assessment.closesAt() : initialStart.plusMinutes(Math.max(15, assessment.durationMinutes()));
        int initialDuration = assessment.durationMinutes() > 0 ? assessment.durationMinutes() : 60;

        DatePicker openDate = new DatePicker(initialStart.toLocalDate());
        DatePicker closeDate = new DatePicker(initialClose.toLocalDate());
        Spinner<Integer> openHour = new Spinner<>(0, 23, initialStart.getHour());
        Spinner<Integer> openMinute = new Spinner<>(0, 59, initialStart.getMinute());
        Spinner<Integer> closeHour = new Spinner<>(0, 23, initialClose.getHour());
        Spinner<Integer> closeMinute = new Spinner<>(0, 59, initialClose.getMinute());
        Spinner<Integer> duration = new Spinner<>(5, 300, initialDuration, 5);
        for (Spinner<Integer> spinner : List.of(openHour, openMinute, closeHour, closeMinute, duration)) spinner.setEditable(true);

        config.add(new Label("Tên kiểm tra:"), 0, 0);
        config.add(titleField, 1, 0, 3, 1);
        config.add(new Label("Bắt đầu:"), 0, 1);
        config.add(dateTimeBox(openDate, openHour, openMinute), 1, 1);
        config.add(new Label("Kết thúc:"), 2, 1);
        config.add(dateTimeBox(closeDate, closeHour, closeMinute), 3, 1);
        config.add(new Label("Thời lượng:"), 0, 2);
        HBox durationBox = new HBox(8, duration, new Label("phút"));
        durationBox.setAlignment(Pos.CENTER_LEFT);
        config.add(durationBox, 1, 2);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12.5px;");

        TableView<Problem> problemTable = new TableView<>(problems);
        problemTable.getStyleClass().add("table-view");
        TableColumn<Problem, String> colTitle = new TableColumn<>("Bài tập");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setMinWidth(320);

        TableColumn<Problem, String> colDiff = new TableColumn<>("Độ khó");
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colDiff.setMinWidth(90);
        colDiff.setMaxWidth(130);
        colDiff.setStyle("-fx-alignment: CENTER;");

        TableColumn<Problem, Boolean> colChoose = new TableColumn<>("Chọn bài");
        colChoose.setMinWidth(90);
        colChoose.setMaxWidth(110);
        colChoose.setStyle("-fx-alignment: CENTER;");
        colChoose.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isActive()));
        colChoose.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(e -> {
                    Problem p = getTableView().getItems().get(getIndex());
                    if (p != null) p.setActive(check.isSelected());
                });
            }
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    check.setSelected(Boolean.TRUE.equals(item));
                    setGraphic(check);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        problemTable.getColumns().addAll(colTitle, colDiff, colChoose);
        problemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(problemTable, Priority.ALWAYS);

        box.getChildren().addAll(config, problemTable, validation);
        pane.setContent(box);

        ButtonType saveType = new ButtonType("Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        Button saveBtn = (Button) pane.lookupButton(saveType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");
        Button cancelBtn = (Button) pane.lookupButton(cancelType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String newTitle = titleField.getText() == null ? "" : titleField.getText().trim();
            if (newTitle.isEmpty()) {
                validation.setText("Tên bài kiểm tra không được để trống.");
                titleField.requestFocus();
                event.consume();
                return;
            }

            LocalDateTime opensAt = LocalDateTime.of(openDate.getValue(), LocalTime.of(openHour.getValue(), openMinute.getValue()));
            LocalDateTime closesAt = LocalDateTime.of(closeDate.getValue(), LocalTime.of(closeHour.getValue(), closeMinute.getValue()));

            if (!closesAt.isAfter(opensAt)) {
                validation.setText("Thời gian kết thúc phải sau thời gian bắt đầu.");
                event.consume();
                return;
            }
            if (Duration.between(opensAt, closesAt).toMinutes() < duration.getValue()) {
                validation.setText("Khoảng mở bài phải dài ít nhất bằng thời lượng kiểm tra.");
                event.consume();
                return;
            }

            JsonArray selectedIds = new JsonArray();
            problems.stream().filter(Problem::isActive).forEach(p -> selectedIds.add(p.getId()));
            if (selectedIds.isEmpty()) {
                validation.setText("Hãy chọn ít nhất một bài tập cho bài kiểm tra.");
                event.consume();
                return;
            }

            validation.setText("");
            saveBtn.setDisable(true);

            client.updateAssessment(assessment.id(), newTitle, opensAt.toString(), closesAt.toString(),
                    duration.getValue(), selectedIds, response -> Platform.runLater(() -> {
                        saveBtn.setDisable(false);
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            setResult(true);
                            close();
                        } else {
                            validation.setText(response.getMessage() != null ? response.getMessage() : "Cập nhật bài kiểm tra thất bại.");
                        }
                    }));
            event.consume();
        });
    }

    private void loadExistingProblems() {
        if (client == null || !client.isConnected()) return;
        client.getAssessmentMonitor(assessment.id(), response -> Platform.runLater(() -> {
            if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonObject()) {
                var data = response.getData().getAsJsonObject();
                if (data.has("problems") && data.get("problems").isJsonArray()) {
                    Set<Integer> activeProblemIds = new HashSet<>();
                    for (var el : data.getAsJsonArray("problems")) {
                        if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                            activeProblemIds.add(el.getAsJsonObject().get("id").getAsInt());
                        }
                    }
                    for (Problem p : problems) {
                        p.setActive(activeProblemIds.contains(p.getId()));
                    }
                }
            }
        }));
    }

    private HBox dateTimeBox(DatePicker date, Spinner<Integer> hour, Spinner<Integer> minute) {
        date.setPrefWidth(145);
        hour.setPrefWidth(72);
        minute.setPrefWidth(72);
        HBox box = new HBox(7, date, hour, new Label(":"), minute);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
