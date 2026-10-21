package com.grading.server.service;

import com.grading.server.model.Course;
import com.grading.server.model.CreditClass;
import com.grading.server.model.User;
import com.grading.server.repository.AcademicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Service xử lý logic nghiệp vụ cho Môn học (Courses),
 * Lớp tín chỉ (Classes), và Quản lý Sinh viên.
 */
public class AcademicService {

    private static final Logger logger = LoggerFactory.getLogger(AcademicService.class);

    private final AcademicRepository academicRepository;

    public AcademicService(AcademicRepository academicRepository) {
        this.academicRepository = academicRepository;
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

    public void updateClass(int id, String classCode, String name, int courseId) throws SQLException {
        if (classCode == null || classCode.isBlank()) {
            throw new IllegalArgumentException("Mã lớp tín chỉ không được để trống");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên lớp tín chỉ không được để trống");
        }
        if (courseId <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn môn học hợp lệ cho lớp");
        }
        academicRepository.updateClass(id, classCode.trim(), name.trim(), courseId);
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
}
