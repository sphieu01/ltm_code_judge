package com.grading.client.network;

import com.grading.client.model.User;

/**
 * Singleton lưu trữ trạng thái phiên làm việc.
 * Được chia sẻ giữa các Controller.
 *
 * Chứa:
 * - SocketClient instance (kết nối TCP)
 * - User đang đăng nhập
 */
public class SessionManager {

    private static SessionManager instance;

    private SocketClient socketClient;
    private User currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public SocketClient getSocketClient() {
        return socketClient;
    }

    public void setSocketClient(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * Xóa session khi logout.
     */
    public void clear() {
        if (socketClient != null) {
            socketClient.disconnect();
        }
        currentUser = null;
        socketClient = null;
    }

    public String getToken() {
        return socketClient != null ? socketClient.getAuthToken() : null;
    }

    public boolean isLoggedIn() {
        return currentUser != null && socketClient != null && socketClient.isConnected();
    }
}
