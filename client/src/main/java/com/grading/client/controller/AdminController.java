package com.grading.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.Course;
import com.grading.client.model.CreditClass;
import com.grading.client.model.Problem;
import com.grading.client.model.User;
import com.grading.client.network.SessionManager;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import com.grading.client.view.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AdminController — Điều phối logic cho Cổng Quản Trị Giáo Viên.
 */
public class AdminController {

    private final AdminView view;
    private final Stage stage;
    private final SessionManager sessionManager;
    private Runnable onLogout;

    private List<Course> cachedCourses = new ArrayList<>();
    private List<CreditClass> cachedClasses = new ArrayList<>();

    public AdminController(AdminView view, Stage stage) {
        this.view = view;
        this.stage = stage;
        this.sessionManager = SessionManager.getInstance();

        initEvents();
        loadAllData();
    }

    private void initEvents() {
        // Tab 1: Môn học
        view.getAddCourseBtn().setOnAction(e -> handleAddCourse());
        view.setOnDeleteCourse(this::handleDeleteCourse);

        // Tab 2: Lớp tín chỉ
        view.getAddClassBtn().setOnAction(e -> handleAddClass());
        view.setOnEditClass(this::handleEditClass);
        view.setOnDeleteClass(this::handleDeleteClass);

        // Tab 3: Sinh viên
        view.getStudentFilterClassCombo().setOnAction(e -> {
            CreditClass sel = view.getStudentFilterClassCombo().getSelectionModel().getSelectedItem();
            loadStudents(sel != null ? sel.getId() : 0);
        });
        view.getImportStudentsBtn().setOnAction(e -> handleImportStudents());
        view.setOnEditStudent(this::handleEditStudent);
        view.setOnDeleteStudent(this::handleDeleteStudent);

        // Tab 4: Bài tập theo lớp
        view.getProblemFilterClassCombo().setOnAction(e -> {
            CreditClass sel = view.getProblemFilterClassCombo().getSelectionModel().getSelectedItem();
            loadProblems(sel != null ? sel.getId() : null);
        });
        view.getAddProblemBtn().setOnAction(e -> handleAddProblem());
        view.setOnToggleProblemActive((problem, isChecked) -> handleToggleProblemActive(problem, isChecked));
        view.setOnEditProblem(this::handleEditProblem);

        // Đăng xuất
        view.setOnLogout(() -> {
            sessionManager.clear();
            if (onLogout != null) {
                onLogout.run();
            }
        });
    }

    public void loadAllData() {
        loadCourses();
        loadClasses();
        loadStudents(0);
        loadProblems(null);
    }

    // ─── 1. COURSES ──────────────────────────────────────────────────────────

    private void loadCourses() {
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.getCourses(response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                    List<Course> list = new ArrayList<>();
                    for (JsonElement el : response.getData().getAsJsonArray()) {
                        JsonObject obj = el.getAsJsonObject();
                        Course c = new Course();
                        if (obj.has("id")) c.setId(obj.get("id").getAsInt());
                        if (obj.has("code")) c.setCode(obj.get("code").getAsString());
                        if (obj.has("name")) c.setName(obj.get("name").getAsString());
                        if (obj.has("language")) c.setLanguage(obj.get("language").getAsString());
                        if (obj.has("description")) c.setDescription(obj.get("description").getAsString());
                        if (obj.has("active")) {
                            c.setActive(obj.get("active").getAsBoolean());
                        } else if (obj.has("isActive")) {
                            c.setActive(obj.get("isActive").getAsBoolean());
                        }
                        list.add(c);
                    }
                    cachedCourses = list;
                    view.setCourses(list);
                }
            });
        });
    }

    private void handleToggleProblemActive(Problem problem, boolean isChecked) {
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.toggleProblemActive(problem.getId(), isChecked, response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                    problem.setActive(isChecked);
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.initOwner(stage);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText("Không thể cập nhật trạng thái bài tập");
                    alert.setContentText(response.getMessage());
                    alert.showAndWait();
                    CreditClass sel = view.getProblemFilterClassCombo().getSelectionModel().getSelectedItem();
                    loadProblems(sel != null ? sel.getId() : null);
                }
            });
        });
    }

    private void handleAddCourse() {
        TeacherCourseDialog dialog = new TeacherCourseDialog(stage);
        Optional<Course> result = dialog.showAndWait();
        result.ifPresent(course -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.createCourse(course.getCode(), course.getName(), course.getLanguage(), course.getDescription(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) loadCourses();
                        else showError("Không thể tạo môn học", response.getMessage());
                    });
                });
            }
        });
    }

    private void handleDeleteCourse(Course course) {
        if (course == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Xác nhận xóa môn học");
        confirm.setHeaderText("Xóa môn " + course.getName());
        confirm.setContentText("Các lớp thuộc môn sẽ bị xóa; sinh viên và bài tập sẽ được gỡ khỏi lớp. Tiếp tục?");
        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) return;
        SocketClient client = sessionManager.getSocketClient();
        if (client != null && client.isConnected()) {
            client.deleteCourse(course.getId(), response -> Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                    loadAllData();
                } else showError("Không thể xóa môn học", response.getMessage());
            }));
        }
    }

    // ─── 2. CLASSES ──────────────────────────────────────────────────────────

    private void loadClasses() {
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.getClasses(response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                    List<CreditClass> list = new ArrayList<>();
                    for (JsonElement el : response.getData().getAsJsonArray()) {
                        JsonObject obj = el.getAsJsonObject();
                        CreditClass cc = new CreditClass();
                        if (obj.has("id")) cc.setId(obj.get("id").getAsInt());
                        if (obj.has("classCode")) cc.setClassCode(obj.get("classCode").getAsString());
                        if (obj.has("name")) cc.setName(obj.get("name").getAsString());
                        if (obj.has("courseId")) cc.setCourseId(obj.get("courseId").getAsInt());
                        if (obj.has("courseName")) cc.setCourseName(obj.get("courseName").getAsString());
                        if (obj.has("courseLanguage")) cc.setCourseLanguage(obj.get("courseLanguage").getAsString());
                        if (obj.has("studentCount")) cc.setStudentCount(obj.get("studentCount").getAsInt());
                        list.add(cc);
                    }
                    cachedClasses = list;
                    view.setClasses(list);
                }
            });
        });
    }

    private void handleAddClass() {
        TeacherClassDialog dialog = new TeacherClassDialog(stage, cachedCourses);
        Optional<CreditClass> result = dialog.showAndWait();
        result.ifPresent(creditClass -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.createClass(creditClass.getClassCode(), creditClass.getName(), creditClass.getCourseId(), response -> {
                    Platform.runLater(() -> {
                        loadClasses();
                    });
                });
            }
        });
    }

    private void handleEditClass(CreditClass creditClass) {
        if (creditClass == null) return;
        TeacherClassDialog dialog = new TeacherClassDialog(stage, cachedCourses, creditClass);
        Optional<CreditClass> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.updateClass(updated.getId(), updated.getClassCode(), updated.getName(), updated.getCourseId(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.initOwner(stage);
                            alert.setTitle("Thông báo");
                            alert.setHeaderText(null);
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Cập nhật lớp tín chỉ thành công!");
                            alert.showAndWait();

                            loadClasses();
                            CreditClass selStudent = view.getStudentFilterClassCombo().getSelectionModel().getSelectedItem();
                            loadStudents(selStudent != null ? selStudent.getId() : 0);
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(stage);
                            alert.setTitle("Lỗi");
                            alert.setHeaderText("Cập nhật lớp tín chỉ thất bại");
                            alert.setContentText(response.getMessage());
                            alert.showAndWait();
                        }
                    });
                });
            }
        });
    }

    private void handleDeleteClass(CreditClass creditClass) {
        if (creditClass == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa lớp tín chỉ");
        confirm.setContentText(String.format("Bạn có chắc chắn muốn xóa lớp \"%s\" (%s)?\nLưu ý: Sinh viên trong lớp sẽ được chuyển về trạng thái Chưa gán lớp và các bài tập sẽ chuyển về bài tập chung.",
                creditClass.getName(), creditClass.getClassCode()));

        Optional<javafx.scene.control.ButtonType> btnRes = confirm.showAndWait();
        if (btnRes.isPresent() && btnRes.get() == javafx.scene.control.ButtonType.OK) {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.deleteClass(creditClass.getId(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.initOwner(stage);
                            alert.setTitle("Thông báo");
                            alert.setHeaderText(null);
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Đã xóa lớp tín chỉ thành công!");
                            alert.showAndWait();

                            loadClasses();
                            loadStudents(0);
                            loadProblems(null);
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(stage);
                            alert.setTitle("Lỗi");
                            alert.setHeaderText("Xóa lớp tín chỉ thất bại");
                            alert.setContentText(response.getMessage());
                            alert.showAndWait();
                        }
                    });
                });
            }
        }
    }

    // ─── 3. STUDENTS ─────────────────────────────────────────────────────────

    private void loadStudents(int classId) {
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.getStudents(classId, response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                    List<User.UserDTO> list = new ArrayList<>();
                    for (JsonElement el : response.getData().getAsJsonArray()) {
                        JsonObject obj = el.getAsJsonObject();
                        int id = obj.has("id") ? obj.get("id").getAsInt() : 0;
                        String username = obj.has("username") ? obj.get("username").getAsString() : "";
                        String fullName = obj.has("fullName") ? obj.get("fullName").getAsString() : "";
                        String role = obj.has("role") ? obj.get("role").getAsString() : "STUDENT";
                        Integer cId = obj.has("classId") && !obj.get("classId").isJsonNull() ? obj.get("classId").getAsInt() : null;
                        String cName = obj.has("className") && !obj.get("className").isJsonNull() ? obj.get("className").getAsString() : null;
                        String createdAt = obj.has("createdAt") && !obj.get("createdAt").isJsonNull() ? obj.get("createdAt").getAsString() : "";

                        list.add(new User.UserDTO(id, username, fullName, role, cId, cName, createdAt));
                    }
                    view.setStudents(list);
                }
            });
        });
    }

    private void handleImportStudents() {
        CreditClass currentSelected = view.getStudentFilterClassCombo().getSelectionModel().getSelectedItem();
        TeacherImportStudentsDialog dialog = new TeacherImportStudentsDialog(stage, cachedClasses, currentSelected);
        Optional<TeacherImportStudentsDialog.ImportPayload> result = dialog.showAndWait();

        result.ifPresent(payload -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.importStudents(payload.classId, payload.studentsArray, response -> {
                    Platform.runLater(() -> {
                        loadStudents(payload.classId);
                        loadClasses(); // Cập nhật lại sĩ số lớp

                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Thông báo");
                        alert.setHeaderText(null);
                        alert.setContentText(response.getMessage() != null ? response.getMessage() : "Đã hoàn thành cấp tài khoản sinh viên.");
                        alert.showAndWait();
                    });
                });
            }
        });
    }

    private void handleEditStudent(User.UserDTO student) {
        if (student == null) return;
        EditStudentDialog dialog = new EditStudentDialog(stage, student, cachedClasses);
        Optional<EditStudentDialog.EditStudentPayload> result = dialog.showAndWait();
        result.ifPresent(payload -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.updateStudent(payload.studentId(), payload.fullName(), payload.classId(), payload.newPassword(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.initOwner(stage);
                            alert.setTitle("Thông báo");
                            alert.setHeaderText(null);
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Cập nhật sinh viên thành công!");
                            alert.showAndWait();

                            CreditClass sel = view.getStudentFilterClassCombo().getSelectionModel().getSelectedItem();
                            loadStudents(sel != null ? sel.getId() : 0);
                            loadClasses();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(stage);
                            alert.setTitle("Lỗi");
                            alert.setHeaderText("Cập nhật sinh viên thất bại");
                            alert.setContentText(response.getMessage());
                            alert.showAndWait();
                        }
                    });
                });
            }
        });
    }

    private void handleDeleteStudent(User.UserDTO student) {
        if (student == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa tài khoản sinh viên");
        confirm.setContentText(String.format("Bạn có chắc chắn muốn xóa sinh viên \"%s\" (%s)?\nLưu ý: Mọi bài nộp của sinh viên này cũng sẽ bị xóa.",
                student.getFullName(), student.getUsername()));

        Optional<javafx.scene.control.ButtonType> btnRes = confirm.showAndWait();
        if (btnRes.isPresent() && btnRes.get() == javafx.scene.control.ButtonType.OK) {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                client.deleteStudent(student.getId(), response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.initOwner(stage);
                            alert.setTitle("Thông báo");
                            alert.setHeaderText(null);
                            alert.setContentText(response.getMessage() != null ? response.getMessage() : "Đã xóa sinh viên thành công!");
                            alert.showAndWait();

                            CreditClass sel = view.getStudentFilterClassCombo().getSelectionModel().getSelectedItem();
                            loadStudents(sel != null ? sel.getId() : 0);
                            loadClasses();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.initOwner(stage);
                            alert.setTitle("Lỗi");
                            alert.setHeaderText("Xóa sinh viên thất bại");
                            alert.setContentText(response.getMessage());
                            alert.showAndWait();
                        }
                    });
                });
            }
        }
    }

    // ─── 4. PROBLEMS ─────────────────────────────────────────────────────────

    private void loadProblems(Integer classId) {
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;

        client.getProblems(classId, response -> {
            Platform.runLater(() -> {
                if ("SUCCESS".equalsIgnoreCase(response.getStatus()) && response.getData() != null && response.getData().isJsonArray()) {
                    List<Problem> list = new ArrayList<>();
                    for (JsonElement el : response.getData().getAsJsonArray()) {
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
                        if (obj.has("active")) {
                            p.setActive(obj.get("active").getAsBoolean());
                        } else if (obj.has("isActive")) {
                            p.setActive(obj.get("isActive").getAsBoolean());
                        }
                        list.add(p);
                    }
                    view.setProblems(list);
                }
            });
        });
    }

    private void handleAddProblem() {
        CreditClass currentSelected = view.getProblemFilterClassCombo().getSelectionModel().getSelectedItem();
        TeacherProblemDialog dialog = new TeacherProblemDialog(stage, cachedClasses, currentSelected);
        Optional<Problem> result = dialog.showAndWait();

        result.ifPresent(problem -> {
            SocketClient client = sessionManager.getSocketClient();
            if (client != null && client.isConnected()) {
                JsonObject data = new JsonObject();
                data.addProperty("title", problem.getTitle());
                data.addProperty("difficulty", problem.getDifficulty());
                data.addProperty("timeLimitMs", problem.getTimeLimitMs());
                data.addProperty("memoryLimitKb", problem.getMemoryLimitKb());
                data.addProperty("description", problem.getDescription());
                if (problem.getClassId() != null) {
                    data.addProperty("classId", problem.getClassId());
                }
                data.add("testCases", dialog.getTestCasesArray());
                data.addProperty("sampleInput", dialog.getSampleInput());
                data.addProperty("sampleOutput", dialog.getSampleOutput());
                data.addProperty("hiddenInput", dialog.getSecretInput());
                data.addProperty("hiddenOutput", dialog.getSecretOutput());

                client.createProblem(data, response -> {
                    Platform.runLater(() -> {
                        if ("SUCCESS".equalsIgnoreCase(response.getStatus())) {
                            loadProblems(problem.getClassId());
                        } else {
                            showError("Không thể tạo bài tập", response.getMessage());
                        }
                    });
                });
            }
        });
    }

    private void handleEditProblem(Problem problem) {
        if (problem == null) return;
        SocketClient client = sessionManager.getSocketClient();
        if (client == null || !client.isConnected()) return;
        client.getProblemDetail(problem.getId(), response -> Platform.runLater(() -> {
            if (!"SUCCESS".equalsIgnoreCase(response.getStatus()) || response.getData() == null || !response.getData().isJsonObject()) {
                showError("Không thể tải bài tập", response.getMessage());
                return;
            }
            JsonObject data = response.getData().getAsJsonObject();
            TeacherProblemDialog dialog = new TeacherProblemDialog(stage, cachedClasses,
                    view.getProblemFilterClassCombo().getSelectionModel().getSelectedItem());
            dialog.loadForEditing(problem, data.has("testCases") && data.get("testCases").isJsonArray()
                    ? data.getAsJsonArray("testCases") : new JsonArray());
            dialog.showAndWait().ifPresent(updated -> {
                JsonObject payload = buildProblemPayload(updated, dialog);
                payload.addProperty("id", problem.getId());
                client.updateProblem(payload, updateResponse -> Platform.runLater(() -> {
                    if ("SUCCESS".equalsIgnoreCase(updateResponse.getStatus())) loadProblems(updated.getClassId());
                    else showError("Không thể cập nhật bài tập", updateResponse.getMessage());
                }));
            });
        }));
    }

    private JsonObject buildProblemPayload(Problem problem, TeacherProblemDialog dialog) {
        JsonObject data = new JsonObject();
        data.addProperty("title", problem.getTitle());
        data.addProperty("difficulty", problem.getDifficulty());
        data.addProperty("timeLimitMs", problem.getTimeLimitMs());
        data.addProperty("memoryLimitKb", problem.getMemoryLimitKb());
        data.addProperty("description", problem.getDescription());
        if (problem.getClassId() != null) data.addProperty("classId", problem.getClassId());
        data.add("testCases", dialog.getTestCasesArray());
        return data;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Lỗi");
        alert.setHeaderText(header);
        alert.setContentText(message == null || message.isBlank() ? "Yêu cầu không thành công." : message);
        alert.showAndWait();
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }
}
