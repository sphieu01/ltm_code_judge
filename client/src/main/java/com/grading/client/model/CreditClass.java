package com.grading.client.model;

/**
 * Model đại diện cho một Lớp tín chỉ (CreditClass) phía Client.
 */
public class CreditClass {
    private int id;
    private String classCode;   // Mã lớp: "D21CNPM01", "INT2204_1"
    private String name;        // Tên lớp: "Lớp Thực hành Java - Nhóm 1"
    private int courseId;       // ID Môn học
    private String courseName;  // Tên Môn học
    private String courseLanguage; // Ngôn ngữ lập trình chính
    private int teacherId;      // ID Giáo viên
    private int studentCount;   // Sĩ số
    private String allowedLanguages = "c,cpp,java,python"; // Danh sách ngôn ngữ cho phép nộp
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

    public String getAllowedLanguages() {
        return allowedLanguages != null && !allowedLanguages.isBlank() ? allowedLanguages : "c,cpp,java,python";
    }
    public void setAllowedLanguages(String allowedLanguages) {
        this.allowedLanguages = allowedLanguages;
    }

    public boolean isLanguageAllowed(String lang) {
        if (lang == null) return false;
        String clean = lang.trim().toLowerCase();
        for (String item : getAllowedLanguages().split(",")) {
            if (item.trim().equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return classCode + " - " + name + (courseName != null ? " (" + courseName + ")" : "");
    }
}
