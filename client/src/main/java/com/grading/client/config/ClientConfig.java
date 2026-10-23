package com.grading.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * ClientConfig — Quản lý cấu hình kết nối của Client tới Server.
 *
 * Thứ tự ưu tiên nạp cấu hình:
 * 1. Java System Property (-Dserver.host, -Dserver.port)
 * 2. Biến môi trường hệ thống (SERVER_HOST, SERVER_PORT hoặc LTM_SERVER_HOST, LTM_SERVER_PORT)
 * 3. File config.properties hoặc client.properties trong thư mục chạy app
 * 4. Giá trị mặc định (localhost:9999)
 */
public class ClientConfig {

    private static String host = "localhost";
    private static int port = 9999;

    static {
        loadConfig();
    }

    public static synchronized void loadConfig() {
        Properties props = new Properties();

        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            configFile = new File("client.properties");
        }
        if (!configFile.exists()) {
            configFile = new File("client/config.properties");
        }
        if (!configFile.exists()) {
            configFile = new File("client/client.properties");
        }

        if (configFile.exists() && configFile.isFile()) {
            try (InputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (Exception ignored) {
            }
        }

        // 2. Xác định Host
        String sysHost = System.getProperty("server.host");
        String envHost = System.getenv("SERVER_HOST");
        if (envHost == null) envHost = System.getenv("LTM_SERVER_HOST");
        String propHost = props.getProperty("server.host");

        if (sysHost != null && !sysHost.isBlank()) {
            host = sysHost.trim();
        } else if (envHost != null && !envHost.isBlank()) {
            host = envHost.trim();
        } else if (propHost != null && !propHost.isBlank()) {
            host = propHost.trim();
        }

        // 3. Xác định Port
        String sysPort = System.getProperty("server.port");
        String envPort = System.getenv("SERVER_PORT");
        if (envPort == null) envPort = System.getenv("LTM_SERVER_PORT");
        String propPort = props.getProperty("server.port");

        String selectedPort = null;
        if (sysPort != null && !sysPort.isBlank()) selectedPort = sysPort;
        else if (envPort != null && !envPort.isBlank()) selectedPort = envPort;
        else if (propPort != null && !propPort.isBlank()) selectedPort = propPort;

        if (selectedPort != null) {
            try {
                port = Integer.parseInt(selectedPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static String getHost() {
        return host;
    }

    public static int getPort() {
        return port;
    }
}
