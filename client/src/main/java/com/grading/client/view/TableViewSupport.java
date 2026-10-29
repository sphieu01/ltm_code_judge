package com.grading.client.view;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.Bindings;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.application.Platform;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import java.util.List;
import java.util.function.Consumer;

/** Shared table sizing and asynchronous loading, empty and failure states. */
public final class TableViewSupport {
    private static final double HEADER_HEIGHT = 35;
    private static final double ROW_HEIGHT = 46;
    private static final double BORDER_ALLOWANCE = 2;
    private static final String LOAD_STATE = TableViewSupport.class.getName() + ".loadState";

    private static final class LoadState {
        long generation;
        boolean loading;
        javafx.scene.Node emptyPlaceholder;
        final StackPane overlay;

        LoadState(TableView<?> table) {
            emptyPlaceholder = table.getPlaceholder() != null ? table.getPlaceholder() : new Label("Không có dữ liệu.");
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(22, 22);
            spinner.setMaxSize(22, 22);
            HBox message = new HBox(10, spinner, new Label("Đang tải dữ liệu…"));
            message.setAlignment(Pos.CENTER);
            overlay = new StackPane(message);
            overlay.getStyleClass().add("table-loading-overlay");
            overlay.setStyle("-fx-background-color: -fx-control-inner-background;");
            overlay.setManaged(false);
            overlay.setVisible(false);
            table.placeholderProperty().addListener((obs, oldValue, newValue) -> {
                if (!loading) emptyPlaceholder = newValue;
            });
            installSkin(table, overlay);
        }
    }

    private static <T> void installSkin(TableView<T> table, StackPane overlay) {
        table.setSkin(new TableViewSkin<T>(table) {
            { getChildren().add(overlay); }
            @Override protected void layoutChildren(double x, double y, double w, double h) {
                super.layoutChildren(x, y, w, h);
                double header = getTableHeaderRow().getHeight();
                overlay.resizeRelocate(x, y + header, w, Math.max(0, h - header));
                overlay.toFront();
            }
        });
    }

    private static LoadState state(TableView<?> table) {
        LoadState state = (LoadState) table.getProperties().get(LOAD_STATE);
        if (state == null) {
            state = new LoadState(table);
            table.getProperties().put(LOAD_STATE, state);
        }
        return state;
    }

    public static void setLoading(TableView<?> table, boolean loading) {
        LoadState state = state(table);
        if (loading && !state.loading) table.setPlaceholder(state.emptyPlaceholder);
        state.loading = loading;
        state.overlay.setVisible(loading);
        table.requestLayout();
    }

    public static boolean isLoading(TableView<?> table) {
        return state(table).loading;
    }

    public static void showLoadError(TableView<?> table, String message) {
        LoadState state = state(table);
        javafx.scene.Node emptyPlaceholder = state.emptyPlaceholder;
        setLoading(table, false);
        table.setPlaceholder(new Label(message));
        state.emptyPlaceholder = emptyPlaceholder;
    }

    public static void load(TableView<?> table, SocketClient client,
                            Consumer<Consumer<Response>> request, Consumer<Response> onSuccess) {
        load(List.of(table), client, request, onSuccess);
    }

    /** Owns the FX-thread transition and ignores responses superseded by a newer request. */
    public static void load(List<TableView<?>> tables, SocketClient client,
                            Consumer<Consumer<Response>> request, Consumer<Response> onSuccess) {
        long[] generations = new long[tables.size()];
        for (int i = 0; i < tables.size(); i++) {
            TableView<?> table = tables.get(i);
            LoadState state = state(table);
            generations[i] = ++state.generation;
            table.setPlaceholder(state.emptyPlaceholder);
            setLoading(table, true);
        }
        if (client == null || !client.isConnected()) {
            tables.forEach(table -> showLoadError(table, "Chưa kết nối đến máy chủ."));
            return;
        }
        try {
            request.accept(response -> Platform.runLater(() -> {
                for (int i = 0; i < tables.size(); i++) {
                    if (state(tables.get(i)).generation != generations[i]) return;
                }
                try {
                    if (response == null || !"SUCCESS".equalsIgnoreCase(response.getStatus())
                            || response.getData() == null || response.getData().isJsonNull()) {
                        tables.forEach(table -> showLoadError(table, "Không thể tải dữ liệu. Vui lòng thử lại."));
                        return;
                    }
                    onSuccess.accept(response);
                } catch (RuntimeException error) {
                    tables.forEach(table -> showLoadError(table, "Không thể đọc dữ liệu trả về."));
                } finally {
                    for (int i = 0; i < tables.size(); i++) {
                        if (state(tables.get(i)).generation == generations[i]) setLoading(tables.get(i), false);
                    }
                }
            }));
        } catch (RuntimeException error) {
            tables.forEach(table -> showLoadError(table, "Không thể gửi yêu cầu tải dữ liệu."));
        }
    }

    private TableViewSupport() {
    }

    public static void fitHeightToRows(TableView<?> table, int maxVisibleRows) {
        int safeMaxRows = Math.max(1, maxVisibleRows);
        DoubleBinding fittedHeight = Bindings.createDoubleBinding(() -> {
            int itemCount = table.getItems() == null ? 0 : table.getItems().size();
            int visibleRows = Math.max(1, Math.min(itemCount, safeMaxRows));
            return HEADER_HEIGHT + visibleRows * ROW_HEIGHT + BORDER_ALLOWANCE;
        }, table.getItems());

        table.setFixedCellSize(ROW_HEIGHT);
        table.minHeightProperty().bind(fittedHeight);
        table.prefHeightProperty().bind(fittedHeight);
        table.maxHeightProperty().bind(fittedHeight);
    }
}
