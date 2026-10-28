package com.grading.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.OopSpec;
import com.grading.client.model.Problem;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TeacherProblemDialog — Giao diện tạo và chỉnh sửa bài tập dành cho Giáo viên.
 *
 * Kiến trúc UI 3 Tab hiện đại, gọn gàng và trực quan:
 * 1. 📋 Thông tin chung: Tiêu đề, phân loại, thông số, mô tả bài toán, định dạng I/O.
 * 2. 🏛️ Cấu trúc OOP: Thiết kế đặc tả lớp (OOP Specification) tách biệt theo từng Tab lớp,
 *    bảng thuộc tính (có toggle ràng buộc nâng cao), danh sách constructor và method chuẩn học thuật.
 * 3. 🧪 Bộ Test Cases: Các test case chấm qua Main.java.
 */
public class TeacherProblemDialog extends Dialog<Problem> {

    // ======================== TEST CASE UI COMPONENT ========================

    public static class TestCaseItem {
        public final CheckBox sampleCheck;
        public final TextArea inputArea;
        public final TextArea outputArea;
        public final VBox rootCard;

        public TestCaseItem(boolean isSample, String inputDefault, String outputDefault, Runnable onDelete) {
            rootCard = new VBox(8);
            rootCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            HBox cardHeader = new HBox(8);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            sampleCheck = new CheckBox("Test case ví dụ (Hiển thị công khai cho sinh viên)");
            sampleCheck.setSelected(isSample);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button delBtn = new Button("🗑 Xóa");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 11px; -fx-cursor: hand;");
            delBtn.setOnAction(e -> onDelete.run());

            cardHeader.getChildren().addAll(sampleCheck, sp, delBtn);

            HBox ioRow = new HBox(12);
            VBox inBox = new VBox(4);
            Label inLabel = new Label("Đầu vào (Input - stdin):");
            inLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
            inputArea = new TextArea(inputDefault != null ? inputDefault : "");
            inputArea.setPromptText("Dữ liệu đầu vào...");
            inputArea.setPrefRowCount(3);
            HBox.setHgrow(inBox, Priority.ALWAYS);
            inBox.getChildren().addAll(inLabel, inputArea);

            VBox outBox = new VBox(4);
            Label outLabel = new Label("Đầu ra kỳ vọng (Expected Output - stdout):");
            outLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
            outputArea = new TextArea(outputDefault != null ? outputDefault : "");
            outputArea.setPromptText("Kết quả mong muốn...");
            outputArea.setPrefRowCount(3);
            HBox.setHgrow(outBox, Priority.ALWAYS);
            outBox.getChildren().addAll(outLabel, outputArea);

            ioRow.getChildren().addAll(inBox, outBox);
            rootCard.getChildren().addAll(cardHeader, ioRow);
        }
    }

    // ======================== OOP DESIGNER UI COMPONENTS ========================

    /**
     * Giao diện dòng thuộc tính: Thiết kế gọn gàng dạng bảng,
     * dòng ràng buộc nâng cao (Default, Min, Max, MaxLen) ẩn mặc định và mở ra khi cần.
     */
    private static class FieldItemUI {
        final TextField nameField = new TextField();
        final ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "String", "int", "double", "boolean", "long", "float", "char", "Date", "List"
        ));
        final ComboBox<String> modifierCombo = new ComboBox<>(FXCollections.observableArrayList(
                "private", "protected", "public"
        ));
        final CheckBox mutableCheck = new CheckBox("Setter");
        final CheckBox optionalCheck = new CheckBox("Optional");
        final Button constraintToggleBtn = new Button("⚙ Ràng buộc");
        final Button delBtn = new Button("✕");

        final TextField defValField = new TextField();
        final TextField minField = new TextField();
        final TextField maxField = new TextField();
        final TextField maxLenField = new TextField();

        final VBox root = new VBox(4);
        final HBox line1 = new HBox(8);
        final HBox line2 = new HBox(8);

        FieldItemUI(String name, String type, String modifier, Boolean mutable, Boolean optional,
                    String defaultValue, Double min, Double max, Integer maxLength, Runnable onDelete) {
            nameField.setPromptText("Tên thuộc tính");
            nameField.setText(name != null ? name : "");
            nameField.setPrefWidth(140);

            typeCombo.setEditable(true);
            typeCombo.setValue(type != null && !type.isBlank() ? type : "String");
            typeCombo.setPrefWidth(100);

            modifierCombo.setValue(modifier != null && !modifier.isBlank() ? modifier : "private");
            modifierCombo.setPrefWidth(95);

            mutableCheck.setSelected(mutable == null || mutable);
            optionalCheck.setSelected(optional != null && optional);

            defValField.setPromptText("Mặc định (VD: 0.0)");
            defValField.setText(defaultValue != null ? defaultValue : "");
            defValField.setPrefWidth(120);

            minField.setPromptText("Min");
            minField.setText(min != null ? String.valueOf(min) : "");
            minField.setPrefWidth(65);

            maxField.setPromptText("Max");
            maxField.setText(max != null ? String.valueOf(max) : "");
            maxField.setPrefWidth(65);

            maxLenField.setPromptText("MaxLen");
            maxLenField.setText(maxLength != null ? String.valueOf(maxLength) : "");
            maxLenField.setPrefWidth(70);

            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 3 8 3 8; -fx-cursor: hand;");
            delBtn.setOnAction(e -> onDelete.run());

            boolean hasConstraints = (defaultValue != null && !defaultValue.isBlank())
                    || min != null || max != null || maxLength != null;

            constraintToggleBtn.getStyleClass().addAll("button", "btn-secondary");
            updateConstraintButtonState(hasConstraints);

            // Toggle hiển thị bảng ràng buộc nâng cao
            line2.setVisible(hasConstraints);
            line2.setManaged(hasConstraints);

            constraintToggleBtn.setOnAction(e -> {
                boolean next = !line2.isVisible();
                line2.setVisible(next);
                line2.setManaged(next);
                if (next) {
                    constraintToggleBtn.setText("▼ Ràng buộc");
                    constraintToggleBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-border-color: -accent; -fx-text-fill: -accent; -fx-cursor: hand;");
                } else {
                    updateConstraintButtonState(hasAnyConstraint());
                }
            });

            Runnable refreshBtn = () -> updateConstraintButtonState(hasAnyConstraint());
            defValField.textProperty().addListener((obs, o, n) -> refreshBtn.run());
            minField.textProperty().addListener((obs, o, n) -> refreshBtn.run());
            maxField.textProperty().addListener((obs, o, n) -> refreshBtn.run());
            maxLenField.textProperty().addListener((obs, o, n) -> refreshBtn.run());

            line1.setAlignment(Pos.CENTER_LEFT);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            line1.getChildren().addAll(
                    nameField,
                    typeCombo,
                    modifierCombo,
                    mutableCheck,
                    optionalCheck,
                    sp,
                    constraintToggleBtn,
                    delBtn
            );

            line2.setAlignment(Pos.CENTER_LEFT);
            line2.setStyle("-fx-background-color: -bg-main; -fx-padding: 6 10 6 10; -fx-background-radius: 4px; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px;");

            Label cLabel = new Label("↳ Ràng buộc nâng cao:");
            cLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");

            Label dL = new Label("Default:");
            dL.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");
            Label miL = new Label("Min:");
            miL.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");
            Label maL = new Label("Max:");
            maL.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");
            Label lenL = new Label("MaxLen:");
            lenL.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");

            line2.getChildren().addAll(
                    cLabel,
                    dL, defValField,
                    miL, minField,
                    maL, maxField,
                    lenL, maxLenField
            );

            root.setStyle("-fx-background-color: -bg-surface; -fx-padding: 6 8 6 8; -fx-background-radius: 4px; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px;");
            root.getChildren().addAll(line1, line2);
        }

        private boolean hasAnyConstraint() {
            return !defValField.getText().trim().isEmpty()
                    || !minField.getText().trim().isEmpty()
                    || !maxField.getText().trim().isEmpty()
                    || !maxLenField.getText().trim().isEmpty();
        }

        private void updateConstraintButtonState(boolean has) {
            if (has) {
                constraintToggleBtn.setText("⚙ Ràng buộc (✓)");
                constraintToggleBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-text-fill: -accent; -fx-border-color: -accent; -fx-cursor: hand;");
            } else {
                constraintToggleBtn.setText("⚙ Ràng buộc");
                constraintToggleBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-text-fill: -text-secondary; -fx-cursor: hand;");
            }
        }
    }

    private static class ConstructorItemUI {
        final ComboBox<String> modifierCombo = new ComboBox<>(FXCollections.observableArrayList(
                "public", "protected", "private"
        ));
        final TextField paramsField = new TextField();
        final HBox row = new HBox(8);

        ConstructorItemUI(String modifier, List<String> params, Runnable onDelete) {
            modifierCombo.setValue(modifier != null && !modifier.isBlank() ? modifier : "public");
            modifierCombo.setPrefWidth(100);

            paramsField.setPromptText("Kiểu tham số (VD: String, double hoặc để trống nếu là Default Constructor)");
            if (params != null && !params.isEmpty()) {
                paramsField.setText(String.join(", ", params));
            }
            HBox.setHgrow(paramsField, Priority.ALWAYS);

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 3 8 3 8; -fx-cursor: hand;");
            delBtn.setOnAction(e -> onDelete.run());

            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: -bg-surface; -fx-padding: 6 8 6 8; -fx-background-radius: 4px; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px;");
            row.getChildren().addAll(modifierCombo, paramsField, delBtn);
        }
    }

    private static class MethodItemUI {
        final TextField nameField = new TextField();
        final TextField returnTypeField = new TextField("void");
        final ComboBox<String> modifierCombo = new ComboBox<>(FXCollections.observableArrayList(
                "public", "protected", "abstract", "private"
        ));
        final TextField paramsField = new TextField();
        final HBox row = new HBox(8);

        MethodItemUI(String name, String returnType, String modifier, List<String> params, Runnable onDelete) {
            nameField.setPromptText("Tên method");
            nameField.setText(name != null ? name : "");
            nameField.setPrefWidth(140);

            returnTypeField.setPromptText("Kiểu trả về");
            returnTypeField.setText(returnType != null && !returnType.isBlank() ? returnType : "void");
            returnTypeField.setPrefWidth(100);

            modifierCombo.setValue(modifier != null && !modifier.isBlank() ? modifier : "public");
            modifierCombo.setPrefWidth(95);

            paramsField.setPromptText("Tham số (VD: double, int hoặc để trống)");
            if (params != null && !params.isEmpty()) {
                paramsField.setText(String.join(", ", params));
            }
            HBox.setHgrow(paramsField, Priority.ALWAYS);

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 3 8 3 8; -fx-cursor: hand;");
            delBtn.setOnAction(e -> onDelete.run());

            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: -bg-surface; -fx-padding: 6 8 6 8; -fx-background-radius: 4px; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px;");
            row.getChildren().addAll(nameField, returnTypeField, modifierCombo, paramsField, delBtn);
        }
    }

    /**
     * Đại diện cho giao diện thiết kế 1 Lớp OOP.
     * Được trình bày dưới dạng 1 Tab riêng biệt trong TabPane các lớp.
     */
    private static class ClassCardUI {
        final TextField nameField = new TextField();
        final ComboBox<String> classTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "CLASS", "ABSTRACT_CLASS", "INTERFACE"
        ));
        final TextField superClassField = new TextField();
        final TextField interfacesField = new TextField();

        final VBox fieldsContainer = new VBox(6);
        final List<FieldItemUI> fieldItems = new ArrayList<>();
        final Label fieldsCountBadge = new Label("0");
        final Label fieldsEmptyHint = new Label("Chưa có thuộc tính nào. Bấm '➕ Thêm Field' để thêm.");

        final VBox constructorsContainer = new VBox(6);
        final List<ConstructorItemUI> constructorItems = new ArrayList<>();
        final Label ctorCountBadge = new Label("0");
        final Label ctorEmptyHint = new Label("Chưa có constructor nào. Bấm '➕ Thêm Constructor' để thêm.");

        final VBox methodsContainer = new VBox(6);
        final List<MethodItemUI> methodItems = new ArrayList<>();
        final Label methodCountBadge = new Label("0");
        final Label methodEmptyHint = new Label("Chưa có phương thức nào. Bấm '➕ Thêm Method' để thêm.");

        final VBox content = new VBox(14);
        final ScrollPane scrollPane = new ScrollPane(content);
        final Tab tab;

        ClassCardUI(String name, String classType, String superClass, List<String> interfaces, Runnable onDelete) {
            String initialName = name != null && !name.isBlank() ? name : "Lớp mới";
            tab = new Tab("🏛️ " + initialName);
            tab.setClosable(false);

            // Tự động đồng bộ tên trên tiêu đề Tab khi giáo viên nhập tên lớp
            nameField.textProperty().addListener((obs, oldVal, newVal) -> {
                String trimmed = newVal != null ? newVal.trim() : "";
                tab.setText(trimmed.isEmpty() ? "🏛️ Lớp mới" : "🏛️ " + trimmed);
            });

            scrollPane.getStyleClass().add("scroll-pane");
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            tab.setContent(scrollPane);

            content.setPadding(new Insets(12));
            content.setStyle("-fx-background-color: transparent;");

            // 1. Thẻ định nghĩa Lớp (Tên, Loại, Extends, Implements)
            VBox classDefCard = new VBox(10);
            classDefCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            HBox headerRow = new HBox(10);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            nameField.setPromptText("Tên Lớp (*, e.g. Student, Point)");
            nameField.setText(name != null ? name : "");
            nameField.setPrefWidth(160);

            classTypeCombo.setValue(classType != null ? classType : "CLASS");
            classTypeCombo.setPrefWidth(130);

            superClassField.setPromptText("Kế thừa (superClass, e.g. Person)");
            superClassField.setText(superClass != null ? superClass : "");
            superClassField.setPrefWidth(160);

            Region spH = new Region();
            HBox.setHgrow(spH, Priority.ALWAYS);

            Button delClassBtn = new Button("🗑 Xóa Lớp này");
            delClassBtn.getStyleClass().addAll("button", "btn-secondary");
            delClassBtn.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 11.5px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
            delClassBtn.setOnAction(e -> onDelete.run());

            headerRow.getChildren().addAll(
                    new Label("Tên Lớp (*):"), nameField,
                    new Label("Loại:"), classTypeCombo,
                    new Label("Kế thừa:"), superClassField,
                    spH, delClassBtn
            );

            HBox implRow = new HBox(10);
            implRow.setAlignment(Pos.CENTER_LEFT);
            interfacesField.setPromptText("Các Interface triển khai (phân cách bằng dấu phẩy, e.g. Comparable, Serializable)");
            if (interfaces != null && !interfaces.isEmpty()) {
                interfacesField.setText(String.join(", ", interfaces));
            }
            HBox.setHgrow(interfacesField, Priority.ALWAYS);
            implRow.getChildren().addAll(new Label("Triển khai (implements):"), interfacesField);

            classDefCard.getChildren().addAll(headerRow, implRow);

            // 2. Thẻ danh sách Thuộc tính (Fields)
            VBox fieldsCard = new VBox(8);
            fieldsCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            HBox fieldHeader = new HBox(8);
            fieldHeader.setAlignment(Pos.CENTER_LEFT);
            Label fieldTitle = new Label("🔹 Danh sách Thuộc tính (Fields - Khuyến nghị 'private' để kiểm tra tính đóng gói)");
            fieldTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            initBadge(fieldsCountBadge);

            Region spF = new Region();
            HBox.setHgrow(spF, Priority.ALWAYS);

            Button addFieldBtn = new Button("➕ Thêm Field");
            addFieldBtn.getStyleClass().addAll("button", "btn-primary");
            addFieldBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-cursor: hand;");
            addFieldBtn.setOnAction(e -> addField("", "String", "private"));

            fieldHeader.getChildren().addAll(fieldTitle, fieldsCountBadge, spF, addFieldBtn);

            HBox fieldColHeader = buildColumnHeader("TÊN THUỘC TÍNH", 140, "KIỂU", 100, "QUYỀN", 95, "ĐÓNG GÓI & KHỞI TẠO", "RÀNG BUỘC & THAO TÁC");

            fieldsEmptyHint.setStyle("-fx-text-fill: -text-secondary; -fx-font-style: italic; -fx-font-size: 11.5px; -fx-padding: 6 0 6 0;");
            fieldsContainer.getChildren().add(fieldsEmptyHint);

            fieldsCard.getChildren().addAll(fieldHeader, fieldColHeader, fieldsContainer);

            // 3. Thẻ danh sách Constructor
            VBox ctorCard = new VBox(8);
            ctorCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            HBox ctorHeader = new HBox(8);
            ctorHeader.setAlignment(Pos.CENTER_LEFT);
            Label ctorTitle = new Label("🔨 Danh sách Hàm khởi tạo (Constructors)");
            ctorTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            initBadge(ctorCountBadge);

            Region spC = new Region();
            HBox.setHgrow(spC, Priority.ALWAYS);

            Button autoGenBtn = new Button("⚡ Tự động sinh (Chuẩn học thuật)");
            autoGenBtn.getStyleClass().addAll("button", "btn-secondary");
            autoGenBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-text-fill: -accent; -fx-border-color: -accent; -fx-cursor: hand;");
            autoGenBtn.setTooltip(new Tooltip("Tự động sinh các Constructor theo công thức: Default ctor () + Compulsory ctor + từng Optional ctor"));
            autoGenBtn.setOnAction(e -> autoGenerateConstructors());

            Button addCtorBtn = new Button("➕ Thêm Constructor");
            addCtorBtn.getStyleClass().addAll("button", "btn-primary");
            addCtorBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-cursor: hand;");
            addCtorBtn.setOnAction(e -> addConstructor("public", new ArrayList<>()));

            ctorHeader.getChildren().addAll(ctorTitle, ctorCountBadge, spC, autoGenBtn, addCtorBtn);

            HBox ctorColHeader = buildColumnHeader("QUYỀN TRUY CẬP", 100, "DANH SÁCH THAM SỐ (phân cách bằng dấu phẩy, để trống nếu là Default Constructor)", -1, null, -1, null, "THAO TÁC");

            ctorEmptyHint.setStyle("-fx-text-fill: -text-secondary; -fx-font-style: italic; -fx-font-size: 11.5px; -fx-padding: 6 0 6 0;");
            constructorsContainer.getChildren().add(ctorEmptyHint);

            ctorCard.getChildren().addAll(ctorHeader, ctorColHeader, constructorsContainer);

            // 4. Thẻ danh sách Phương thức (Methods)
            VBox methodCard = new VBox(8);
            methodCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            HBox methodHeader = new HBox(8);
            methodHeader.setAlignment(Pos.CENTER_LEFT);
            Label methodTitle = new Label("⚡ Danh sách Phương thức (Methods)");
            methodTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            initBadge(methodCountBadge);

            Region spM = new Region();
            HBox.setHgrow(spM, Priority.ALWAYS);

            Button addMethodBtn = new Button("➕ Thêm Method");
            addMethodBtn.getStyleClass().addAll("button", "btn-primary");
            addMethodBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-cursor: hand;");
            addMethodBtn.setOnAction(e -> addMethod("", "void", "public", new ArrayList<>()));

            methodHeader.getChildren().addAll(methodTitle, methodCountBadge, spM, addMethodBtn);

            HBox methodColHeader = buildColumnHeader("TÊN METHOD", 140, "TRẢ VỀ", 100, "QUYỀN", 95, "THAM SỐ TRUYỀN VÀO", "THAO TÁC");

            methodEmptyHint.setStyle("-fx-text-fill: -text-secondary; -fx-font-style: italic; -fx-font-size: 11.5px; -fx-padding: 6 0 6 0;");
            methodsContainer.getChildren().add(methodEmptyHint);

            methodCard.getChildren().addAll(methodHeader, methodColHeader, methodsContainer);

            content.getChildren().addAll(classDefCard, fieldsCard, ctorCard, methodCard);
        }

        private static void initBadge(Label badge) {
            badge.setStyle("-fx-background-color: -bg-hover; -fx-text-fill: -accent; -fx-padding: 1 6 1 6; -fx-background-radius: 10px; -fx-font-size: 11px; -fx-font-weight: bold;");
        }

        private static HBox buildColumnHeader(String col1, int w1, String col2, int w2, String col3, int w3, String col4, String colRight) {
            HBox bar = new HBox(8);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setStyle("-fx-background-color: -bg-main; -fx-padding: 4 8 4 8; -fx-background-radius: 4px;");

            if (col1 != null) {
                Label l1 = new Label(col1);
                l1.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");
                if (w1 > 0) l1.setPrefWidth(w1);
                else HBox.setHgrow(l1, Priority.ALWAYS);
                bar.getChildren().add(l1);
            }
            if (col2 != null) {
                Label l2 = new Label(col2);
                l2.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");
                if (w2 > 0) l2.setPrefWidth(w2);
                else HBox.setHgrow(l2, Priority.ALWAYS);
                bar.getChildren().add(l2);
            }
            if (col3 != null) {
                Label l3 = new Label(col3);
                l3.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");
                if (w3 > 0) l3.setPrefWidth(w3);
                else HBox.setHgrow(l3, Priority.ALWAYS);
                bar.getChildren().add(l3);
            }
            if (col4 != null) {
                Label l4 = new Label(col4);
                l4.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");
                HBox.setHgrow(l4, Priority.ALWAYS);
                bar.getChildren().add(l4);
            }

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            bar.getChildren().add(sp);

            if (colRight != null) {
                Label lr = new Label(colRight);
                lr.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -text-secondary;");
                bar.getChildren().add(lr);
            }
            return bar;
        }

        private boolean isCustomConstructors = false;
        private boolean isAutoGenerating = false;

        public void setCustomConstructors(boolean custom) {
            this.isCustomConstructors = custom;
        }

        void autoGenerateConstructors() {
            isAutoGenerating = true;
            try {
                constructorItems.clear();
                constructorsContainer.getChildren().clear();

                List<String> compulsoryTypes = new ArrayList<>();
                List<String> optionalTypes = new ArrayList<>();

                for (FieldItemUI fi : fieldItems) {
                    String type = fi.typeCombo.getValue();
                    if (type == null || type.isBlank()) type = "Object";
                    else type = type.trim();

                    if (fi.optionalCheck.isSelected()) {
                        optionalTypes.add(type);
                    } else {
                        compulsoryTypes.add(type);
                    }
                }

                // 1. Default constructor ()
                addConstructorInternal("public", new ArrayList<>());

                // 2. Compulsory fields constructor (nếu có ít nhất 1 trường bắt buộc)
                if (!compulsoryTypes.isEmpty()) {
                    addConstructorInternal("public", new ArrayList<>(compulsoryTypes));
                }

                // 3. Mỗi trường optional: compulsory + optional đó
                for (String optType : optionalTypes) {
                    List<String> optParams = new ArrayList<>(compulsoryTypes);
                    optParams.add(optType);
                    addConstructorInternal("public", optParams);
                }

                ctorCountBadge.setText(String.valueOf(constructorItems.size()));
                isCustomConstructors = false;
            } finally {
                isAutoGenerating = false;
            }
        }

        void addField(String name, String type, String modifier) {
            addField(name, type, modifier, true, false, null, null, null, null);
        }

        void addField(String name, String type, String modifier, Boolean mutable, Boolean optional,
                      String defaultValue, Double min, Double max, Integer maxLength) {
            FieldItemUI[] holder = new FieldItemUI[1];
            holder[0] = new FieldItemUI(name, type, modifier, mutable, optional, defaultValue, min, max, maxLength, () -> {
                fieldItems.remove(holder[0]);
                fieldsContainer.getChildren().remove(holder[0].root);
                fieldsCountBadge.setText(String.valueOf(fieldItems.size()));
                if (fieldItems.isEmpty() && !fieldsContainer.getChildren().contains(fieldsEmptyHint)) {
                    fieldsContainer.getChildren().add(fieldsEmptyHint);
                }
                if (!isCustomConstructors) {
                    autoGenerateConstructors();
                }
            });

            // Tự động đồng bộ Constructors khi sửa kiểu dữ liệu hoặc checkbox Optional
            holder[0].typeCombo.valueProperty().addListener((obs, o, n) -> {
                if (!isCustomConstructors) autoGenerateConstructors();
            });
            holder[0].optionalCheck.selectedProperty().addListener((obs, o, n) -> {
                if (!isCustomConstructors) autoGenerateConstructors();
            });

            fieldsContainer.getChildren().remove(fieldsEmptyHint);
            fieldItems.add(holder[0]);
            fieldsContainer.getChildren().add(holder[0].root);
            fieldsCountBadge.setText(String.valueOf(fieldItems.size()));

            if (!isCustomConstructors) {
                autoGenerateConstructors();
            }
        }

        void addConstructor(String modifier, List<String> params) {
            if (!isAutoGenerating) {
                isCustomConstructors = true;
            }
            addConstructorInternal(modifier, params);
        }

        private void addConstructorInternal(String modifier, List<String> params) {
            ConstructorItemUI[] holder = new ConstructorItemUI[1];
            holder[0] = new ConstructorItemUI(modifier, params, () -> {
                isCustomConstructors = true;
                constructorItems.remove(holder[0]);
                constructorsContainer.getChildren().remove(holder[0].row);
                ctorCountBadge.setText(String.valueOf(constructorItems.size()));
                if (constructorItems.isEmpty() && !constructorsContainer.getChildren().contains(ctorEmptyHint)) {
                    constructorsContainer.getChildren().add(ctorEmptyHint);
                }
            });

            holder[0].paramsField.textProperty().addListener((obs, o, n) -> {
                if (!isAutoGenerating) isCustomConstructors = true;
            });
            holder[0].modifierCombo.valueProperty().addListener((obs, o, n) -> {
                if (!isAutoGenerating) isCustomConstructors = true;
            });

            constructorsContainer.getChildren().remove(ctorEmptyHint);
            constructorItems.add(holder[0]);
            constructorsContainer.getChildren().add(holder[0].row);
            ctorCountBadge.setText(String.valueOf(constructorItems.size()));
        }

        void addMethod(String name, String returnType, String modifier, List<String> params) {
            MethodItemUI[] holder = new MethodItemUI[1];
            holder[0] = new MethodItemUI(name, returnType, modifier, params, () -> {
                methodItems.remove(holder[0]);
                methodsContainer.getChildren().remove(holder[0].row);
                methodCountBadge.setText(String.valueOf(methodItems.size()));
                if (methodItems.isEmpty() && !methodsContainer.getChildren().contains(methodEmptyHint)) {
                    methodsContainer.getChildren().add(methodEmptyHint);
                }
            });
            methodsContainer.getChildren().remove(methodEmptyHint);
            methodItems.add(holder[0]);
            methodsContainer.getChildren().add(holder[0].row);
            methodCountBadge.setText(String.valueOf(methodItems.size()));
        }

        OopSpec.ClassSpec toSpec() {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return null;
            OopSpec.ClassSpec cs = new OopSpec.ClassSpec();
            cs.setName(name);
            cs.setClassType(classTypeCombo.getValue());
            String sc = superClassField.getText().trim();
            cs.setSuperClass(sc.isEmpty() ? null : sc);

            String ifStr = interfacesField.getText().trim();
            if (!ifStr.isEmpty()) {
                cs.setInterfaces(Arrays.stream(ifStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            }

            for (FieldItemUI fi : fieldItems) {
                String fn = fi.nameField.getText().trim();
                if (!fn.isEmpty()) {
                    OopSpec.FieldSpec f = new OopSpec.FieldSpec(fn, fi.typeCombo.getValue(), fi.modifierCombo.getValue());
                    f.setMutable(fi.mutableCheck.isSelected());
                    f.setOptional(fi.optionalCheck.isSelected());
                    String defVal = fi.defValField.getText().trim();
                    f.setDefaultValue(defVal.isEmpty() ? null : defVal);
                    String minStr = fi.minField.getText().trim();
                    if (!minStr.isEmpty()) {
                        try { f.setMin(Double.parseDouble(minStr)); } catch (Exception ignored) {}
                    }
                    String maxStr = fi.maxField.getText().trim();
                    if (!maxStr.isEmpty()) {
                        try { f.setMax(Double.parseDouble(maxStr)); } catch (Exception ignored) {}
                    }
                    String maxLenStr = fi.maxLenField.getText().trim();
                    if (!maxLenStr.isEmpty()) {
                        try { f.setMaxLength(Integer.parseInt(maxLenStr)); } catch (Exception ignored) {}
                    }
                    cs.getFields().add(f);
                }
            }

            for (ConstructorItemUI ci : constructorItems) {
                List<String> pars = new ArrayList<>();
                String ps = ci.paramsField.getText().trim();
                if (!ps.isEmpty()) {
                    pars = Arrays.stream(ps.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }
                cs.getConstructors().add(new OopSpec.ConstructorSpec(ci.modifierCombo.getValue(), pars));
            }

            for (MethodItemUI mi : methodItems) {
                String mn = mi.nameField.getText().trim();
                if (!mn.isEmpty()) {
                    List<String> pars = new ArrayList<>();
                    String ps = mi.paramsField.getText().trim();
                    if (!ps.isEmpty()) {
                        pars = Arrays.stream(ps.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                    }
                    cs.getMethods().add(new OopSpec.MethodSpec(mn, mi.returnTypeField.getText().trim(), mi.modifierCombo.getValue(), pars));
                }
            }
            return cs;
        }
    }

    // ======================== FORM CONTROLS ========================

    private TabPane mainTabPane;
    private Tab oopTab;
    private ComboBox<String> problemTypeCombo;
    private Label typeStatusHint;

    // Khu vực OOP
    private VBox oopSectionBox;
    private VBox oopNotEnabledBox;
    private TabPane oopClassTabPane;
    private final List<ClassCardUI> oopClassCards = new ArrayList<>();

    // Thông tin cơ bản
    private TextField titleField;
    private ComboBox<String> difficultyCombo;
    private TextField timeLimitField;
    private TextField memoryLimitField;

    private TextArea descriptionArea;
    private TextArea inputFormatArea;
    private TextArea outputFormatArea;
    private TextArea constraintsArea;

    // Test cases
    private VBox testCasesContainer;
    private final List<TestCaseItem> testCaseItems = new ArrayList<>();

    public TeacherProblemDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Thêm bài tập mới - Dành cho Giáo viên");

        DialogPane pane = getDialogPane();
        ThemeManager.getInstance().applyCurrentZoom(pane);
        pane.getStyleClass().add("card");
        String cssPath = (ThemeManager.getInstance().isDark()) ? "/css/dark.css" : "/css/light.css";
        var res = getClass().getResource(cssPath);
        if (res != null) {
            pane.getStylesheets().setAll(res.toExternalForm());
        }
        pane.setPrefWidth(940);
        pane.setPrefHeight(720);

        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab generalTab = new Tab("📋 1. Thông tin chung", buildGeneralInfoTab());
        oopTab = new Tab("🏛️ 2. Cấu trúc OOP", buildOopTab());
        Tab testCaseTab = new Tab("🧪 3. Bộ Test Cases", buildTestCasesTab());

        mainTabPane.getTabs().addAll(generalTab, oopTab, testCaseTab);
        pane.setContent(mainTabPane);

        ButtonType saveBtnType = new ButtonType("Lưu bài tập", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveBtnType, cancelBtnType);

        Button saveBtn = (Button) pane.lookupButton(saveBtnType);
        saveBtn.getStyleClass().addAll("button", "btn-primary");

        Button cancelBtn = (Button) pane.lookupButton(cancelBtnType);
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");

        setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                return extractProblemFromForm();
            }
            return null;
        });

        // Thiết lập trạng thái ban đầu cho dạng bài
        toggleProblemTypeView();
    }

    /**
     * Tab 1: Thông tin chung (General Information & Constraints)
     */
    private ScrollPane buildGeneralInfoTab() {
        VBox form = new VBox(14);
        form.setPadding(new Insets(16));

        // 1. Phân loại bài tập
        VBox typeCard = new VBox(8);
        typeCard.setStyle("-fx-background-color: -bg-surface; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: -border-subtle; -fx-border-width: 1px;");

        HBox typeRow = new HBox(12);
        typeRow.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label("Dạng bài tập (*):");
        typeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: -text-bright;");

        problemTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "IO_MATCHING (Thuật toán I/O)",
                "JAVA_OOP (Lập trình Hướng đối tượng Java)"
        ));
        problemTypeCombo.getSelectionModel().select(0);
        problemTypeCombo.setPrefWidth(320);
        problemTypeCombo.setOnAction(e -> toggleProblemTypeView());

        typeStatusHint = new Label();
        typeStatusHint.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -accent; -fx-font-weight: bold;");

        typeRow.getChildren().addAll(typeLabel, problemTypeCombo, typeStatusHint);
        typeCard.getChildren().add(typeRow);

        // 2. Tiêu đề bài tập
        VBox titleBox = new VBox(6);
        Label titleLabel = new Label("Tiêu đề bài tập (*)");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-bright;");
        titleField = new TextField();
        titleField.setPromptText("Ví dụ: Quản lý sinh viên (OOP), Tìm số nguyên tố...");
        titleBox.getChildren().addAll(titleLabel, titleField);

        // 3. Thông số kỹ thuật (Độ khó, Giới hạn thời gian, Bộ nhớ)
        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox diffBox = new VBox(4);
        Label diffLabel = new Label("Độ khó");
        diffLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        difficultyCombo = new ComboBox<>(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));
        difficultyCombo.getSelectionModel().select("EASY");
        difficultyCombo.setPrefWidth(120);
        diffBox.getChildren().addAll(diffLabel, difficultyCombo);

        VBox timeBox = new VBox(4);
        Label timeLabel = new Label("Thời gian tối đa (ms)");
        timeLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        timeLimitField = new TextField("1000");
        timeLimitField.setPrefWidth(140);
        timeBox.getChildren().addAll(timeLabel, timeLimitField);

        VBox memBox = new VBox(4);
        Label memLabel = new Label("Bộ nhớ tối đa (KB)");
        memLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        memoryLimitField = new TextField("524288"); // 512MB
        memoryLimitField.setPrefWidth(140);
        memBox.getChildren().addAll(memLabel, memoryLimitField);

        metaRow.getChildren().addAll(diffBox, timeBox, memBox);

        // 4. Mô tả bài toán
        VBox descBox = new VBox(6);
        Label descLabel = new Label("Mô tả bài toán (*)");
        descLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-bright;");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Nêu rõ yêu cầu bài toán, các chức năng cần cài đặt...");
        descriptionArea.setPrefRowCount(4);
        descBox.getChildren().addAll(descLabel, descriptionArea);

        // 5. Input & Output format
        HBox ioFormatRow = new HBox(14);
        VBox inFmtBox = new VBox(4);
        Label inFmtLabel = new Label("Định dạng đầu vào (Input Format)");
        inFmtLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        inputFormatArea = new TextArea();
        inputFormatArea.setPromptText("Dòng 1 chứa n, các dòng sau chứa thông tin...");
        inputFormatArea.setPrefRowCount(3);
        HBox.setHgrow(inFmtBox, Priority.ALWAYS);
        inFmtBox.getChildren().addAll(inFmtLabel, inputFormatArea);

        VBox outFmtBox = new VBox(4);
        Label outFmtLabel = new Label("Định dạng đầu ra (Output Format)");
        outFmtLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        outputFormatArea = new TextArea();
        outputFormatArea.setPromptText("In ra danh sách hoặc kết quả tính toán...");
        outputFormatArea.setPrefRowCount(3);
        HBox.setHgrow(outFmtBox, Priority.ALWAYS);
        outFmtBox.getChildren().addAll(outFmtLabel, outputFormatArea);

        ioFormatRow.getChildren().addAll(inFmtBox, outFmtBox);

        // 6. Constraints
        VBox conBox = new VBox(4);
        Label conLabel = new Label("Ràng buộc (Constraints)");
        conLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary; -fx-font-weight: bold;");
        constraintsArea = new TextArea();
        constraintsArea.setPromptText("1 <= N <= 10^5, các giá trị số nguyên...");
        constraintsArea.setPrefRowCount(2);
        conBox.getChildren().addAll(conLabel, constraintsArea);

        form.getChildren().addAll(typeCard, titleBox, metaRow, descBox, ioFormatRow, conBox);

        ScrollPane sp = new ScrollPane(form);
        sp.getStyleClass().add("scroll-pane");
        sp.setFitToWidth(true);
        return sp;
    }

    /**
     * Tab 2: Thiết kế đặc tả cấu trúc OOP (OOP Specification)
     */
    private StackPane buildOopTab() {
        StackPane root = new StackPane();

        // 1. Giao diện khi bài tập KHÔNG PHẢI là JAVA_OOP
        oopNotEnabledBox = new VBox(14);
        oopNotEnabledBox.setAlignment(Pos.CENTER);
        oopNotEnabledBox.setPadding(new Insets(40));

        Label notOopIcon = new Label("🏛️");
        notOopIcon.setStyle("-fx-font-size: 40px;");
        Label notOopTitle = new Label("Bài tập hiện tại đang ở chế độ 'Thuật toán I/O'");
        notOopTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
        Label notOopDesc = new Label("Để thiết kế cấu trúc lớp OOP (Fields, Constructors, Methods), vui lòng chuyển Dạng bài tập sang 'JAVA_OOP'.");
        notOopDesc.setStyle("-fx-text-fill: -text-secondary; -fx-font-size: 12.5px;");

        Button switchOopBtn = new Button("Chuyển sang dạng JAVA_OOP ngay");
        switchOopBtn.getStyleClass().addAll("button", "btn-primary");
        switchOopBtn.setStyle("-fx-padding: 6 16 6 16; -fx-font-size: 12px; -fx-cursor: hand;");
        switchOopBtn.setOnAction(e -> {
            problemTypeCombo.getSelectionModel().select(1);
            toggleProblemTypeView();
        });

        oopNotEnabledBox.getChildren().addAll(notOopIcon, notOopTitle, notOopDesc, switchOopBtn);

        // 2. Giao diện Thiết kế OOP chuyên nghiệp khi là JAVA_OOP
        oopSectionBox = new VBox(10);
        oopSectionBox.setPadding(new Insets(12));

        // Banner giải thích quy chuẩn chấm OOP
        HBox banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8 14;");

        Label infoIcon = new Label("ℹ");
        infoIcon.setStyle("-fx-font-size: 15px; -fx-text-fill: -accent; -fx-font-weight: bold;");
        Label infoTxt = new Label("QUY TẮC CHẤM JAVA OOP: Hệ thống dùng Reflection kiểm tra cấu trúc (đóng gói, getter/setter theo mutable, số lượng constructor), sau đó kiểm thử động (giá trị mặc định, validator biên) và chấm test case qua Main.java.");
        infoTxt.setWrapText(true);
        infoTxt.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary;");
        HBox.setHgrow(infoTxt, Priority.ALWAYS);
        banner.getChildren().addAll(infoIcon, infoTxt);

        // Thanh công cụ quản lý các Lớp
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label secTitle = new Label("DANH SÁCH CÁC LỚP OOP ĐƯỢC ĐẶC TẢ:");
        secTitle.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: -accent;");

        Region spT = new Region();
        HBox.setHgrow(spT, Priority.ALWAYS);

        Button addClassBtn = new Button("➕ Thêm Lớp mới");
        addClassBtn.getStyleClass().addAll("button", "btn-primary");
        addClassBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 5 14; -fx-font-weight: bold; -fx-cursor: hand;");
        addClassBtn.setOnAction(e -> {
            ClassCardUI card = addClassCard("", "CLASS", null, new ArrayList<>());
            oopClassTabPane.getSelectionModel().select(card.tab);
        });

        toolbar.getChildren().addAll(secTitle, spT, addClassBtn);

        // TabPane chứa các Lớp OOP: Mỗi lớp là 1 Tab độc lập
        oopClassTabPane = new TabPane();
        oopClassTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(oopClassTabPane, Priority.ALWAYS);

        oopSectionBox.getChildren().addAll(banner, toolbar, oopClassTabPane);

        root.getChildren().addAll(oopNotEnabledBox, oopSectionBox);
        return root;
    }

    /**
     * Tab 3: Danh sách Test Cases (I/O Matching)
     */
    private ScrollPane buildTestCasesTab() {
        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        HBox tcHeader = new HBox(10);
        tcHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label tcTitle = new Label("BỘ TEST CASE QUA MAIN.JAVA (I/O MATCHING)");
        tcTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -accent;");
        Label tcSub = new Label("So khớp chuẩn stdin/stdout cho hàm main của sinh viên sau khi vượt qua kiểm tra cấu trúc OOP.");
        tcSub.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-secondary;");
        titleBox.getChildren().addAll(tcTitle, tcSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addTcBtn = new Button("➕ Thêm Testcase");
        addTcBtn.getStyleClass().addAll("button", "btn-primary");
        addTcBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 4 12; -fx-cursor: hand;");
        addTcBtn.setOnAction(e -> addTestCase(false, "", ""));

        tcHeader.getChildren().addAll(titleBox, sp, addTcBtn);

        testCasesContainer = new VBox(10);

        // Khởi tạo sẵn 2 testcase ban đầu
        addTestCase(true, "3\nNguyen Van A 3.5\nTran Thi B 3.8\nLe Van C 2.9", "Tran Thi B 3.8");
        addTestCase(false, "1\nPham Van D 4.0", "Pham Van D 4.0");

        form.getChildren().addAll(tcHeader, testCasesContainer);

        ScrollPane spRoot = new ScrollPane(form);
        spRoot.getStyleClass().add("scroll-pane");
        spRoot.setFitToWidth(true);
        return spRoot;
    }

    private void toggleProblemTypeView() {
        boolean isOop = problemTypeCombo.getSelectionModel().getSelectedIndex() == 1;

        oopNotEnabledBox.setVisible(!isOop);
        oopNotEnabledBox.setManaged(!isOop);

        oopSectionBox.setVisible(isOop);
        oopSectionBox.setManaged(isOop);

        if (isOop) {
            typeStatusHint.setText("✓ Đã bật chế độ OOP — Thiết kế cấu trúc các lớp tại Tab '2. Cấu trúc OOP'");
            if (oopClassCards.isEmpty()) {
                // Khởi tạo mẫu 1 Class gợi ý (Constructors tự động sinh theo chuẩn học thuật)
                ClassCardUI sampleClass = addClassCard("Student", "CLASS", null, new ArrayList<>());
                sampleClass.addField("id", "String", "private");
                sampleClass.addField("name", "String", "private");
                sampleClass.addField("gpa", "double", "private");
                sampleClass.addMethod("toString", "String", "public", new ArrayList<>());
            }
        } else {
            typeStatusHint.setText("✓ Thuật toán I/O tiêu chuẩn (Chấm stdin/stdout)");
        }
    }

    private ClassCardUI addClassCard(String name, String type, String superClass, List<String> interfaces) {
        ClassCardUI[] holder = new ClassCardUI[1];
        holder[0] = new ClassCardUI(name, type, superClass, interfaces, () -> {
            if (oopClassCards.size() <= 1) {
                Alert a = new Alert(Alert.AlertType.WARNING, "Bài tập OOP cần có ít nhất 1 Lớp.");
                a.initOwner(getDialogPane().getScene().getWindow());
                a.setHeaderText(null);
                a.showAndWait();
                return;
            }
            oopClassCards.remove(holder[0]);
            oopClassTabPane.getTabs().remove(holder[0].tab);
        });
        oopClassCards.add(holder[0]);
        oopClassTabPane.getTabs().add(holder[0].tab);
        return holder[0];
    }

    private void addTestCase(boolean isSample, String inDefault, String outDefault) {
        TestCaseItem[] holder = new TestCaseItem[1];
        holder[0] = new TestCaseItem(isSample, inDefault, outDefault, () -> {
            if (testCaseItems.size() > 1) {
                testCaseItems.remove(holder[0]);
                testCasesContainer.getChildren().remove(holder[0].rootCard);
            }
        });
        testCaseItems.add(holder[0]);
        testCasesContainer.getChildren().add(holder[0].rootCard);
    }

    private Problem extractProblemFromForm() {
        Problem problem = new Problem();
        problem.setTitle(titleField.getText().trim().isEmpty() ? "Bài tập mới" : titleField.getText().trim());
        problem.setDifficulty(difficultyCombo.getSelectionModel().getSelectedItem());
        try {
            problem.setTimeLimitMs(Integer.parseInt(timeLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setTimeLimitMs(1000);
        }
        try {
            problem.setMemoryLimitKb(Integer.parseInt(memoryLimitField.getText().trim()));
        } catch (NumberFormatException e) {
            problem.setMemoryLimitKb(524288);
        }

        problem.setClassId(null);
        problem.setClassName(null);

        boolean isOop = problemTypeCombo.getSelectionModel().getSelectedIndex() == 1;
        if (isOop) {
            problem.setProblemType("JAVA_OOP");
            OopSpec spec = new OopSpec();
            for (ClassCardUI card : oopClassCards) {
                OopSpec.ClassSpec cs = card.toSpec();
                if (cs != null) {
                    spec.getClasses().add(cs);
                }
            }
            problem.setOopSpec(new Gson().toJson(spec));
        } else {
            problem.setProblemType("IO_MATCHING");
            problem.setOopSpec(null);
        }

        StringBuilder fullDesc = new StringBuilder();
        fullDesc.append(descriptionArea.getText().trim());

        String inFmt = inputFormatArea.getText().trim();
        String outFmt = outputFormatArea.getText().trim();
        String con = constraintsArea.getText().trim();

        if (!inFmt.isEmpty()) {
            fullDesc.append("\n\n**Input:**\n").append(inFmt);
        }
        if (!outFmt.isEmpty()) {
            fullDesc.append("\n\n**Output:**\n").append(outFmt);
        }
        if (!con.isEmpty()) {
            fullDesc.append("\n\n**Giới hạn (Constraints):**\n").append(con);
        }

        problem.setDescription(fullDesc.toString());
        return problem;
    }

    /**
     * Trả về toàn bộ danh sách testcase dưới dạng JsonArray để nộp lên Server.
     */
    public JsonArray getTestCasesArray() {
        JsonArray arr = new JsonArray();
        for (TestCaseItem item : testCaseItems) {
            JsonObject obj = new JsonObject();
            obj.addProperty("inputData", item.inputArea.getText());
            obj.addProperty("expectedOutput", item.outputArea.getText());
            obj.addProperty("isSample", item.sampleCheck.isSelected());
            arr.add(obj);
        }
        return arr;
    }

    /** Nạp dữ liệu bài đã có để giáo viên sửa cả metadata lẫn test case và cấu trúc OOP. */
    public void loadForEditing(Problem problem, JsonArray testCases) {
        if (problem == null) return;
        setTitle("Chỉnh sửa bài tập");
        titleField.setText(problem.getTitle() == null ? "" : problem.getTitle());
        difficultyCombo.getSelectionModel().select(problem.getDifficulty());
        timeLimitField.setText(String.valueOf(problem.getTimeLimitMs()));
        memoryLimitField.setText(String.valueOf(problem.getMemoryLimitKb()));
        descriptionArea.setText(problem.getDescription() == null ? "" : problem.getDescription());

        // Loại bài
        if (problem.isJavaOop()) {
            problemTypeCombo.getSelectionModel().select(1);
        } else {
            problemTypeCombo.getSelectionModel().select(0);
        }
        toggleProblemTypeView();

        // Nạp cấu trúc OOP nếu có
        oopClassCards.clear();
        oopClassTabPane.getTabs().clear();
        if (problem.isJavaOop() && problem.getOopSpec() != null && !problem.getOopSpec().isBlank()) {
            try {
                OopSpec spec = new Gson().fromJson(problem.getOopSpec(), OopSpec.class);
                if (spec != null && spec.getClasses() != null) {
                    for (OopSpec.ClassSpec cs : spec.getClasses()) {
                        ClassCardUI card = addClassCard(cs.getName(), cs.getClassType(), cs.getSuperClass(), cs.getInterfaces());
                        card.setCustomConstructors(true);
                        if (cs.getFields() != null) {
                            for (OopSpec.FieldSpec f : cs.getFields()) {
                                card.addField(f.getName(), f.getType(), f.getModifier(),
                                        f.getMutable(), f.getOptional(),
                                        f.getDefaultValue(), f.getMin(), f.getMax(), f.getMaxLength());
                            }
                        }
                        if (cs.getConstructors() != null) {
                            for (OopSpec.ConstructorSpec c : cs.getConstructors()) {
                                card.addConstructor(c.getModifier(), c.getParameterTypes());
                            }
                        }
                        if (cs.getMethods() != null) {
                            for (OopSpec.MethodSpec m : cs.getMethods()) {
                                card.addMethod(m.getName(), m.getReturnType(), m.getModifier(), m.getParameterTypes());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Test cases
        testCaseItems.clear();
        testCasesContainer.getChildren().clear();
        if (testCases != null) {
            for (JsonElement element : testCases) {
                if (!element.isJsonObject()) continue;
                JsonObject test = element.getAsJsonObject();
                addTestCase(test.has("isSample") && test.get("isSample").getAsBoolean(),
                        test.has("inputData") ? test.get("inputData").getAsString() : "",
                        test.has("expectedOutput") ? test.get("expectedOutput").getAsString() : "");
            }
        }
        if (testCaseItems.isEmpty()) addTestCase(true, "", "");
    }

    public String getSampleInput() {
        for (TestCaseItem item : testCaseItems) {
            if (item.sampleCheck.isSelected()) return item.inputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).inputArea.getText();
    }

    public String getSampleOutput() {
        for (TestCaseItem item : testCaseItems) {
            if (item.sampleCheck.isSelected()) return item.outputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).outputArea.getText();
    }

    public String getSecretInput() {
        for (TestCaseItem item : testCaseItems) {
            if (!item.sampleCheck.isSelected()) return item.inputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).inputArea.getText();
    }

    public String getSecretOutput() {
        for (TestCaseItem item : testCaseItems) {
            if (!item.sampleCheck.isSelected()) return item.outputArea.getText();
        }
        return testCaseItems.isEmpty() ? "" : testCaseItems.get(0).outputArea.getText();
    }
}
