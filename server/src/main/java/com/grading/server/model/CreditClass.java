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

    public String getEffectiveLanguage() {
        if (courseLanguage != null && !courseLanguage.isBlank()) {
            return courseLanguage.trim().toLowerCase();
        }
        if (allowedLanguages != null && !allowedLanguages.isBlank() && !allowedLanguages.contains(",")) {
            return allowedLanguages.trim().toLowerCase();
        }
        String combined = ((name != null ? name : "") + " " + (courseName != null ? courseName : "")).toLowerCase();
        if (combined.contains("oop") || combined.contains("java") || combined.contains("hướng đối tượng")) {
            return "java";
        }
        if (combined.contains("python") || combined.contains("py")) {
            return "python";
        }
        if (combined.contains("c++") || combined.contains("cpp")) {
            return "cpp";
        }
        if (combined.contains("ngôn ngữ c") || combined.contains("lập trình c")) {
            return "c";
        }
        return null;
    }

    public String getEffectiveAllowedLanguages() {
        if (allowedLanguages != null && !allowedLanguages.isBlank() && !isDefaultAllLanguages(allowedLanguages)) {
            return allowedLanguages.trim().toLowerCase();
        }
        String eff = getEffectiveLanguage();
        if (eff != null) {
            if ("cpp".equalsIgnoreCase(eff)) {
                return "cpp,c";
            }
            return eff;
        }
        return getAllowedLanguages();
    }

    private boolean isDefaultAllLanguages(String langs) {
        String s = langs.trim().toLowerCase();
        return s.equals("c,cpp,java,python")
                || (s.contains("c") && s.contains("cpp") && s.contains("java") && s.contains("python"));
    }

    public String getAllowedLanguages() {
        return allowedLanguages != null && !allowedLanguages.isBlank() ? allowedLanguages : "c,cpp,java,python";
    }
    public void setAllowedLanguages(String allowedLanguages) {
        this.allowedLanguages = allowedLanguages;
    }

    public boolean isLanguageAllowed(String lang) {
        if (lang == null) return false;
        String clean = lang.trim().toLowerCase();
        for (String item : getEffectiveAllowedLanguages().split(",")) {
            String it = item.trim().toLowerCase();
            if (it.equalsIgnoreCase(clean)
                    || (it.equals("cpp") && (clean.equals("c++") || clean.equals("cpp")))
                    || (it.equals("c++") && (clean.equals("cpp") || clean.equals("c++")))
                    || (it.equals("python") && (clean.equals("python") || clean.startsWith("python") || clean.equals("py")))
                    || (it.equals("c") && clean.equals("c"))
                    || (it.equals("java") && clean.equals("java"))) {
                return true;
            }
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
