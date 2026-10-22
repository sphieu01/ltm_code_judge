package com.grading.client.view;

import javafx.scene.Scene;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ThemeManager — Quản lý chế độ hiển thị Sáng / Tối (Light / Dark Theme).
 * Mặc định khởi động là THEME SÁNG (Light Mode).
 */
public class ThemeManager {

    public enum Theme {
        LIGHT("Sáng"),
        DARK("Tối");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static ThemeManager instance;
    private Theme currentTheme = Theme.LIGHT; // MẶC ĐỊNH LÀ THEME SÁNG (MÀU TRẮNG)
    private Scene registeredScene;
    private final List<Consumer<Theme>> themeListeners = new ArrayList<>();
    private double zoomLevel = 1.0;
    private static final double MIN_ZOOM = 0.80;
    private static final double MAX_ZOOM = 1.40;
    private static final double ZOOM_STEP = 0.10;

    private ThemeManager() {}

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public boolean isDark() {
        return currentTheme == Theme.DARK;
    }

    public void toggleTheme() {
        setTheme(currentTheme == Theme.LIGHT ? Theme.DARK : Theme.LIGHT);
    }

    public void setTheme(Theme theme) {
        if (theme != null && this.currentTheme != theme) {
            this.currentTheme = theme;
            applyTheme(registeredScene);
            notifyListeners();
        }
    }

    public void registerScene(Scene scene) {
        this.registeredScene = scene;
        applyTheme(scene);
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        String cssPath = (currentTheme == Theme.LIGHT) ? "/css/light.css" : "/css/dark.css";
        URL res = getClass().getResource(cssPath);
        if (res != null) {
            scene.getStylesheets().setAll(res.toExternalForm());
        }
        applyZoom(scene);
    }

    public void zoomIn() {
        setZoomLevel(zoomLevel + ZOOM_STEP);
    }

    public void zoomOut() {
        setZoomLevel(zoomLevel - ZOOM_STEP);
    }

    public void resetZoom() {
        setZoomLevel(1.0);
    }

    public double getZoomLevel() {
        return zoomLevel;
    }

    public int getZoomPercent() {
        return (int) Math.round(zoomLevel * 100);
    }

    public boolean canZoomIn() {
        return zoomLevel < MAX_ZOOM - 0.001;
    }

    public boolean canZoomOut() {
        return zoomLevel > MIN_ZOOM + 0.001;
    }

    private void setZoomLevel(double value) {
        zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                Math.round(value * 10.0) / 10.0));
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window.getScene() != null) applyZoom(window.getScene());
        }
    }

    public void applyCurrentZoom(javafx.scene.Parent root) {
        if (root == null) return;
        applyZoomStyle(root);
    }

    private void applyZoom(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        applyZoomStyle(scene.getRoot());
    }

    private void applyZoomStyle(javafx.scene.Parent root) {
        String current = root.getStyle();
        String withoutFontSize = current == null ? "" : current.replaceAll("-fx-font-size\\s*:[^;]+;?", "").trim();
        root.setStyle(withoutFontSize + (withoutFontSize.isEmpty() ? "" : ";")
                + "-fx-font-size: " + String.format(java.util.Locale.ROOT, "%.1f", 13.0 * zoomLevel) + "px;");
        root.applyCss();
        root.requestLayout();
    }

    public void addThemeListener(Consumer<Theme> listener) {
        if (listener != null) {
            themeListeners.add(listener);
            listener.accept(currentTheme);
        }
    }

    public void removeThemeListener(Consumer<Theme> listener) {
        themeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<Theme> listener : themeListeners) {
            listener.accept(currentTheme);
        }
    }

    public String getToggleIconText() {
        return (currentTheme == Theme.LIGHT) ? "🌙" : "☀";
    }
}
