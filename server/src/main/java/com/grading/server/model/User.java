package com.grading.server.model;

/**
 * Model đại diện cho một user trong hệ thống.
 * Map 1-1 với bảng `users` trong PostgreSQL.
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;    // BCrypt hash — không bao giờ trả về client
    private String fullName;
    private String role;            // "STUDENT" | "TEACHER"
    private Integer classId;        // ID lớp tín chỉ (dành cho STUDENT)
    private String className;       // Tên lớp tín chỉ
    private String createdAt;

    public User() {}

    public User(String username, String passwordHash, String fullName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }


    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /** Trả về DTO an toàn (không có passwordHash) để serialize vào JSON */
    public UserDTO toDTO() {
        return new UserDTO(id, username, fullName, role, classId, className, createdAt);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "'}";
    }

    // ─── Inner DTO class ──────────────────────────────────────────────────────
    /** Data Transfer Object — dùng khi trả về cho client (không chứa password) */
    public static class UserDTO {
        public final int id;
        public final String username;
        public final String fullName;
        public final String role;
        public final Integer classId;
        public final String className;
        public final String createdAt;

        public UserDTO(int id, String username, String fullName, String role, Integer classId, String className, String createdAt) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.classId = classId;
            this.className = className;
            this.createdAt = createdAt;
        }
    }

}
