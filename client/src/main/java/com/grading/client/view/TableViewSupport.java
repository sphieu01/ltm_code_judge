package com.grading.client.view;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.Bindings;
import javafx.scene.control.TableView;

/** Tiện ích giữ chiều cao bảng vừa khít dữ liệu và chỉ cuộn khi vượt số dòng cho phép. */
public final class TableViewSupport {
    private static final double HEADER_HEIGHT = 35;
    private static final double ROW_HEIGHT = 46;
    private static final double BORDER_ALLOWANCE = 2;

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
