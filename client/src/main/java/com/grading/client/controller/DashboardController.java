package com.grading.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
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
    private Integer activeAssessmentId;
    private boolean assessmentStateKnown;
    private final List<CreditClass> cachedClasses = new ArrayList<>();
    private final java.util.Map<Integer, JsonObject> assessmentCache = new java.util.HashMap<>();

    public DashboardController(DashboardView view, Stage stage) {
        this.view = view;
        this.stage = stage;
        this.sessionManager = SessionManager.getInstance();

        initEvents();
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
            sessionManager.setCurrentClass(selectedClass);
            Integer classId = selectedClass != null ? selectedClass.getId() : null;
            loadProblems(classId);
        });

        view.setOnOpenAssessment(id -> {
            JsonObject assessment = assessmentCache.get(id);
            if (assessment != null && "ACTIVE".equalsIgnoreCase(stringValue(assessment, "status"))) {
                startAssessment(assessment);
            }
        });

        view.setOnCompleteAssessment(this::completeAssessment);

        view.setOnLogout(() -> {
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
            socketClient.getMyClasses(response -> {
                Platform.runLater(() -> {
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
                    view.setClasses(classes);

                    // Tải bài tập của lớp đang chọn
                    CreditClass selected = view.getSelectedClass();
                    sessionManager.setCurrentClass(selected);
                    refreshAssessments(selected != null ? selected.getId() : null);
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
        view.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null && assessmentPoller != null) assessmentPoller.stop();
        });
    }

    private void refreshAssessments(Integer selectedClassId) {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) return;
        socketClient.getStudentAssessments(response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
            assessmentCache.clear();
            List<DashboardView.AssessmentSummary> summaries = new ArrayList<>();
            JsonObject active = null;
            if (response.getData() != null && response.getData().isJsonArray()) {
                for (JsonElement element : response.getData().getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject item = element.getAsJsonObject();
                    int id = intValue(item, "id");
                    assessmentCache.put(id, item);
                    String status = stringValue(item, "status");
                    summaries.add(new DashboardView.AssessmentSummary(id,
                            stringValue(item, "title"), stringValue(item, "classCode"), status,
                            stringValue(item, "opensAt"), stringValue(item, "closesAt"),
                            intValue(item, "durationMinutes"), intValue(item, "submittedCount"),
                            intValue(item, "problemCount"), boolValue(item, "started")));
                    if (active == null && "ACTIVE".equalsIgnoreCase(status)) active = item;
                }
            }
            view.setAssessments(summaries);
            if (active != null) {
                activeAssessmentId = intValue(active, "id");
                assessmentStateKnown = true;
                Integer sessionAssessmentId = sessionManager.getActiveAssessmentId();
                if (sessionManager.isAssessmentMode() && sessionAssessmentId != null
                        && sessionAssessmentId.equals(activeAssessmentId)) {
                    enterAssessment(active);
                } else {
                    sessionManager.setActiveAssessment(null, false);
                    view.setAssessmentMode(false, null, null, null, 0);
                    view.setAssessmentLobby(true);
                    view.setProblems(List.of());
                }
            } else {
                boolean wasInAssessment = activeAssessmentId != null;
                activeAssessmentId = null;
                sessionManager.setActiveAssessment(null, false);
                view.setAssessmentLobby(false);
                view.setAssessmentMode(false, null, null, null, 0);
                if (!assessmentStateKnown || wasInAssessment) {
                    assessmentStateKnown = true;
                    loadProblems(selectedClassId);
                }
            }
        }));
    }

    private void startAssessment(JsonObject assessment) {
        SocketClient socketClient = sessionManager.getSocketClient();
        int id = intValue(assessment, "id");
        if (socketClient == null || !socketClient.isConnected() || id <= 0) return;
        socketClient.startAssessment(id, response -> Platform.runLater(() -> {
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                sessionManager.setActiveAssessment(id, false);
                enterAssessment(assessment);
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
        int id = intValue(assessment, "id");
        activeAssessmentId = id;
        assessmentStateKnown = true;
        sessionManager.setActiveAssessment(id, false);
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

    private void completeAssessment() {
        Integer id = sessionManager.getActiveAssessmentId();
        JsonObject assessment = id == null ? null : assessmentCache.get(id);
        if (id == null || assessment == null) return;
        int submitted = intValue(assessment, "submittedCount");
        int total = intValue(assessment, "problemCount");
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Bạn đã nộp " + submitted + "/" + total + " bài. Sau khi hoàn thành, bạn không thể quay lại bài thi này.",
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText("Hoàn thành và nộp bài thi?");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                != javafx.scene.control.ButtonType.OK) return;

        SocketClient socketClient = sessionManager.getSocketClient();
        socketClient.completeAssessment(id, response -> Platform.runLater(() -> {
            if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                sessionManager.setActiveAssessment(null, true);
                activeAssessmentId = null;
                assessmentStateKnown = false;
                view.setAssessmentLobby(false);
                view.setAssessmentMode(false, null, null, null, 0);
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
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient != null && socketClient.isConnected()) {
            // Tải submission history trước để cập nhật AC/WA badge
            socketClient.getHistory(historyResp -> {
                if (historyResp != null && "SUCCESS".equalsIgnoreCase(historyResp.getStatus())
                        && historyResp.getData() != null && historyResp.getData().isJsonArray()) {
                    JsonArray hist = historyResp.getData().getAsJsonArray();
                    java.util.Map<Integer, String> mostRecent = new java.util.HashMap<>();
                    java.util.Set<Integer> acSet = new java.util.HashSet<>();
                    for (JsonElement el : hist) {
                        if (!el.isJsonObject()) continue;
                        JsonObject sub = el.getAsJsonObject();
                        int pid = sub.has("problemId") ? sub.get("problemId").getAsInt() : 0;
                        String verdict = sub.has("verdict") ? sub.get("verdict").getAsString() : "";
                        if (pid > 0) {
                            if (!mostRecent.containsKey(pid)) {
                                mostRecent.put(pid, verdict);
                            }
                            String u = verdict.toUpperCase();
                            if (u.equals("ACCEPTED") || u.equals("AC") || u.equals("PASSED")) {
                                acSet.add(pid);
                            }
                        }
                    }
                    for (java.util.Map.Entry<Integer, String> entry : mostRecent.entrySet()) {
                        int pid = entry.getKey();
                        if (acSet.contains(pid)) {
                            sessionManager.recordSubmission(pid, "AC");
                        } else {
                            sessionManager.recordSubmission(pid, entry.getValue());
                        }
                    }
                }

                // Tải bài tập theo lớp tín chỉ
                socketClient.getProblems(classId, response -> {
                    Platform.runLater(() -> handleProblemsResponse(response));
                });
            });
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
                        p.setStatus(sessionManager.getProblemStatus(p.getId()));
                        list.add(p);
                    }
                }
                view.setProblems(list);
            } catch (Exception ex) {
                view.setProblems(activeAssessmentId != null ? List.of() : createDefaultProblems());
            }
        } else {
            view.setProblems(activeAssessmentId != null ? List.of() : createDefaultProblems());
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
}
