package com.grading.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
import com.grading.client.model.AssessmentSchedule;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import com.grading.client.view.DashboardView;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DashboardController — Quản lý danh sách bài tập của sinh viên theo lớp tín chỉ,
 * xử lý chọn bài tập, đổi lớp học, và đăng xuất.
 */
public class DashboardController {

    private final DashboardView view;
    private final SessionManager sessionManager;
    private Stage stage;

    private Consumer<Problem> onSelectProblem;
    private Runnable onLogout;
    private Timeline assessmentPoller;
    private Timeline countdownTicker;
    private Integer activeAssessmentId;
    private boolean assessmentStateKnown;
    private boolean assessmentDataLoaded;
    private boolean assessmentLobby;
    private boolean assessmentTransitionInProgress;
    private boolean refreshInFlight;
    private boolean disposed;
    private long assessmentStateVersion;
    private long problemRequestVersion;
    private boolean updatingClasses;
    private boolean classesRefreshInFlight;
    private boolean classesRefreshPending;
    private boolean assessmentRefreshPending;
    private boolean assessmentDetailsInFlight;
    private final Consumer<Response> dataChangedListener = this::handleDataChanged;
    private final javafx.animation.PauseTransition dataRefreshDebounce =
            new javafx.animation.PauseTransition(Duration.millis(150));
    private boolean refreshClassesOnChange;
    private boolean refreshProblemsOnChange;
    private boolean refreshAssessmentsOnChange;
    private boolean practiceRequestStarted;
    private boolean classesLoaded;
    private final List<CreditClass> cachedClasses = new ArrayList<>();
    private final java.util.Map<Integer, JsonObject> assessmentCache = new java.util.HashMap<>();

    public DashboardController(DashboardView view, Stage stage) {
        this.view = view;
        this.stage = stage;
        this.sessionManager = SessionManager.getInstance();

        initEvents();
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null) socketClient.addDataChangedListener(dataChangedListener);
        dataRefreshDebounce.setOnFinished(event -> refreshChangedData());
        loadStudentClassesAndProblems();
        startAssessmentPolling();
    }

    private void initEvents() {
        view.setOnSelectProblem(problem -> {
            if (onSelectProblem != null) {
                if (sessionManager.isAssessmentMode()) {
                    CreditClass cur = sessionManager.getCurrentClass();
                    if (cur != null) {
                        if (problem.getClassId() == null || problem.getClassId() <= 0) {
                            problem.setClassId(cur.getId());
                            problem.setClassName(cur.getName());
                        }
                    }
                } else {
                    CreditClass sel = view.getSelectedClass();
                    if (sel != null) {
                        sessionManager.setCurrentClass(sel);
                        if (problem.getClassId() == null || problem.getClassId() <= 0) {
                            problem.setClassId(sel.getId());
                            problem.setClassName(sel.getName());
                        }
                    }
                }
                onSelectProblem.accept(problem);
            }
        });

        view.setOnSelectClass(selectedClass -> {
            if (updatingClasses || sessionManager.isAssessmentMode() || assessmentLobby) return;
            sessionManager.setCurrentClass(selectedClass);
            Integer classId = selectedClass != null ? selectedClass.getId() : null;
            loadProblems(classId);
        });

        view.setOnOpenAssessment(id -> {
            JsonObject assessment = assessmentCache.get(id);
            if (assessment != null && schedule(assessment).canStart(sessionManager.getAssessmentNowEpochMs())
                    && !boolValue(assessment, "completed")) {
                startAssessment(assessment);
            }
        });

        view.setOnCompleteAssessment(this::completeAssessment);

        view.setOnLogout(() -> {
            cleanup();
            sessionManager.clear();
            if (onLogout != null) onLogout.run();
        });
    }

    /**
     * Tải danh sách lớp học mà sinh viên được giáo viên thêm vào,
     * sau đó tải bài tập của lớp đầu tiên.
     */
    public void loadStudentClassesAndProblems() {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            if (classesRefreshInFlight) {
                classesRefreshPending = true;
                return;
            }
            classesRefreshInFlight = true;
            socketClient.getMyClasses(response -> {
                Platform.runLater(() -> {
                    classesRefreshInFlight = false;
                    if (disposed) return;
                    if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) {
                        view.showProblemLoadError("Không thể tải lớp học. Vui lòng thử lại.");
                        return;
                    }
                    List<CreditClass> classes = new ArrayList<>();
                    if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                        for (JsonElement el : response.getData().getAsJsonArray()) {
                            JsonObject obj = el.getAsJsonObject();
                            CreditClass cc = new CreditClass();
                            if (obj.has("id")) cc.setId(obj.get("id").getAsInt());
                            if (obj.has("classCode")) cc.setClassCode(obj.get("classCode").getAsString());
                            if (obj.has("name")) cc.setName(obj.get("name").getAsString());
                            if (obj.has("courseId")) cc.setCourseId(obj.get("courseId").getAsInt());
                            if (obj.has("courseName") && !obj.get("courseName").isJsonNull()) {
                                cc.setCourseName(obj.get("courseName").getAsString());
                            }
                            if (obj.has("courseLanguage") && !obj.get("courseLanguage").isJsonNull()) {
                                cc.setCourseLanguage(obj.get("courseLanguage").getAsString());
                            }
                            if (obj.has("allowedLanguages") && !obj.get("allowedLanguages").isJsonNull()) {
                                cc.setAllowedLanguages(obj.get("allowedLanguages").getAsString());
                            }
                            classes.add(cc);
                        }
                    }
                    cachedClasses.clear();
                    cachedClasses.addAll(classes);
                    classesLoaded = true;
                    CreditClass previousClass = sessionManager.isAssessmentMode()
                            ? sessionManager.getCurrentClass() : view.getSelectedClass();
                    if (previousClass == null) previousClass = sessionManager.getCurrentClass();
                    updatingClasses = true;
                    view.setClasses(classes);
                    if (previousClass != null) {
                        int previousClassId = previousClass.getId();
                        classes.stream().filter(c -> c.getId() == previousClassId).findFirst()
                                .ifPresent(view::selectClass);
                    }
                    updatingClasses = false;

                    // Tải bài tập của lớp đang chọn
                    CreditClass selected = view.getSelectedClass();
                    if (!sessionManager.isAssessmentMode()) sessionManager.setCurrentClass(selected);
                    loadProblems(selected != null ? selected.getId() : null);
                    refreshAssessments(selected != null ? selected.getId() : null);
                    if (classesRefreshPending) {
                        classesRefreshPending = false;
                        loadStudentClassesAndProblems();
                    }
                });
            });
        } else {
            loadProblems(null);
        }
    }

    private void startAssessmentPolling() {
        assessmentPoller = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            CreditClass selected = view.getSelectedClass();
            refreshAssessments(selected != null ? selected.getId() : null);
        }));
        assessmentPoller.setCycleCount(Timeline.INDEFINITE);
        assessmentPoller.play();

        countdownTicker = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            applyAssessmentState(false);
            view.refreshAssessmentCountdowns();
            if (sessionManager.isAssessmentMode()) {
                view.setAssessmentCountdownVisible(true);
                long remaining = sessionManager.getRemainingAssessmentSeconds();
                String formatted = sessionManager.formatRemainingAssessmentTime();
                view.updateAssessmentCountdown(remaining, formatted);
            } else {
                view.setAssessmentCountdownVisible(false);
            }
        }));
        countdownTicker.setCycleCount(Timeline.INDEFINITE);
        countdownTicker.play();

        view.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                cleanup();
            }
        });
    }

    private void refreshAssessments(Integer selectedClassId) {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (disposed || socketClient == null || !socketClient.isConnected()) return;
        if (refreshInFlight || assessmentTransitionInProgress) {
            assessmentRefreshPending = true;
            return;
        }
        refreshInFlight = true;
        long stateVersion = assessmentStateVersion;
        socketClient.getStudentAssessments(response -> Platform.runLater(() -> {
            refreshInFlight = false;
            if (disposed || stateVersion != assessmentStateVersion) return;
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
            java.util.Map<Integer, JsonObject> previous = new java.util.HashMap<>(assessmentCache);
            assessmentCache.clear();
            assessmentDataLoaded = true;
            if (response.getData() != null && response.getData().isJsonArray()) {
                for (JsonElement element : response.getData().getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject item = element.getAsJsonObject();
                    int id = intValue(item, "id");
                    JsonObject old = previous.get(id);
                    if (!item.has("problems") && old != null && old.has("problems")) {
                        item.add("problems", old.get("problems"));
                        if (old.has("problemIds")) item.add("problemIds", old.get("problemIds"));
                    }
                    assessmentCache.put(id, item);
                    sessionManager.synchronizeAssessmentClock(stringValue(item, "serverTime"));
                }
            }
            applyAssessmentState(true);
            if (assessmentRefreshPending) {
                assessmentRefreshPending = false;
                refreshAssessments(null);
            }
        }));
    }

    private void handleDataChanged(Response response) {
        Platform.runLater(() -> {
            if (disposed) return;
            assessmentStateVersion++;
            JsonObject data = response.getData() != null && response.getData().isJsonObject()
                    ? response.getData().getAsJsonObject() : new JsonObject();
            String scope = stringValue(data, "scope");
            refreshClassesOnChange |= scope.isBlank() || List.of("CLASSES", "COURSES", "ENROLLMENTS").contains(scope);
            refreshProblemsOnChange |= scope.isBlank() || !"ASSESSMENTS".equals(scope);
            refreshAssessmentsOnChange = true;
            if (sessionManager.isAssessmentMode()) {
                JsonObject active = assessmentCache.get(sessionManager.getActiveAssessmentId());
                if (active != null) {
                    active.remove("problems");
                    active.remove("problemIds");
                }
            }
            dataRefreshDebounce.playFromStart();
        });
    }

    private void refreshChangedData() {
        if (disposed) return;
        boolean reloadClasses = refreshClassesOnChange;
        boolean reloadProblems = refreshProblemsOnChange;
        boolean reloadAssessments = refreshAssessmentsOnChange;
        refreshClassesOnChange = false;
        refreshProblemsOnChange = false;
        refreshAssessmentsOnChange = false;
        if (reloadClasses) {
            loadStudentClassesAndProblems();
        } else {
            if (reloadProblems) {
                CreditClass selected = view.getSelectedClass();
                loadProblems(selected != null ? selected.getId() : null);
            }
            if (reloadAssessments) refreshAssessments(null);
        }
    }

    private AssessmentSchedule schedule(JsonObject assessment) {
        return AssessmentSchedule.from(stringValue(assessment, "opensAt"), stringValue(assessment, "closesAt"));
    }

    private void applyAssessmentState(boolean refreshProblems) {
        if (disposed || assessmentTransitionInProgress || !assessmentDataLoaded) return;
        long now = sessionManager.getAssessmentNowEpochMs();
        Integer sessionId = sessionManager.getActiveAssessmentId();
        JsonObject active = sessionId == null ? null : assessmentCache.get(sessionId);
        if (active != null && (boolValue(active, "completed") || !schedule(active).canStart(now))) active = null;
        if (active == null && !sessionManager.isAssessmentMode()) {
            active = assessmentCache.values().stream()
                    .filter(a -> boolValue(a, "started") && !boolValue(a, "completed") && schedule(a).canStart(now))
                    .findFirst().orElse(null);
        }
        if (active != null) {
            if (refreshProblems || !sessionManager.isAssessmentMode() || activeAssessmentId == null) {
                enterAssessment(active);
            }
            return;
        }

        boolean wasInAssessment = sessionManager.isAssessmentMode() || activeAssessmentId != null;
        if (wasInAssessment) {
            sessionManager.setActiveAssessment(null, false);
            activeAssessmentId = null;
            CreditClass restoredClass = sessionManager.getCurrentClass();
            if (restoredClass != null) view.selectClass(restoredClass);
        }

        List<DashboardView.AssessmentSummary> pending = assessmentCache.values().stream()
                .filter(a -> schedule(a).isLobbyEligible(stringValue(a, "status"), boolValue(a, "completed"), now))
                .sorted(java.util.Comparator.comparingLong(a -> schedule(a).opensAtEpochMs()))
                .map(a -> new DashboardView.AssessmentSummary(intValue(a, "id"), stringValue(a, "title"),
                        stringValue(a, "classCode"), schedule(a).canStart(now) ? "ACTIVE" : "UPCOMING",
                        stringValue(a, "opensAt"), stringValue(a, "closesAt"), intValue(a, "durationMinutes"),
                        intValue(a, "submittedCount"), intValue(a, "problemCount"), boolValue(a, "started")))
                .toList();
        boolean wasLobby = assessmentLobby;
        assessmentLobby = !pending.isEmpty();
        view.setAssessmentMode(false, null, null, null, 0);
        view.setAssessmentLobby(assessmentLobby);
        view.setAssessments(pending);
        if (assessmentLobby) {
            if (!wasLobby || wasInAssessment) {
                problemRequestVersion++;
                view.setProblems(List.of());
            }
        } else if ((!assessmentStateKnown && !practiceRequestStarted) || wasLobby || wasInAssessment) {
            CreditClass selected = view.getSelectedClass();
            loadProblems(selected != null ? selected.getId() : null);
        }
        assessmentStateKnown = true;
    }

    private void startAssessment(JsonObject assessment) {
        SocketClient socketClient = sessionManager.getSocketClient();
        int id = intValue(assessment, "id");
        if (disposed || assessmentTransitionInProgress || socketClient == null
                || !socketClient.isConnected() || id <= 0) return;
        assessmentTransitionInProgress = true;
        assessmentStateVersion++;
        socketClient.startAssessment(id, response -> Platform.runLater(() -> {
            assessmentTransitionInProgress = false;
            if (disposed) return;
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                assessment.addProperty("started", true);
                JsonObject started = response.getData() != null && response.getData().isJsonObject()
                        ? response.getData().getAsJsonObject() : assessment;
                assessmentCache.put(id, started);
                enterAssessment(started);
                refreshAssessments(null);
            } else {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR,
                        response.getMessage() == null ? "Không thể bắt đầu bài kiểm tra." : response.getMessage(),
                        javafx.scene.control.ButtonType.OK);
                alert.initOwner(stage);
                alert.showAndWait();
                refreshAssessments(view.getSelectedClass() == null ? null : view.getSelectedClass().getId());
            }
        }));
    }

    private void enterAssessment(JsonObject assessment) {
        if (!assessment.has("problems") || !assessment.get("problems").isJsonArray()) {
            loadActiveAssessmentDetails(intValue(assessment, "id"));
            return;
        }
        int id = intValue(assessment, "id");
        activeAssessmentId = id;
        assessmentStateKnown = true;
        assessmentLobby = false;
        problemRequestVersion++;
        sessionManager.setActiveAssessment(id, false);
        Long remainingSec = Math.max(0, (schedule(assessment).closesAtEpochMs()
                - sessionManager.getAssessmentNowEpochMs() + 999) / 1000);
        sessionManager.setAssessmentClosesAt(stringValue(assessment, "closesAt"), remainingSec);
        int classId = intValue(assessment, "classId");
        CreditClass targetClass = null;
        if (classId > 0) {
            targetClass = cachedClasses.stream().filter(c -> c.getId() == classId).findFirst().orElse(null);
        }
        if (targetClass == null && classId > 0) {
            targetClass = new CreditClass();
            targetClass.setId(classId);
            targetClass.setClassCode(stringValue(assessment, "classCode"));
            targetClass.setName(stringValue(assessment, "className"));
            String allowed = stringValue(assessment, "allowedLanguages");
            if (allowed != null && !allowed.isBlank()) {
                targetClass.setAllowedLanguages(allowed);
            }
        }
        if (targetClass != null) {
            sessionManager.setCurrentClass(targetClass);
            view.selectClass(targetClass);
        }
        view.setAssessmentLobby(false);
        view.setAssessmentMode(true, stringValue(assessment, "title"), stringValue(assessment, "classCode"),
                stringValue(assessment, "closesAt"), intValue(assessment, "durationMinutes"));
        setAssessmentProblems(assessment);
    }

    private void loadActiveAssessmentDetails(int assessmentId) {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (assessmentDetailsInFlight || disposed || socketClient == null || !socketClient.isConnected()) return;
        assessmentDetailsInFlight = true;
        long version = assessmentStateVersion;
        socketClient.getActiveAssessment(null, response -> Platform.runLater(() -> {
            assessmentDetailsInFlight = false;
            if (disposed || version != assessmentStateVersion || !"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
            JsonObject active = response.getData() != null && response.getData().isJsonObject()
                    ? response.getData().getAsJsonObject() : null;
            if (active != null && intValue(active, "id") == assessmentId && active.has("problems")) {
                assessmentCache.put(assessmentId, active);
                sessionManager.synchronizeAssessmentClock(stringValue(active, "serverTime"));
                enterAssessment(active);
            } else {
                assessmentCache.remove(assessmentId);
                sessionManager.setActiveAssessment(null, false);
                activeAssessmentId = null;
                assessmentStateKnown = false;
                applyAssessmentState(false);
                refreshAssessments(null);
            }
        }));
    }

    private void completeAssessment() {
        Integer id = sessionManager.getActiveAssessmentId();
        JsonObject assessment = id == null ? null : assessmentCache.get(id);
        if (disposed || assessmentTransitionInProgress || id == null) return;
        int submitted = assessment == null ? 0 : intValue(assessment, "submittedCount");
        int total = assessment == null ? 0 : intValue(assessment, "problemCount");
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Bạn đã nộp " + submitted + "/" + total + " bài. Sau khi hoàn thành, bạn không thể quay lại bài thi này.",
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText("Kết thúc bài thi?");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                != javafx.scene.control.ButtonType.OK) return;

        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) return;
        assessmentTransitionInProgress = true;
        assessmentStateVersion++;
        socketClient.completeAssessment(id, response -> Platform.runLater(() -> {
            assessmentTransitionInProgress = false;
            if (disposed) return;
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                sessionManager.setActiveAssessment(null, true);
                assessmentCache.remove(id);
                activeAssessmentId = null;
                assessmentStateKnown = false;
                assessmentLobby = false;
                view.setAssessmentCountdownVisible(false);
                view.setAssessmentLobby(false);
                view.setAssessmentMode(false, null, null, null, 0);
                CreditClass restoredClass = sessionManager.getCurrentClass();
                if (restoredClass != null) view.selectClass(restoredClass);
                applyAssessmentState(false);
                refreshAssessments(view.getSelectedClass() == null ? null : view.getSelectedClass().getId());
            } else {
                javafx.scene.control.Alert error = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR,
                        response.getMessage() == null ? "Không thể hoàn thành bài kiểm tra." : response.getMessage(),
                        javafx.scene.control.ButtonType.OK);
                error.initOwner(stage);
                error.showAndWait();
            }
        }));
    }

    private void setAssessmentProblems(JsonObject assessment) {
        List<Problem> list = new ArrayList<>();
        int assessmentClassId = intValue(assessment, "classId");
        String assessmentClassName = stringValue(assessment, "className");
        String assessmentAllowedLanguages = stringValue(assessment, "allowedLanguages");

        if (assessment.has("problems") && assessment.get("problems").isJsonArray()) {
            for (JsonElement element : assessment.getAsJsonArray("problems")) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                Problem p = new Problem();
                p.setId(intValue(obj, "id"));
                p.setTitle(stringValue(obj, "title"));
                p.setDifficulty(stringValue(obj, "difficulty"));
                p.setDescription(stringValue(obj, "description"));
                p.setTimeLimitMs(intValue(obj, "timeLimitMs"));
                p.setMemoryLimitKb(intValue(obj, "memoryLimitKb"));

                int pClassId = obj.has("classId") && !obj.get("classId").isJsonNull() ? obj.get("classId").getAsInt() : assessmentClassId;
                if (pClassId <= 0) pClassId = assessmentClassId;
                p.setClassId(pClassId);

                String pClassName = obj.has("className") && !obj.get("className").isJsonNull() ? obj.get("className").getAsString() : assessmentClassName;
                p.setClassName(pClassName);

                String pAllowed = obj.has("allowedLanguages") && !obj.get("allowedLanguages").isJsonNull() ? obj.get("allowedLanguages").getAsString() : assessmentAllowedLanguages;
                if (pAllowed != null && !pAllowed.isBlank()) {
                    p.setAllowedLanguages(pAllowed);
                }

                if (obj.has("problemType") && !obj.get("problemType").isJsonNull()) p.setProblemType(obj.get("problemType").getAsString());
                if (obj.has("oopSpec") && !obj.get("oopSpec").isJsonNull()) p.setOopSpec(obj.get("oopSpec").getAsString());
                p.setStatus(stringValue(obj, "verdict"));
                list.add(p);
            }
        }
        view.setProblems(list);
    }

    private String stringValue(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private int intValue(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : 0;
    }

    private boolean boolValue(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() && object.get(field).getAsBoolean();
    }

    /**
     * Nạp danh sách bài tập theo lớp tín chỉ từ Server.
     */
    public void loadProblems(Integer classId) {
        if (disposed || sessionManager.isAssessmentMode() || assessmentLobby) return;
        practiceRequestStarted = true;
        long requestVersion = ++problemRequestVersion;
        if (classesLoaded && cachedClasses.isEmpty()) {
            view.setProblemsLoading(false);
            view.setProblems(List.of());
            return;
        }
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            view.setProblemsLoading(true);
            socketClient.getProblems(classId, response -> Platform.runLater(() -> {
                if (disposed || requestVersion != problemRequestVersion
                        || sessionManager.isAssessmentMode() || assessmentLobby) return;
                view.setProblemsLoading(false);
                handleProblemsResponse(response);
            }));
        } else {
            view.setProblems(createDefaultProblems());
        }
    }

    private void handleProblemsResponse(Response response) {
        if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null) {
            List<Problem> list = new ArrayList<>();
            try {
                if (response.getData().isJsonArray()) {
                    JsonArray arr = response.getData().getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        Problem p = new Problem();
                        if (obj.has("id")) p.setId(obj.get("id").getAsInt());
                        if (obj.has("title")) p.setTitle(obj.get("title").getAsString());
                        if (obj.has("difficulty")) p.setDifficulty(obj.get("difficulty").getAsString());
                        if (obj.has("timeLimitMs")) p.setTimeLimitMs(obj.get("timeLimitMs").getAsInt());
                        if (obj.has("memoryLimitKb")) p.setMemoryLimitKb(obj.get("memoryLimitKb").getAsInt());
                        if (obj.has("description")) p.setDescription(obj.get("description").getAsString());
                        if (obj.has("classId") && !obj.get("classId").isJsonNull()) p.setClassId(obj.get("classId").getAsInt());
                        if (obj.has("className") && !obj.get("className").isJsonNull()) p.setClassName(obj.get("className").getAsString());
                        if (obj.has("problemType") && !obj.get("problemType").isJsonNull()) p.setProblemType(obj.get("problemType").getAsString());
                        if (obj.has("oopSpec") && !obj.get("oopSpec").isJsonNull()) p.setOopSpec(obj.get("oopSpec").getAsString());
                        p.setStatus(obj.has("status") && !obj.get("status").isJsonNull()
                                ? com.grading.client.model.Submission.formatVerdict(obj.get("status").getAsString())
                                : sessionManager.getProblemStatus(p.getId()));
                        list.add(p);
                    }
                }
                view.setProblems(list);
            } catch (Exception ex) {
                view.showProblemLoadError("Không thể đọc danh sách bài tập.");
            }
        } else {
            view.showProblemLoadError("Không thể tải bài tập. Vui lòng kiểm tra kết nối.");
        }
    }

    private List<Problem> createDefaultProblems() {
        List<Problem> list = new ArrayList<>();
        Problem p1 = new Problem();
        p1.setId(1);
        p1.setTitle("Hello World");
        p1.setDifficulty("EASY");
        p1.setTimeLimitMs(2000);
        p1.setMemoryLimitKb(262144);
        p1.setDescription("In ra 'Hello, World!' (không có dấu ngoặc kép).\n\n**Input:** Không có input.\n\n**Output:** Một dòng duy nhất: `Hello, World!`");
        list.add(p1);

        Problem p2 = new Problem();
        p2.setId(2);
        p2.setTitle("Tổng hai số");
        p2.setDifficulty("EASY");
        p2.setTimeLimitMs(2000);
        p2.setMemoryLimitKb(262144);
        p2.setDescription("Cho hai số nguyên a và b, hãy in ra tổng của chúng.\n\n**Input:** Một dòng chứa hai số nguyên a và b (−10^9 ≤ a, b ≤ 10^9).\n\n**Output:** In ra một số nguyên duy nhất là a + b.");
        list.add(p2);

        return list;
    }

    public void setOnSelectProblem(Consumer<Problem> callback) {
        this.onSelectProblem = callback;
    }

    public void setOnLogout(Runnable callback) {
        this.onLogout = callback;
    }

    public void cleanup() {
        disposed = true;
        assessmentStateVersion++;
        problemRequestVersion++;
        if (assessmentPoller != null) assessmentPoller.stop();
        if (countdownTicker != null) countdownTicker.stop();
        dataRefreshDebounce.stop();
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null) socketClient.removeDataChangedListener(dataChangedListener);
    }
}
