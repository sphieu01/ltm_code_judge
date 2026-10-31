package com.grading.client.view;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
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
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import com.grading.client.controller.AdminController;
import com.grading.client.model.User;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    void classTabCountsAppearTogetherAfterStudentsStatsAndProblemsAreReady() throws Exception {
        for (boolean dialog : List.of(false, true)) {
            CapturingClient client = new CapturingClient();
            Object screen = onFx(() -> {
                SessionManager.getInstance().setSocketClient(client);
                CreditClass creditClass = creditClass();
                creditClass.setStudentCount(5); // Summary count from the previous screen is not ready data.
                Object result = dialog ? new TeacherClassDetailDialog(null, creditClass)
                        : new TeacherClassDetailView(null, creditClass);
                assertEquals("Sinh viên", ((Tab) field(result, "studentsTab")).getText());
                assertEquals("Bài tập của lớp", ((Tab) field(result, "classProblemsTab")).getText());
                return result;
            });
            client.reply(MessageType.GET_STUDENTS,
                    "[{\"id\":1,\"username\":\"SV001\"},{\"id\":2,\"username\":\"SV002\"}]");
            onFx(() -> {
                assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
                TableView<?> students = field(screen, "studentTable");
                assertEquals(2, students.getItems().size());
                assertTrue(TableViewSupport.isLoading(students), "Wait for solved-count statistics too");
                return null;
            });
            client.reply(MessageType.GET_CLASS_PROBLEMS,
                    "[{\"id\":1,\"title\":\"A\"},{\"id\":2,\"title\":\"B\"},{\"id\":3,\"title\":\"C\"}]");
            onFx(() -> {
                assertEquals("Bài tập của lớp", ((Tab) field(screen, "classProblemsTab")).getText());
                assertFalse(TableViewSupport.isLoading(field(screen, "classProblemTable")));
                assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
                return null;
            });
            client.reply(MessageType.GET_CLASS_STUDENT_STATS, "[{\"studentId\":1,\"solvedCount\":4}]");
            onFx(() -> {
                assertEquals("Sinh viên (2)", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp (3)", ((Tab) field(screen, "classProblemsTab")).getText());
                TableView<?> students = field(screen, "studentTable");
                assertFalse(TableViewSupport.isLoading(students));
                assertEquals("4", students.getColumns().get(2).getCellObservableValue(0).getValue());
                reloadClassTable(screen, "loadStudents");
                reloadClassTable(screen, "loadProblems");
                assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp", ((Tab) field(screen, "classProblemsTab")).getText());
                return null;
            });
            client.reply(MessageType.GET_STUDENTS, "[]");
            client.reply(MessageType.GET_CLASS_PROBLEMS, "[]");
            onFx(() -> {
                assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp", ((Tab) field(screen, "classProblemsTab")).getText());
                return null;
            });
            client.reply(MessageType.GET_CLASS_STUDENT_STATS, "[]");
            onFx(() -> {
                assertEquals("Sinh viên (0)", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp (0)", ((Tab) field(screen, "classProblemsTab")).getText());
                assertFalse(TableViewSupport.isLoading(field(screen, "studentTable")));
                return null;
            });
        }
    }

    @Test
    void classCountsAlsoWaitForSlowerProblemsAndStaySynchronizedOnReload() throws Exception {
        for (boolean dialog : List.of(false, true)) {
            CapturingClient client = new CapturingClient();
            Object screen = onFx(() -> {
                SessionManager.getInstance().setSocketClient(client);
                return dialog ? new TeacherClassDetailDialog(null, creditClass())
                        : new TeacherClassDetailView(null, creditClass());
            });
            client.reply(MessageType.GET_STUDENTS, "[{\"id\":1,\"username\":\"SV001\"}]");
            client.reply(MessageType.GET_CLASS_STUDENT_STATS, "[]");
            onFx(() -> {
                assertFalse(TableViewSupport.isLoading(field(screen, "studentTable")));
                assertTrue(TableViewSupport.isLoading(field(screen, "classProblemTable")));
                assertClassCountsHidden(screen);
                return null;
            });
            client.reply(MessageType.GET_CLASS_PROBLEMS, "[{\"id\":1,\"title\":\"A\"}]");
            onFx(() -> {
                assertEquals("Sinh viên (1)", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp (1)", ((Tab) field(screen, "classProblemsTab")).getText());
                reloadClassTable(screen, "loadProblems");
                assertClassCountsHidden(screen);
                return null;
            });
            client.reply(MessageType.GET_CLASS_PROBLEMS, "[]");
            onFx(() -> {
                assertEquals("Sinh viên (1)", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp (0)", ((Tab) field(screen, "classProblemsTab")).getText());
                reloadClassTable(screen, "loadStudents");
                assertClassCountsHidden(screen);
                return null;
            });
            client.reply(MessageType.GET_STUDENTS, "[]");
            onFx(() -> { assertClassCountsHidden(screen); return null; });
            client.reply(MessageType.GET_CLASS_STUDENT_STATS, "[]");
            onFx(() -> {
                assertEquals("Sinh viên (0)", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp (0)", ((Tab) field(screen, "classProblemsTab")).getText());
                return null;
            });
        }
    }

    private static void assertClassCountsHidden(Object screen) throws Exception {
        assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
        assertEquals("Bài tập của lớp", ((Tab) field(screen, "classProblemsTab")).getText());
    }

    @Test
    void failedClassLoadsDoNotPublishAnUnconfirmedCount() throws Exception {
        for (boolean dialog : List.of(false, true)) {
            CapturingClient client = new CapturingClient();
            Object screen = onFx(() -> {
                SessionManager.getInstance().setSocketClient(client);
                return dialog ? new TeacherClassDetailDialog(null, creditClass())
                        : new TeacherClassDetailView(null, creditClass());
            });
            client.reply(MessageType.GET_STUDENTS, "[{\"id\":1,\"username\":\"SV001\"}]");
            onFx(() -> {
                Response failure = new Response();
                failure.setStatus("FAIL");
                client.callbacks.get(MessageType.GET_CLASS_STUDENT_STATS).accept(failure);
                client.callbacks.get(MessageType.GET_CLASS_PROBLEMS).accept(failure);
                return null;
            });
            onFx(() -> {
                assertEquals("Sinh viên", ((Tab) field(screen, "studentsTab")).getText());
                assertEquals("Bài tập của lớp", ((Tab) field(screen, "classProblemsTab")).getText());
                assertFalse(TableViewSupport.isLoading(field(screen, "studentTable")));
                assertFalse(TableViewSupport.isLoading(field(screen, "classProblemTable")));
                return null;
            });
        }
    }

    private static void reloadClassTable(Object screen, String methodName) throws Exception {
        var method = screen.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(screen);
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
    void openingMonitorFromClassKeepsTheScreenVisibleAndLoadsProgress() throws Exception {
        CapturingClient client = new CapturingClient();
        Stage stage = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            SessionManager.getInstance().setCurrentUser(new User(7, "teacher", "Teacher", "TEACHER"));
            AdminView admin = new AdminView();
            Stage window = new Stage();
            Scene scene = new Scene(admin, 1200, 760);
            window.setScene(scene);
            ThemeManager.getInstance().registerScene(scene);
            AdminController controller = new AdminController(admin, window);
            var openClass = AdminController.class.getDeclaredMethod("handleViewClass", CreditClass.class);
            openClass.setAccessible(true);
            openClass.invoke(controller, creditClass());
            window.show();
            return window;
        });
        try {
            client.reply(MessageType.GET_CLASS_ASSESSMENTS, """
                    [{"id":50,"title":"Live exam","status":"ACTIVE","opensAt":"2026-11-01T09:00:00",
                      "closesAt":"2026-11-01T10:00:00","durationMinutes":60}]
                    """);
            onFx(() -> {
                var root = stage.getScene().getRoot();
                TabPane tabs = (TabPane) root.lookup(".tab-pane");
                tabs.getSelectionModel().selectLast();
                root.applyCss();
                root.layout();
                Button open = root.lookupAll(".button").stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> "Mở giám sát".equals(button.getText())).findFirst().orElseThrow();
                open.fire();
                assertInstanceOf(TeacherAssessmentMonitorView.class, stage.getScene().getRoot());
                assertEquals(50, client.data.get(MessageType.GET_ASSESSMENT_MONITOR).get("assessmentId").getAsInt());
                return null;
            });
            client.reply(MessageType.GET_ASSESSMENT_MONITOR, """
                    {"title":"Live exam","status":"ACTIVE","opensAt":"2026-11-01T09:00:00",
                     "closesAt":"2026-11-01T10:00:00","progress":[
                       {"username":"SV001","fullName":"Student One","submittedCount":1,"totalProblems":2,
                        "solvedCount":1,"started":true,"completed":false}],"submissions":[],"alerts":[]}
                    """);
            onFx(() -> {
                TeacherAssessmentMonitorView monitor = (TeacherAssessmentMonitorView) stage.getScene().getRoot();
                monitor.applyCss();
                monitor.layout();
                assertNotNull(monitor.getTop());
                assertNotNull(monitor.getCenter());
                assertTrue(monitor.getWidth() > 0, "Monitor root must occupy the window");
                TableView<?> table = field(monitor, "progressTable");
                assertEquals(1, table.getItems().size());
                assertTrue(table.getWidth() > 0 && table.getHeight() > 0, "Progress table must be visible");
                assertEquals("SV001", table.getColumns().get(0).getCellObservableValue(0).getValue());
                assertEquals("Đang làm", table.getColumns().get(4).getCellObservableValue(0).getValue());
                saveSnapshot(monitor, "assessment-monitor-light.png");
                ThemeManager.getInstance().setTheme(ThemeManager.Theme.DARK);
                monitor.applyCss();
                monitor.layout();
                saveSnapshot(monitor, "assessment-monitor-dark.png");

                TabPane tabs = (TabPane) monitor.lookup(".tab-pane");
                for (int i = 0; i < tabs.getTabs().size(); i++) {
                    tabs.getSelectionModel().select(i);
                    monitor.applyCss();
                    monitor.layout();
                    TableView<?> visibleTable = field(monitor,
                            List.of("progressTable", "submissionTable", "alertTable").get(i));
                    assertTrue(visibleTable.getWidth() > 0 && visibleTable.getHeight() > 0);
                    assertFalse(TableViewSupport.isLoading(visibleTable));
                }

                Button back = monitor.lookupAll(".button").stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getText().contains("Chi tiết lớp")).findFirst().orElseThrow();
                back.fire();
                assertInstanceOf(TeacherClassDetailView.class, stage.getScene().getRoot());
                assertNull(field(monitor, "poller"), "Leaving the monitor must stop its refresh timer");
                return null;
            });
        } finally {
            onFx(() -> {
                if (stage.getScene().getRoot() instanceof TeacherAssessmentMonitorView monitor) monitor.stopPolling();
                stage.close();
                ThemeManager.getInstance().registerScene(null);
                ThemeManager.getInstance().setTheme(ThemeManager.Theme.LIGHT);
                SessionManager.getInstance().setCurrentUser(null);
                return null;
            });
        }
    }

    @Test
    void backgroundMonitorRefreshPreservesSelectionScrollAndUnchangedRows() throws Exception {
        CapturingClient client = new CapturingClient();
        TeacherAssessmentMonitorView view = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            TeacherAssessmentMonitorView monitor = new TeacherAssessmentMonitorView(null, 50, "Exam");
            new Scene(monitor, 1200, 660);
            monitor.resize(1200, 660);
            ThemeManager.getInstance().applyTheme(monitor.getScene());
            AssessmentMonitorRefresh refresh = field(monitor, "refresher");
            refresh.refresh();
            assertTrue(TableViewSupport.isLoading(field(monitor, "progressTable")));
            return monitor;
        });
        String initial = monitorData(false).toString();
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, initial);
        List<Object> selectedKeys = new ArrayList<>();
        List<Object> anchorKeys = new ArrayList<>();
        List<Double> offsets = new ArrayList<>();
        onFx(() -> {
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            var pending = client.callbacks.get(MessageType.GET_ASSESSMENT_MONITOR);
            refresh.refresh();
            assertSame(pending, client.callbacks.get(MessageType.GET_ASSESSMENT_MONITOR), "Do not overlap polls");
            TabPane tabs = (TabPane) view.lookup(".tab-pane");
            for (int i = 0; i < 3; i++) {
                tabs.getSelectionModel().select(i);
                view.applyCss();
                view.layout();
                TableView<?> table = field(view, monitorTableNames().get(i));
                assertFalse(TableViewSupport.isLoading(table));
                assertFalse(table.lookup(".table-loading-overlay").isVisible());
                // The teacher can keep interacting while the request is pending.
                table.getSelectionModel().select(30);
                table.getFocusModel().focus(30);
                VirtualFlow<?> flow = (VirtualFlow<?>) table.lookup(".virtual-flow");
                flow.scrollToTop(23);
                flow.layout();
                flow.scrollPixels(11);
                flow.layout();
                selectedKeys.add(monitorRowKey(table.getSelectionModel().getSelectedItem()));
                anchorKeys.add(monitorRowKey(table.getItems().get(flow.getFirstVisibleCell().getIndex())));
                offsets.add(flow.getFirstVisibleCell().getLayoutY());
            }
            return null;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, monitorData(true).toString());
        AtomicInteger rowChanges = new AtomicInteger();
        onFx(() -> {
            TabPane tabs = (TabPane) view.lookup(".tab-pane");
            for (int i = 0; i < 3; i++) {
                tabs.getSelectionModel().select(i);
                view.applyCss();
                view.layout();
                TableView<?> table = field(view, monitorTableNames().get(i));
                assertEquals(81, table.getItems().size());
                table.getItems().addListener((ListChangeListener<Object>) change -> rowChanges.incrementAndGet());
                assertEquals(selectedKeys.get(i), monitorRowKey(table.getSelectionModel().getSelectedItem()));
                assertEquals(selectedKeys.get(i), monitorRowKey(table.getFocusModel().getFocusedItem()));
                VirtualFlow<?> flow = (VirtualFlow<?>) table.lookup(".virtual-flow");
                assertEquals(anchorKeys.get(i), monitorRowKey(table.getItems().get(flow.getFirstVisibleCell().getIndex())));
                assertEquals(offsets.get(i), flow.getFirstVisibleCell().getLayoutY(), 0.5);
            }
            TableView<TeacherAssessmentMonitorView.SubmissionRow> submissions = field(view, "submissionTable");
            assertEquals(1030, submissions.getSelectionModel().getSelectedItem().id());
            assertEquals("100", submissions.getSelectionModel().getSelectedItem().score());
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            return null;
        });
        List<Object> selectedRows = onFx(() -> {
            List<Object> rows = new ArrayList<>();
            for (String name : monitorTableNames()) {
                TableView<?> table = field(view, name);
                rows.add(table.getSelectionModel().getSelectedItem());
            }
            return rows;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, monitorData(true).toString());
        onFx(() -> {
            assertEquals(0, rowChanges.get(), "Unchanged polls must not replace the row list");
            for (int i = 0; i < 3; i++) {
                TableView<?> table = field(view, monitorTableNames().get(i));
                assertSame(selectedRows.get(i), table.getSelectionModel().getSelectedItem(), "Unchanged rows are reused");
            }
            return null;
        });
    }

    @Test
    void failedBackgroundPollKeepsDataAndCanRetryAndIgnoresResponsesAfterLeaving() throws Exception {
        CapturingClient client = new CapturingClient();
        TeacherAssessmentMonitorView view = onFx(() -> {
            SessionManager.getInstance().setSocketClient(client);
            TeacherAssessmentMonitorView monitor = new TeacherAssessmentMonitorView(null, 50, "Exam");
            AssessmentMonitorRefresh refresh = field(monitor, "refresher");
            refresh.refresh();
            return monitor;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, monitorData(false).toString());
        onFx(() -> {
            TableView<?> table = field(view, "submissionTable");
            table.getSelectionModel().select(10);
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            Response failure = new Response();
            failure.setStatus("FAIL");
            failure.setMessage("Request timed out");
            client.callbacks.get(MessageType.GET_ASSESSMENT_MONITOR).accept(failure);
            return null;
        });
        onFx(() -> {
            TableView<?> table = field(view, "submissionTable");
            assertEquals(80, table.getItems().size());
            assertEquals(1010, monitorRowKey(table.getSelectionModel().getSelectedItem()));
            assertFalse(TableViewSupport.isLoading(table));
            assertTrue(((Label) field(view, "summary")).getText().contains("dữ liệu trước đó"));
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            return null;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, "{\"progress\":[],\"submissions\":\"invalid\"}");
        onFx(() -> {
            TableView<?> table = field(view, "progressTable");
            assertEquals(80, table.getItems().size(), "Malformed responses must not partially clear valid data");
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            return null;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, monitorData(true).toString());
        onFx(() -> {
            TableView<?> table = field(view, "submissionTable");
            assertEquals(81, table.getItems().size());
            assertEquals(1010, monitorRowKey(table.getSelectionModel().getSelectedItem()));
            assertFalse(((Label) field(view, "summary")).getText().contains("dữ liệu trước đó"));
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            view.stopPolling();
            return null;
        });
        client.reply(MessageType.GET_ASSESSMENT_MONITOR, "{\"progress\":[],\"submissions\":[],\"alerts\":[]}");
        onFx(() -> {
            TableView<?> table = field(view, "submissionTable");
            assertEquals(81, table.getItems().size(), "Ignore the response after the monitor is closed");
            return null;
        });
    }

    private static List<String> monitorTableNames() {
        return List.of("progressTable", "submissionTable", "alertTable");
    }

    private static Object monitorRowKey(Object row) {
        if (row instanceof TeacherAssessmentMonitorView.ProgressRow progress) return progress.username();
        if (row instanceof TeacherAssessmentMonitorView.SubmissionRow submission) return submission.id();
        return ((TeacherAssessmentMonitorView.AlertRow) row).id();
    }

    private static JsonObject monitorData(boolean updated) {
        JsonObject data = new JsonObject();
        data.addProperty("title", "Exam");
        data.addProperty("status", "ACTIVE");
        JsonArray progress = new JsonArray();
        JsonArray submissions = new JsonArray();
        JsonArray alerts = new JsonArray();
        for (int i = updated ? -1 : 0; i < 80; i++) {
            JsonObject student = new JsonObject();
            student.addProperty("username", "SV" + String.format("%03d", i));
            student.addProperty("fullName", "Student " + i);
            student.addProperty("submittedCount", updated && i == 30 ? 2 : 1);
            student.addProperty("totalProblems", 2);
            progress.add(student);
            JsonObject submission = new JsonObject();
            submission.addProperty("submissionId", 1000 + i);
            submission.addProperty("username", "SV001");
            submission.addProperty("fullName", "Same student");
            submission.addProperty("problemTitle", "Same problem");
            submission.addProperty("language", "python");
            submission.addProperty("verdict", updated && i == 30 ? "AC" : "JUDGING");
            submission.addProperty("score", updated && i == 30 ? 100 : 0);
            submission.addProperty("sourceCode", "print(1)");
            submissions.add(submission);
            JsonObject alert = new JsonObject();
            alert.addProperty("id", 2000 + i);
            alert.addProperty("fullName", "Same student");
            alert.addProperty("eventType", "AI_DETECTED");
            alert.addProperty("detail", updated && i == 30 ? "Updated detail" : "Same detail");
            alerts.add(alert);
        }
        data.add("progress", progress);
        data.add("submissions", submissions);
        data.add("alerts", alerts);
        return data;
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
            AssessmentMonitorRefresh refresh = field(view, "refresher");
            refresh.refresh();
            for (String name : monitorTableNames()) {
                assertFalse(TableViewSupport.isLoading(field(view, name)), "Empty initial data still completes loading");
            }
            view.stopPolling();
            return null;
        });
    }

    private static Response success(String json) {
        Response response = new Response();
        response.setStatus("SUCCESS");
        response.setData(JsonParser.parseString(json));
        return response;
    }

    private static void saveSnapshot(javafx.scene.Parent root, String name) throws Exception {
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
