package com.grading.client.view;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.Bindings;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.application.Platform;
import com.grading.client.network.SocketClient;
import com.grading.client.protocol.Response;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
        // Dispose the old skin before constructing its replacement. Otherwise JavaFX
        // 17 can leave table headers referencing the disposed skin, failing the next
        // layout pass and preventing the entire monitor screen from rendering.
        table.setSkin(null);
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

    /** Refresh live rows while retaining sorting, selection, focus and the visible row/offset. */
    public static <T, K> void updateItems(TableView<T> table, List<T> rows, Function<T, K> key) {
        List<T> next = new ArrayList<>(rows);
        if (table.getComparator() != null) next.sort(table.getComparator());
        var items = table.getItems();
        if (items.equals(next)) return; // Unchanged polls must not trigger a table rebuild.

        Map<K, T> previous = new HashMap<>();
        for (T row : items) previous.put(key.apply(row), row);
        next.replaceAll(row -> Objects.equals(previous.get(key.apply(row)), row)
                ? previous.get(key.apply(row)) : row);

        T selected = table.getSelectionModel().getSelectedItem();
        T focused = table.getFocusModel().getFocusedItem();
        VirtualFlow<?> flow = table.lookup(".virtual-flow") instanceof VirtualFlow<?> found ? found : null;
        var firstCell = flow == null ? null : flow.getFirstVisibleCell();
        int firstIndex = firstCell == null ? -1 : firstCell.getIndex();
        K anchor = firstIndex >= 0 && firstIndex < items.size() ? key.apply(items.get(firstIndex)) : null;
        double offset = firstCell == null ? 0 : firstCell.getLayoutY();
        ScrollBar horizontal = table.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.HORIZONTAL).findFirst().orElse(null);
        double horizontalPosition = horizontal == null ? 0 : horizontal.getValue();

        items.setAll(next);
        table.getSelectionModel().clearSelection();
        if (selected != null) table.getSelectionModel().select(indexOf(next, key.apply(selected), key));
        table.getFocusModel().focus(focused == null ? -1 : indexOf(next, key.apply(focused), key));

        if (flow != null && anchor != null) {
            // Anchor by identity so rows arriving above the viewport do not move the
            // content being read. Capture at response time, after any user scrolling.
            int index = indexOf(next, anchor, key);
            if (index < 0) index = Math.min(firstIndex, next.size() - 1);
            table.layout();
            if (index >= 0) {
                flow.scrollToTop(index);
                flow.layout();
                flow.scrollPixels(-offset);
                flow.layout();
            }
        }
        if (horizontal != null) horizontal.setValue(horizontalPosition);
    }

    private static <T, K> int indexOf(List<T> rows, K value, Function<T, K> key) {
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(value, key.apply(rows.get(i)))) return i;
        }
        return -1;
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
