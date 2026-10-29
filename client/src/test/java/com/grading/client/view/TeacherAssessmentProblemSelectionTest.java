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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
