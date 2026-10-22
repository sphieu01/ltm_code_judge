package com.grading.client.model;

/**
 * Model đại diện cho người dùng đã đăng nhập.
 */
public class User {
    private int id;
    private String username;
    private String fullName;
    private String role; // STUDENT, TEACHER
    private Integer classId;
    private String className;

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

    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public boolean isTeacher() {
        return "TEACHER".equalsIgnoreCase(role);
    }

    /**
     * Lấy 1-2 chữ cái viết tắt đại diện cho người dùng để hiển thị trên Avatar
     */
    public String getAvatarText() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length >= 2) {
                return (parts[parts.length - 2].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            }
        }
        if (username != null && !username.trim().isEmpty()) {
            return username.substring(0, Math.min(2, username.length())).toUpperCase();
        }
        return "U";
    }

    public static class UserDTO {
        private int id;
        private String username;
        private String fullName;
        private String role;
        private Integer classId;
        private String className;
        private String createdAt;

        public UserDTO() {}

        public UserDTO(int id, String username, String fullName, String role, Integer classId, String className, String createdAt) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.classId = classId;
            this.className = className;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

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
    }
}

