package com.grading.client.model;

/**
 * Model đại diện cho một môn học (Course / Subject) phía Client.
 */
public class Course {
    private int id;
    private String code;        // Mã môn: "CS101", "INT2204"
    private String name;        // Tên môn: "Lập trình C++", "Lập trình Java", "Lập trình Python"
    private String language;    // "cpp" | "java" | "python" | "c"
    private String description;
    private boolean active = true;
    private String createdAt;

    public Course() {}

    public Course(int id, String code, String name, String language, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.language = language;
        this.description = description;
        this.active = true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return name + " (" + code + ")";
    }
}
