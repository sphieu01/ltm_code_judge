package com.grading.server.service;

import com.grading.server.model.Course;
import com.grading.server.model.CreditClass;
import com.grading.server.model.User;
import com.grading.server.repository.AcademicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.time.LocalDateTime;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/**
 * Service xử lý logic nghiệp vụ cho Môn học (Courses),
 * Lớp tín chỉ (Classes), và Quản lý Sinh viên.
 */
public class AcademicService {

    private static final Logger logger = LoggerFactory.getLogger(AcademicService.class);

    private final AcademicRepository academicRepository;
    private final com.grading.server.repository.AssessmentRepository assessmentRepository;

    public AcademicService(AcademicRepository academicRepository) {
        this.academicRepository = academicRepository;
        this.assessmentRepository = new com.grading.server.repository.AssessmentRepository();
    }

    public List<Course> getAllCourses() throws SQLException {
        return academicRepository.findAllCourses();
    }

    public Course createCourse(Course course) throws SQLException {
        if (course.getCode() == null || course.getCode().isBlank()) {
            throw new IllegalArgumentException("Mã môn học không được để trống");
        }
        if (course.getName() == null || course.getName().isBlank()) {
            throw new IllegalArgumentException("Tên môn học không được để trống");
        }
        return academicRepository.createCourse(course);
    }

    public void updateCourse(Course course) throws SQLException {
        if (course.getId() <= 0) throw new IllegalArgumentException("Môn học không hợp lệ");
        if (course.getCode() == null || course.getCode().isBlank()) throw new IllegalArgumentException("Mã môn học không được để trống");
        if (course.getName() == null || course.getName().isBlank()) throw new IllegalArgumentException("Tên môn học không được để trống");
        academicRepository.updateCourse(course);
    }

    public List<CreditClass> getAllClasses() throws SQLException {
        return academicRepository.findAllClasses();
    }

    public List<CreditClass> getClassesByStudent(int studentId) throws SQLException {
        return academicRepository.findClassesByStudent(studentId);
    }

    public CreditClass createClass(CreditClass creditClass) throws SQLException {
        if (creditClass.getClassCode() == null || creditClass.getClassCode().isBlank()) {
            throw new IllegalArgumentException("Mã lớp tín chỉ không được để trống");
        }
        if (creditClass.getName() == null || creditClass.getName().isBlank()) {
            throw new IllegalArgumentException("Tên lớp tín chỉ không được để trống");
        }
        if (creditClass.getCourseId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn môn học hợp lệ cho lớp");
        }
        return academicRepository.createClass(creditClass);
    }

    public CreditClass getClassById(int classId) throws SQLException {
        return academicRepository.findClassById(classId);
    }

    public void updateClass(int id, String classCode, String name, int courseId) throws SQLException {
        updateClass(id, classCode, name, courseId, "c,cpp,java,python");
    }

    public void updateClass(int id, String classCode, String name, int courseId, String allowedLanguages) throws SQLException {
        if (classCode == null || classCode.isBlank()) {
            throw new IllegalArgumentException("Mã lớp tín chỉ không được để trống");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên lớp tín chỉ không được để trống");
        }
        if (courseId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn môn học hợp lệ cho lớp");
        }
        academicRepository.updateClass(id, classCode.trim(), name.trim(), courseId, allowedLanguages);
    }

    public List<com.grading.server.model.Problem> getProblemsByClassId(int classId) throws SQLException {
        return academicRepository.findProblemsByClassId(classId);
    }

    public void assignProblemsToClass(int classId, List<Integer> problemIds) throws SQLException {
        if (classId <= 0) throw new IllegalArgumentException("Lớp tín chỉ không hợp lệ");
        academicRepository.assignProblemsToClass(classId, problemIds);
    }

    public void removeProblemFromClass(int classId, int problemId) throws SQLException {
        if (classId <= 0 || problemId <= 0) throw new IllegalArgumentException("Thông tin lớp hoặc bài tập không hợp lệ");
        academicRepository.removeProblemFromClass(classId, problemId);
    }

    public void deleteClass(int classId) throws SQLException {
        academicRepository.deleteClass(classId);
    }

    public List<User.UserDTO> getStudentsByClass(int classId) throws SQLException {
        return academicRepository.findStudentsByClass(classId);
    }

    public void setCourseActive(int courseId, boolean active) throws SQLException {
        academicRepository.updateCourseActive(courseId, active);
    }

    public void deleteCourse(int courseId) throws SQLException {
        academicRepository.deleteCourse(courseId);
    }

    public void updateStudent(int studentId, String fullName, Integer classId, String newPassword) throws SQLException {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Họ tên sinh viên không được để trống");
        }
        academicRepository.updateStudent(studentId, fullName.trim(), classId, newPassword);
    }

    public void deleteStudent(int studentId) throws SQLException {
        academicRepository.deleteStudent(studentId);
    }

    public void removeStudentFromClass(int studentId, int classId) throws SQLException {
        if (classId <= 0) throw new IllegalArgumentException("Lớp tín chỉ không hợp lệ");
        academicRepository.removeStudentFromClass(studentId, classId);
    }

    public int createAssessment(int classId, String title, LocalDateTime opensAt, LocalDateTime closesAt,
                                int durationMinutes, int teacherId, List<Integer> problemIds) throws SQLException {
        if (classId <= 0) throw new IllegalArgumentException("Lớp tín chỉ không hợp lệ.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Tên bài kiểm tra không được để trống.");
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt))
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        if (durationMinutes <= 0) throw new IllegalArgumentException("Thời lượng phải lớn hơn 0 phút.");
        if (problemIds == null || problemIds.isEmpty()) throw new IllegalArgumentException("Hãy chọn ít nhất một bài tập.");
        return assessmentRepository.create(classId, title.trim(), opensAt, closesAt,
                durationMinutes, teacherId, problemIds.stream().distinct().toList());
    }

    public void updateAssessment(int assessmentId, String title, LocalDateTime opensAt, LocalDateTime closesAt,
                                 int durationMinutes, List<Integer> problemIds) throws SQLException {
        if (assessmentId <= 0) throw new IllegalArgumentException("Bài kiểm tra không hợp lệ.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Tên bài kiểm tra không được để trống.");
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt))
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        if (durationMinutes <= 0) throw new IllegalArgumentException("Thời lượng phải lớn hơn 0 phút.");
        assessmentRepository.update(assessmentId, title.trim(), opensAt, closesAt,
                durationMinutes, problemIds != null ? problemIds.stream().distinct().toList() : List.of());
    }

    public void deleteAssessment(int assessmentId) throws SQLException {
        if (assessmentId <= 0) throw new IllegalArgumentException("Bài kiểm tra không hợp lệ.");
        assessmentRepository.delete(assessmentId);
    }

    public JsonObject getActiveAssessment(int studentId, Integer classId) throws SQLException {
        return assessmentRepository.findActiveForStudent(studentId, classId);
    }

    public JsonArray getStudentAssessments(int studentId) throws SQLException {
        return assessmentRepository.findForStudent(studentId);
    }

    public JsonArray getStudentAssessmentSummaries(int studentId) throws SQLException {
        return assessmentRepository.findSummariesForStudent(studentId);
    }

    public JsonArray getClassAssessments(int classId, int teacherId) throws SQLException {
        if (classId <= 0) throw new IllegalArgumentException("Lớp tín chỉ không hợp lệ.");
        return assessmentRepository.findByClass(classId, teacherId);
    }

    public JsonObject getAssessmentMonitor(int assessmentId, int teacherId) throws SQLException {
        if (assessmentId <= 0) throw new IllegalArgumentException("Bài kiểm tra không hợp lệ.");
        return assessmentRepository.getMonitor(assessmentId, teacherId);
    }

    public void startAssessment(int assessmentId, int studentId) throws SQLException {
        if (assessmentId <= 0) throw new IllegalArgumentException("Bài kiểm tra không hợp lệ.");
        assessmentRepository.start(assessmentId, studentId);
    }

    public void completeAssessment(int assessmentId, int studentId) throws SQLException {
        if (assessmentId <= 0) throw new IllegalArgumentException("Bài kiểm tra không hợp lệ.");
        assessmentRepository.complete(assessmentId, studentId);
    }

    public int completeExpiredAssessmentAttempts() throws SQLException {
        return assessmentRepository.completeExpiredAttempts();
    }

    public Integer getActiveAssessmentIdForProblem(int studentId, int problemId) throws SQLException {
        return assessmentRepository.findActiveAssessmentIdForProblem(studentId, problemId);
    }

    public boolean isProblemAllowedForStudent(int studentId, int problemId) throws SQLException {
        return assessmentRepository.isProblemAllowedForStudent(studentId, problemId);
    }
}
