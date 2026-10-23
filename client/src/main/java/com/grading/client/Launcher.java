package com.grading.client;

/**
 * Launcher — Lớp khởi động phụ không kế thừa javafx.application.Application.
 *
 * Giúp JavaFX chạy trực tiếp từ Fat JAR (java -jar grading-client.jar)
 * mà không bị lỗi 'missing JavaFX runtime components'.
 */
public class Launcher {
    public static void main(String[] args) {
        ClientApp.main(args);
    }
}
