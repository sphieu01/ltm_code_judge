package com.grading.client.view;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.MessageType;
import com.grading.client.protocol.Response;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TeacherAssessmentProblemSelectionTest {
    private static final String BANK = """
            [{"id":1,"title":"Visible","active":true},
             {"id":2,"title":"Hidden exam question","active":false}]
            """;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FutureTask<Void> ready = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        Platform.startup(ready);
        ready.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopJavaFx() {
        SessionManager.getInstance().setSocketClient(null);
        Platform.exit();
    }

    @Test
    void creationLoadsWholeBankAndClassReloadDoesNotResetExamSelection() throws Exception {
        CapturingClient client = new CapturingClient();
        TeacherClassDetailView view = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            return new TeacherClassDetailView(null, creditClass());
        });
        assertTrue(client.data.containsKey(MessageType.GET_PROBLEMS));
        assertNull(client.data.get(MessageType.GET_PROBLEMS), "The bank request must not filter by class");
        client.reply(MessageType.GET_CLASS_PROBLEMS, "[{\"id\":1,\"title\":\"Visible\"}]");
        client.reply(MessageType.GET_PROBLEMS, BANK);

        onFx(() -> {
            TableView<Problem> table = field(view, "problemTable");
            TableView<Problem> classTable = field(view, "classProblemTable");
            assertEquals("I/O Matching", classTable.getColumns().get(2).getCellObservableValue(0).getValue());
            assertEquals(List.of(1, 2), table.getItems().stream().map(Problem::getId).toList());
            assertFalse(table.getItems().get(1).isActive(), "Loading must preserve hidden status");
            Set<Integer> selected = field(view, "selectedAssessmentProblemIds");
            selected.add(2);
            view.loadProblems();
            return null;
        });
        client.reply(MessageType.GET_CLASS_PROBLEMS, "[]");
        onFx(() -> {
            TableView<Problem> table = field(view, "problemTable");
            Set<Integer> selected = field(view, "selectedAssessmentProblemIds");
            assertEquals(2, table.getItems().size());
            assertEquals(Set.of(2), selected);
            assertFalse(table.getItems().get(1).isActive());
            return null;
        });
    }

    @Test
    void editingRestoresHiddenSelectionOnlyAfterBothResponsesAndSendsItBack() throws Exception {
        CapturingClient client = new CapturingClient();
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);
        TeacherEditAssessmentDialog dialog = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            var row = new TeacherClassDetailView.AssessmentRow(50, "Exam", "", "", "", "UPCOMING",
                    start, start.plusHours(1), 60);
            return new TeacherEditAssessmentDialog(null, row, creditClass());
        });
        client.reply(MessageType.GET_PROBLEMS, BANK);
        onFx(() -> {
            Button save = field(dialog, "saveButton");
            assertTrue(save.isDisabled());
            return null;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, "{\"problems\":[{\"id\":2}]}");
        onFx(() -> {
            TableView<Problem> table = field(dialog, "problemTable");
            assertEquals(2, table.getItems().size());
            assertEquals(Boolean.FALSE, table.getColumns().get(2).getCellObservableValue(0).getValue());
            assertEquals(Boolean.TRUE, table.getColumns().get(2).getCellObservableValue(1).getValue());
            assertFalse(table.getItems().get(1).isActive());
            Button save = field(dialog, "saveButton");
            assertFalse(save.isDisabled());
            save.fire();
            assertEquals("[2]", client.data.get(MessageType.UPDATE_ASSESSMENT).get("problemIds").toString());
            dialog.close();
            return null;
        });
    }

    @Test
    void loadingSpinnerCoversExistingRowsAndEmptyResponseRestoresEmptyMessage() throws Exception {
        CapturingClient client = new CapturingClient();
        List<Consumer<Response>> replies = new ArrayList<>();
        TableView<String> table = onFx(() -> {
            TableView<String> result = new TableView<>();
            result.setPlaceholder(new Label("Chưa có dữ liệu."));
            TableColumn<String, String> column = new TableColumn<>("Dữ liệu");
            column.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue()));
            result.getColumns().add(column);
            result.getItems().add("Dữ liệu trước khi làm mới");
            TableViewSupport.load(result, client, replies::add, response -> result.getItems().clear());
            StackPane root = new StackPane(result);
            Scene scene = new Scene(root, 680, 160);
            ThemeManager.getInstance().applyTheme(scene);
            root.applyCss();
            root.layout();
            assertTrue(TableViewSupport.isLoading(result));
            var overlay = result.lookup(".table-loading-overlay");
            assertNotNull(overlay);
            assertTrue(overlay.isVisible());
            assertTrue(overlay.getBoundsInParent().getWidth() > 0);
            assertEquals(1, result.getItems().size(), "Reloading must preserve existing rows");
            saveSnapshot(root, "table-loading-light.png");
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.DARK);
            ThemeManager.getInstance().applyTheme(scene);
            root.applyCss();
            root.layout();
            saveSnapshot(root, "table-loading-dark.png");
            ThemeManager.getInstance().setTheme(ThemeManager.Theme.LIGHT);
            replies.get(0).accept(success("[]"));
            return result;
        });
        onFx(() -> {
            assertFalse(TableViewSupport.isLoading(table));
            assertFalse(table.lookup(".table-loading-overlay").isVisible());
            assertTrue(table.getItems().isEmpty());
            assertEquals("Chưa có dữ liệu.", ((Label) table.getPlaceholder()).getText());
            return null;
        });
    }

    @Test
    void failedLoadStopsSpinnerAndRetryWithEmptyResultRestoresOriginalMessage() throws Exception {
        CapturingClient client = new CapturingClient();
        List<Consumer<Response>> replies = new ArrayList<>();
        TableView<String> table = onFx(() -> {
            TableView<String> result = new TableView<>();
            result.setPlaceholder(new Label("Danh sách rỗng."));
            TableViewSupport.load(result, client, replies::add, response -> fail("Failure must not be parsed as data"));
            Response failed = new Response();
            failed.setStatus("FAIL");
            replies.get(0).accept(failed);
            return result;
        });
        onFx(() -> {
            assertFalse(TableViewSupport.isLoading(table));
            assertTrue(((Label) table.getPlaceholder()).getText().contains("Không thể tải"));
            TableViewSupport.load(table, client, replies::add, response -> {});
            replies.get(1).accept(success("[]"));
            return null;
        });
        onFx(() -> {
            assertFalse(TableViewSupport.isLoading(table));
            assertEquals("Danh sách rỗng.", ((Label) table.getPlaceholder()).getText());
            return null;
        });
    }

    @Test
    void olderResponseCannotStopNewSpinnerOrOverwriteNewerData() throws Exception {
        CapturingClient client = new CapturingClient();
        List<Consumer<Response>> replies = new ArrayList<>();
        TableView<String> table = onFx(() -> {
            TableView<String> result = new TableView<>();
            TableViewSupport.load(result, client, replies::add, response -> result.getItems().setAll("old"));
            TableViewSupport.load(result, client, replies::add, response -> result.getItems().setAll("new"));
            replies.get(0).accept(success("[]"));
            return result;
        });
        onFx(() -> {
            assertTrue(TableViewSupport.isLoading(table));
            assertTrue(table.getItems().isEmpty());
            replies.get(1).accept(success("[]"));
            return null;
        });
        onFx(() -> {
            assertFalse(TableViewSupport.isLoading(table));
            assertEquals(List.of("new"), table.getItems());
            return null;
        });
    }

    @Test
    void chainedRequestKeepsSpinnerUntilSecondRequestFinishes() throws Exception {
        CapturingClient client = new CapturingClient();
        List<Consumer<Response>> replies = new ArrayList<>();
        TableView<String> table = onFx(() -> {
            TableView<String> result = new TableView<>();
            TableViewSupport.load(result, client, replies::add,
                    response -> TableViewSupport.load(result, client, replies::add, selected -> {}));
            replies.get(0).accept(success("[]"));
            return result;
        });
        onFx(() -> {
            assertTrue(TableViewSupport.isLoading(table));
            replies.get(1).accept(success("[]"));
            return null;
        });
        onFx(() -> { assertFalse(TableViewSupport.isLoading(table)); return null; });
    }

    @Test
    void disconnectedAndMalformedLoadsAlwaysLeaveTheLoadingState() throws Exception {
        onFx(() -> {
            TableView<String> table = new TableView<>();
            TableViewSupport.load(table, null, done -> fail("No network request without a connection"), response -> {});
            assertFalse(TableViewSupport.isLoading(table));
            assertTrue(((Label) table.getPlaceholder()).getText().contains("Chưa kết nối"));
            return null;
        });
        List<Consumer<Response>> replies = new ArrayList<>();
        TableView<String> table = onFx(() -> {
            TableView<String> result = new TableView<>();
            TableViewSupport.load(result, new CapturingClient(), replies::add,
                    response -> { throw new IllegalArgumentException("Invalid rows"); });
            replies.get(0).accept(success("[]"));
            return result;
        });
        onFx(() -> {
            assertFalse(TableViewSupport.isLoading(table));
            assertTrue(((Label) table.getPlaceholder()).getText().contains("Không thể đọc"));
            return null;
        });
    }

    @Test
    void monitorTablesShareOneRequestAndFinishWithTheirOwnEmptyMessages() throws Exception {
        CapturingClient client = new CapturingClient();
        TeacherAssessmentMonitorView view = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            TeacherAssessmentMonitorView result = new TeacherAssessmentMonitorView(null, 50, "Exam");
            var refresh = TeacherAssessmentMonitorView.class.getDeclaredMethod("refresh");
            refresh.setAccessible(true);
            refresh.invoke(result);
            for (String name : List.of("progressTable", "submissionTable", "alertTable")) {
                TableView<?> table = field(result, name);
                assertTrue(TableViewSupport.isLoading(table));
            }
            return result;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, "{\"progress\":[],\"submissions\":[],\"alerts\":[]}");
        onFx(() -> {
            for (String name : List.of("progressTable", "submissionTable", "alertTable")) {
                TableView<?> table = field(view, name);
                assertFalse(TableViewSupport.isLoading(table));
                assertTrue(table.getItems().isEmpty());
                assertTrue(((Label) table.getPlaceholder()).getText().startsWith("Chưa có"));
            }
            return null;
        });
    }

    private static Response success(String json) {
        Response response = new Response();
        response.setStatus("SUCCESS");
        response.setData(JsonParser.parseString(json));
        return response;
    }

    private static void saveSnapshot(StackPane root, String name) throws Exception {
        var snapshot = root.snapshot(null, null);
        var pixels = snapshot.getPixelReader();
        var image = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, pixels.getArgb(x, y));
        }
        javax.imageio.ImageIO.write(image, "png", java.nio.file.Path.of("target", name).toFile());
    }

    private static CreditClass creditClass() {
        return new CreditClass(10, "CLASS10", "Test class", 1, "Course", 7);
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    private static final class CapturingClient extends SocketClient {
        final Map<MessageType, JsonObject> data = new EnumMap<>(MessageType.class);
        final Map<MessageType, Consumer<Response>> callbacks = new EnumMap<>(MessageType.class);

        CapturingClient() { super("unused", 1); }
        @Override public boolean isConnected() { return true; }
        @Override public String sendRequest(MessageType type, JsonObject request, Consumer<Response> callback) {
            data.put(type, request);
            callbacks.put(type, callback);
            return "captured";
        }

        void reply(MessageType type, String json) throws Exception {
            onFx(() -> {
                Response response = new Response();
                response.setStatus("SUCCESS");
                response.setData(JsonParser.parseString(json));
                callbacks.get(type).accept(response);
                return null;
            });
            onFx(() -> null); // Drain the UI response callback.
        }
    }
}
