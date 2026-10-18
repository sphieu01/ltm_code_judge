package com.grading.server.model;

/**
 * Model đại diện cho một Lớp tín chỉ (Course Section / Credit Class).
 * Lớp tín chỉ gắn với một Môn học và chứa danh sách Sinh viên,
 * các bài tập được cấu hình riêng biệt theo từng lớp tín chỉ.
 */
public class CreditClass {
    private int id;
    private String classCode;   // Ví dụ: "D21CNPM01", "INT2204_1"
    private String name;        // Ví dụ: "Lớp Thực hành Java - Nhóm 1"
    private int courseId;       // Môn học liên kết
    private String courseName;  // Tên môn học
    private String courseLanguage; // Ngôn ngữ lập trình chính của môn
    private int teacherId;      // Giáo viên phụ trách
    private int studentCount;   // Số lượng sinh viên trong lớp
    private String createdAt;

    public CreditClass() {}

    public CreditClass(int id, String classCode, String name, int courseId, String courseName, int teacherId) {
        this.id = id;
        this.classCode = classCode;
        this.name = name;
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacherId = teacherId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getCourseLanguage() { return courseLanguage; }
    public void setCourseLanguage(String courseLanguage) { this.courseLanguage = courseLanguage; }

    public int getTeacherId() { return teacherId; }
    public void setTeacherId(int teacherId) { this.teacherId = teacherId; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return classCode + " - " + name + (courseName != null ? " (" + courseName + ")" : "");
    }
}
