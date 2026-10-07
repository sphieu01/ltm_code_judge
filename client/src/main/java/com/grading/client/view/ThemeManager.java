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
    private Theme currentTheme = Theme.LIGHT; // MẶC ĐỊNH LÀ THEME SÁNG!
    private Scene registeredScene;
    private final List<Consumer<Theme>> themeListeners = new ArrayList<>();

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
        return (currentTheme == Theme.LIGHT) ? "🌙 Tối" : "☀ Sáng";
    }
}
