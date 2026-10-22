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
                onSelectProblem.accept(problem);
            }
        });

        view.setOnSelectClass(selectedClass -> {
            Integer classId = selectedClass != null ? selectedClass.getId() : null;
            loadProblems(classId);
        });

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
                            if (obj.has("courseName")) cc.setCourseName(obj.get("courseName").getAsString());
                            classes.add(cc);
                        }
                    }
                    view.setClasses(classes);

                    // Tải bài tập của lớp đang chọn
                    CreditClass selected = view.getSelectedClass();
                    checkActiveAssessment(selected != null ? selected.getId() : null);
                });
            });
        } else {
            loadProblems(null);
        }
    }

    private void startAssessmentPolling() {
        assessmentPoller = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            CreditClass selected = view.getSelectedClass();
            checkActiveAssessment(selected != null ? selected.getId() : null);
        }));
        assessmentPoller.setCycleCount(Timeline.INDEFINITE);
        assessmentPoller.play();
        view.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null && assessmentPoller != null) assessmentPoller.stop();
        });
    }

    private void checkActiveAssessment(Integer selectedClassId) {
        SocketClient socketClient = sessionManager.getSocketClient();
        if (socketClient == null || !socketClient.isConnected()) return;
        socketClient.getActiveAssessment(null, response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus())) return;
            JsonObject assessment = response.getData() != null && response.getData().isJsonObject()
                    ? response.getData().getAsJsonObject() : null;
            if (assessment != null) {
                int id = assessment.get("id").getAsInt();
                view.setAssessmentMode(true,
                        assessment.has("title") ? assessment.get("title").getAsString() : "Bài kiểm tra",
                        assessment.has("classCode") ? assessment.get("classCode").getAsString() : null,
                        assessment.has("closesAt") ? assessment.get("closesAt").getAsString() : null,
                        assessment.has("durationMinutes") ? assessment.get("durationMinutes").getAsInt() : 0);
                if (!assessmentStateKnown || activeAssessmentId == null || activeAssessmentId != id) {
                    activeAssessmentId = id;
                    assessmentStateKnown = true;
                    loadProblems(assessment.get("classId").getAsInt());
                }
            } else {
                boolean wasInAssessment = activeAssessmentId != null;
                activeAssessmentId = null;
                view.setAssessmentMode(false, null, null, null, 0);
                if (!assessmentStateKnown || wasInAssessment) {
                    assessmentStateKnown = true;
                    loadProblems(selectedClassId);
                }
            }
        }));
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
