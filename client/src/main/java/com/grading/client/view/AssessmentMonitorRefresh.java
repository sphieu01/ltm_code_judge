package com.grading.client.view;

import com.google.gson.JsonObject;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.function.Consumer;

/** Initial loading indicator followed by non-blocking monitor updates. FX thread only. */
final class AssessmentMonitorRefresh {
    private final List<TableView<?>> tables;
    private final SocketClient client;
    private final int assessmentId;
    private final Label summary;
    private final Consumer<JsonObject> applyData;
    private boolean initialLoadComplete;
    private boolean inFlight;
    private long generation;

    AssessmentMonitorRefresh(List<TableView<?>> tables, SocketClient client, int assessmentId,
                             Label summary, Consumer<JsonObject> applyData) {
        this.tables = tables;
        this.client = client;
        this.assessmentId = assessmentId;
        this.summary = summary;
        this.applyData = applyData;
    }

    void refresh() {
        if (inFlight) return;
        inFlight = true;
        long requestGeneration = ++generation;
        if (!initialLoadComplete) tables.forEach(table -> TableViewSupport.setLoading(table, true));
        if (client == null || !client.isConnected()) {
            fail("Chưa kết nối đến máy chủ.");
            finish();
            return;
        }
        try {
            client.getAssessmentMonitor(assessmentId, response -> Platform.runLater(() -> {
                if (generation != requestGeneration) return;
                try {
                    if (!valid(response)) {
                        fail(response != null && response.getMessage() != null
                                ? response.getMessage() : "Không thể tải dữ liệu theo dõi.");
                        return;
                    }
                    applyData.accept(response.getData().getAsJsonObject());
                    initialLoadComplete = true;
                } catch (RuntimeException error) {
                    fail("Không thể đọc dữ liệu trả về.");
                } finally {
                    finish();
                }
            }));
        } catch (RuntimeException error) {
            fail("Không thể gửi yêu cầu tải dữ liệu.");
            finish();
        }
    }

    void stop() {
        // A delayed response must not modify a screen that the teacher has left.
        generation++;
        finish();
    }

    private boolean valid(Response response) {
        return response != null && "SUCCESS".equalsIgnoreCase(response.getStatus())
                && response.getData() != null && response.getData().isJsonObject();
    }

    private void fail(String message) {
        summary.setText(initialLoadComplete
                ? "Chưa cập nhật được dữ liệu; đang hiển thị dữ liệu trước đó. " + message : message);
        if (!initialLoadComplete) tables.forEach(table -> TableViewSupport.showLoadError(table, message));
    }

    private void finish() {
        inFlight = false;
        tables.forEach(table -> TableViewSupport.setLoading(table, false));
    }
}
