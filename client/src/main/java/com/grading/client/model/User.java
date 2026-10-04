package com.grading.client.model;

/**
 * Model đại diện cho người dùng đã đăng nhập.
 */
public class User {
    private int id;
    private String username;
    private String fullName;
    private String role; // STUDENT, TEACHER

    public User() {}

    public User(int id, String username, String fullName, String role) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isTeacher() {
        return "TEACHER".equals(role);
    }
}
