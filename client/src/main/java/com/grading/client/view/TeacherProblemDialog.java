package com.grading.client.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grading.client.model.CreditClass;
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
 * Hỗ trợ 2 loại bài:
 * 1. IO_MATCHING: Thuật toán so khớp I/O qua stdin/stdout.
 * 2. JAVA_OOP: Lập trình Hướng đối tượng Java với bộ thiết kế cấu trúc lớp OOP trực quan (Reflection Grading)
 *    và kiểm thử I/O qua Main.java.
 */
public class TeacherProblemDialog extends Dialog<Problem> {

    public static class TestCaseItem {
        public final CheckBox sampleCheck;
        public final TextArea inputArea;
        public final TextArea outputArea;
        public final VBox rootCard;

        public TestCaseItem(boolean isSample, String inputDefault, String outputDefault, Runnable onDelete) {
            rootCard = new VBox(6);
            rootCard.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 10px;");

            HBox cardHeader = new HBox(10);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            sampleCheck = new CheckBox("Là Testcase ví dụ (Sample - Hiển thị trong đề bài)");
            sampleCheck.setSelected(isSample);
            sampleCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-bright;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button deleteBtn = new Button("🗑 Xóa");
            deleteBtn.getStyleClass().addAll("button", "btn-secondary");
            deleteBtn.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            deleteBtn.setOnAction(e -> onDelete.run());

            cardHeader.getChildren().addAll(sampleCheck, sp, deleteBtn);

            HBox ioRow = new HBox(10);
            VBox inBox = new VBox(3);
            Label inLbl = new Label("Input:");
            inLbl.getStyleClass().add("meta-item");
            inputArea = new TextArea(inputDefault);
            inputArea.setPromptText("Dữ liệu đầu vào (Input)...");
            inputArea.setPrefRowCount(2);
            inputArea.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
            inBox.getChildren().addAll(inLbl, inputArea);
            HBox.setHgrow(inBox, Priority.ALWAYS);

            VBox outBox = new VBox(3);
            Label outLbl = new Label("Expected Output:");
            outLbl.getStyleClass().add("meta-item");
            outputArea = new TextArea(outputDefault);
            outputArea.setPromptText("Kết quả mong đợi (Output)...");
            outputArea.setPrefRowCount(2);
            outputArea.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 12px;");
            outBox.getChildren().addAll(outLbl, outputArea);
            HBox.setHgrow(outBox, Priority.ALWAYS);

            ioRow.getChildren().addAll(inBox, outBox);
            rootCard.getChildren().addAll(cardHeader, ioRow);
        }
    }

    // ======================== OOP DESIGNER UI COMPONENTS ========================

    private static class FieldItemUI {
        final TextField nameField = new TextField();
        final ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "String", "int", "double", "boolean", "long", "float", "char", "Date", "List"
        ));
        final ComboBox<String> modifierCombo = new ComboBox<>(FXCollections.observableArrayList(
                "private", "protected", "public"
        ));
        final CheckBox mutableCheck = new CheckBox("Mutable (Có setter)");
        final CheckBox optionalCheck = new CheckBox("Optional");
        final TextField defValField = new TextField();
        final TextField minField = new TextField();
        final TextField maxField = new TextField();
        final TextField maxLenField = new TextField();

        final VBox row = new VBox(4);

        FieldItemUI(String name, String type, String modifier, Boolean mutable, Boolean optional,
                    String defaultValue, Double min, Double max, Integer maxLength, Runnable onDelete) {
            nameField.setPromptText("Tên thuộc tính (e.g. name, gpa)");
            nameField.setText(name != null ? name : "");
            nameField.setPrefWidth(140);

            typeCombo.setEditable(true);
            typeCombo.setValue(type != null && !type.isBlank() ? type : "String");
            typeCombo.setPrefWidth(100);

            modifierCombo.setValue(modifier != null && !modifier.isBlank() ? modifier : "private");
            modifierCombo.setPrefWidth(90);

            mutableCheck.setSelected(mutable == null || mutable);
            optionalCheck.setSelected(optional != null && optional);

            defValField.setPromptText("Mặc định (VD: 0.0)");
            defValField.setText(defaultValue != null ? defaultValue : "");
            defValField.setPrefWidth(110);

            minField.setPromptText("Min");
            minField.setText(min != null ? String.valueOf(min) : "");
            minField.setPrefWidth(60);

            maxField.setPromptText("Max");
            maxField.setText(max != null ? String.valueOf(max) : "");
            maxField.setPrefWidth(60);

            maxLenField.setPromptText("MaxLen");
            maxLenField.setText(maxLength != null ? String.valueOf(maxLength) : "");
            maxLenField.setPrefWidth(65);

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 2 6 2 6;");
            delBtn.setOnAction(e -> onDelete.run());

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            HBox line1 = new HBox(8);
            line1.setAlignment(Pos.CENTER_LEFT);
            line1.getChildren().addAll(
                    new Label("Tên:"), nameField,
                    new Label("Kiểu:"), typeCombo,
                    new Label("Quyền:"), modifierCombo,
                    mutableCheck,
                    optionalCheck,
                    sp,
                    delBtn
            );

            HBox line2 = new HBox(8);
            line2.setAlignment(Pos.CENTER_LEFT);
            line2.setStyle("-fx-padding: 2 0 2 0;");
            Label constraintLabel = new Label("↳ Ràng buộc:");
            constraintLabel.setStyle("-fx-text-fill: -text-muted; -fx-font-size: 11px;");

            Label defLabel = new Label("Default:");
            defLabel.setStyle("-fx-font-size: 11px;");
            Label minLabel = new Label("Min:");
            minLabel.setStyle("-fx-font-size: 11px;");
            Label maxLabel = new Label("Max:");
            maxLabel.setStyle("-fx-font-size: 11px;");
            Label lenLabel = new Label("MaxLen:");
            lenLabel.setStyle("-fx-font-size: 11px;");

            line2.getChildren().addAll(
                    constraintLabel,
                    defLabel, defValField,
                    minLabel, minField,
                    maxLabel, maxField,
                    lenLabel, maxLenField
            );

            row.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6px;");
            row.getChildren().addAll(line1, line2);
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
            modifierCombo.setPrefWidth(110);

            paramsField.setPromptText("Kiểu tham số phân cách dấu phẩy (e.g. String, double hoặc để trống nếu constructor mặc định)");
            if (params != null && !params.isEmpty()) {
                paramsField.setText(String.join(", ", params));
            }
            HBox.setHgrow(paramsField, Priority.ALWAYS);

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 2 6 2 6;");
            delBtn.setOnAction(e -> onDelete.run());

            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(
                    new Label("Quyền:"), modifierCombo,
                    new Label("Tham số:"), paramsField,
                    delBtn
            );
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
            modifierCombo.setPrefWidth(100);

            paramsField.setPromptText("Tham số (e.g. double, int)");
            if (params != null && !params.isEmpty()) {
                paramsField.setText(String.join(", ", params));
            }
            HBox.setHgrow(paramsField, Priority.ALWAYS);

            Button delBtn = new Button("✕");
            delBtn.getStyleClass().addAll("button", "btn-secondary");
            delBtn.setStyle("-fx-text-fill: -status-wa; -fx-padding: 2 6 2 6;");
            delBtn.setOnAction(e -> onDelete.run());

            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(
                    new Label("Tên:"), nameField,
                    new Label("Trả về:"), returnTypeField,
                    new Label("Quyền:"), modifierCombo,
                    new Label("Tham số:"), paramsField,
                    delBtn
            );
        }
    }

    private static class ClassCardUI {
        final TextField nameField = new TextField();
        final ComboBox<String> classTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "CLASS", "ABSTRACT_CLASS", "INTERFACE"
        ));
        final TextField superClassField = new TextField();
        final TextField interfacesField = new TextField();

        final VBox fieldsContainer = new VBox(4);
        final List<FieldItemUI> fieldItems = new ArrayList<>();

        final VBox constructorsContainer = new VBox(4);
        final List<ConstructorItemUI> constructorItems = new ArrayList<>();

        final VBox methodsContainer = new VBox(4);
        final List<MethodItemUI> methodItems = new ArrayList<>();

        final VBox card = new VBox(8);

        ClassCardUI(String name, String classType, String superClass, List<String> interfaces, Runnable onDelete) {
            card.setStyle("-fx-background-color: -bg-editor; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

            // Header hàng 1: Tên class, loại, extends, nút xóa
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            nameField.setPromptText("Tên Lớp (e.g. Student, Shape)");
            nameField.setText(name != null ? name : "");
            nameField.setPrefWidth(160);

            classTypeCombo.setValue(classType != null ? classType : "CLASS");
            classTypeCombo.setPrefWidth(140);

            superClassField.setPromptText("Kế thừa (superClass, e.g. Person)");
            superClassField.setText(superClass != null ? superClass : "");
            superClassField.setPrefWidth(160);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button delClassBtn = new Button("🗑 Xóa Lớp");
            delClassBtn.getStyleClass().addAll("button", "btn-secondary");
            delClassBtn.setStyle("-fx-text-fill: -status-wa; -fx-font-size: 11px;");
            delClassBtn.setOnAction(e -> onDelete.run());

            header.getChildren().addAll(
                    new Label("Tên Lớp (*):"), nameField,
                    new Label("Loại:"), classTypeCombo,
                    new Label("Kế thừa:"), superClassField,
                    sp, delClassBtn
            );

            // Header hàng 2: Implements interfaces
            HBox implRow = new HBox(8);
            implRow.setAlignment(Pos.CENTER_LEFT);
            interfacesField.setPromptText("Các Interface triển khai (phân cách dấu phẩy, e.g. Comparable, Serializable)");
            if (interfaces != null && !interfaces.isEmpty()) {
                interfacesField.setText(String.join(", ", interfaces));
            }
            HBox.setHgrow(interfacesField, Priority.ALWAYS);
            implRow.getChildren().addAll(new Label("Triển khai (implements):"), interfacesField);

            // Section Fields
            HBox fieldHeader = new HBox(8);
            fieldHeader.setAlignment(Pos.CENTER_LEFT);
            Label fieldTitle = new Label("• Danh sách Thuộc tính (Fields - Khuyến nghị 'private' để kiểm tra tính đóng gói):");
            fieldTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            Region spF = new Region();
            HBox.setHgrow(spF, Priority.ALWAYS);
            Button addFieldBtn = new Button("➕ Thêm Field");
            addFieldBtn.getStyleClass().addAll("button", "btn-secondary");
            addFieldBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            addFieldBtn.setOnAction(e -> addField("", "String", "private"));
            fieldHeader.getChildren().addAll(fieldTitle, spF, addFieldBtn);

            // Section Constructors
            HBox ctorHeader = new HBox(8);
            ctorHeader.setAlignment(Pos.CENTER_LEFT);
            Label ctorTitle = new Label("• Danh sách Hàm khởi tạo (Constructors):");
            ctorTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            Region spC = new Region();
            HBox.setHgrow(spC, Priority.ALWAYS);
            Button addCtorBtn = new Button("➕ Thêm Constructor");
            addCtorBtn.getStyleClass().addAll("button", "btn-secondary");
            addCtorBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            addCtorBtn.setOnAction(e -> addConstructor("public", new ArrayList<>()));
            ctorHeader.getChildren().addAll(ctorTitle, spC, addCtorBtn);

            // Section Methods
            HBox methodHeader = new HBox(8);
            methodHeader.setAlignment(Pos.CENTER_LEFT);
            Label methodTitle = new Label("• Danh sách Phương thức (Methods):");
            methodTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: -text-bright;");
            Region spM = new Region();
            HBox.setHgrow(spM, Priority.ALWAYS);
            Button addMethodBtn = new Button("➕ Thêm Method");
            addMethodBtn.getStyleClass().addAll("button", "btn-secondary");
            addMethodBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8;");
            addMethodBtn.setOnAction(e -> addMethod("", "void", "public", new ArrayList<>()));
            methodHeader.getChildren().addAll(methodTitle, spM, addMethodBtn);

            card.getChildren().addAll(
                    header, implRow,
                    new Separator(),
                    fieldHeader, fieldsContainer,
                    new Separator(),
                    ctorHeader, constructorsContainer,
                    new Separator(),
                    methodHeader, methodsContainer
            );
        }

        void addField(String name, String type, String modifier) {
            addField(name, type, modifier, true, false, null, null, null, null);
        }

        void addField(String name, String type, String modifier, Boolean mutable, Boolean optional,
                      String defaultValue, Double min, Double max, Integer maxLength) {
            FieldItemUI[] holder = new FieldItemUI[1];
            holder[0] = new FieldItemUI(name, type, modifier, mutable, optional, defaultValue, min, max, maxLength, () -> {
                fieldItems.remove(holder[0]);
                fieldsContainer.getChildren().remove(holder[0].row);
            });
            fieldItems.add(holder[0]);
            fieldsContainer.getChildren().add(holder[0].row);
        }

        void addConstructor(String modifier, List<String> params) {
            ConstructorItemUI[] holder = new ConstructorItemUI[1];
            holder[0] = new ConstructorItemUI(modifier, params, () -> {
                constructorItems.remove(holder[0]);
                constructorsContainer.getChildren().remove(holder[0].row);
            });
            constructorItems.add(holder[0]);
            constructorsContainer.getChildren().add(holder[0].row);
        }

        void addMethod(String name, String returnType, String modifier, List<String> params) {
            MethodItemUI[] holder = new MethodItemUI[1];
            holder[0] = new MethodItemUI(name, returnType, modifier, params, () -> {
                methodItems.remove(holder[0]);
                methodsContainer.getChildren().remove(holder[0].row);
            });
            methodItems.add(holder[0]);
            methodsContainer.getChildren().add(holder[0].row);
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

    private ComboBox<String> problemTypeCombo;
    private VBox oopSectionBox;
    private VBox oopClassesContainer;
    private final List<ClassCardUI> oopClassCards = new ArrayList<>();

    private TextField titleField;
    private ComboBox<String> difficultyCombo;
    private TextField timeLimitField;
    private TextField memoryLimitField;

    private TextArea descriptionArea;
    private TextArea inputFormatArea;
    private TextArea outputFormatArea;
    private TextArea constraintsArea;

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
        pane.setPrefWidth(880);
        pane.setPrefHeight(720);

        ScrollPane scrollPane = new ScrollPane(buildFormContent());
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setFitToWidth(true);
        pane.setContent(scrollPane);

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
    }

    private VBox buildFormContent() {
        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        // 1. Phân loại bài tập
        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox typeBox = new VBox(4);
        Label typeLabel = new Label("Dạng bài tập (*)");
        typeLabel.getStyleClass().add("meta-item");
        problemTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "IO_MATCHING (Thuật toán I/O)",
                "JAVA_OOP (Lập trình Hướng đối tượng Java)"
        ));
        problemTypeCombo.getSelectionModel().select(0);
        problemTypeCombo.setPrefWidth(350);
        problemTypeCombo.setOnAction(e -> toggleProblemTypeView());
        typeBox.getChildren().addAll(typeLabel, problemTypeCombo);

        topRow.getChildren().addAll(typeBox);

        // 2. Tiêu đề & Thông số
        Label titleLabel = new Label("Tiêu đề bài tập (*)");
        titleLabel.getStyleClass().add("meta-item");
        titleField = new TextField();
        titleField.setPromptText("Ví dụ: Quản lý sinh viên (OOP), Tìm số nguyên tố...");

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox diffBox = new VBox(4);
        Label diffLabel = new Label("Độ khó");
        diffLabel.getStyleClass().add("meta-item");
        difficultyCombo = new ComboBox<>(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));
        difficultyCombo.getSelectionModel().select("EASY");
        diffBox.getChildren().addAll(diffLabel, difficultyCombo);

        VBox timeBox = new VBox(4);
        Label timeLabel = new Label("Thời gian tối đa (ms)");
        timeLabel.getStyleClass().add("meta-item");
        timeLimitField = new TextField("1000");
        timeBox.getChildren().addAll(timeLabel, timeLimitField);

        VBox memBox = new VBox(4);
        Label memLabel = new Label("Bộ nhớ tối đa (KB)");
        memLabel.getStyleClass().add("meta-item");
        memoryLimitField = new TextField("524288"); // 512MB
        memBox.getChildren().addAll(memLabel, memoryLimitField);

        metaRow.getChildren().addAll(diffBox, timeBox, memBox);

        // 3. KHU VỰC THIẾT KẾ CẤU TRÚC OOP (Ẩn khi là bài IO_MATCHING)
        oopSectionBox = buildOopSection();
        oopSectionBox.setVisible(false);
        oopSectionBox.setManaged(false);

        // 4. Mô tả bài toán
        Label descLabel = new Label("Mô tả bài toán (*)");
        descLabel.getStyleClass().add("meta-item");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Nêu rõ yêu cầu bài toán, các chức năng cần cài đặt...");
        descriptionArea.setPrefRowCount(4);

        // Input & Output format
        Label inFmtLabel = new Label("Định dạng đầu vào");
        inFmtLabel.getStyleClass().add("meta-item");
        inputFormatArea = new TextArea();
        inputFormatArea.setPromptText("Dòng 1 chứa n, các dòng sau chứa thông tin...");
        inputFormatArea.setPrefRowCount(2);

        Label outFmtLabel = new Label("Định dạng đầu ra");
        outFmtLabel.getStyleClass().add("meta-item");
        outputFormatArea = new TextArea();
        outputFormatArea.setPromptText("In ra danh sách hoặc kết quả tính toán...");
        outputFormatArea.setPrefRowCount(2);

        // Constraints
        Label conLabel = new Label("Ràng buộc");
        conLabel.getStyleClass().add("meta-item");
        constraintsArea = new TextArea();
        constraintsArea.setPromptText("1 <= N <= 10^5...");
        constraintsArea.setPrefRowCount(2);

        // 5. DANH SÁCH TESTCASE ĐỘNG
        HBox tcHeader = new HBox(10);
        tcHeader.setAlignment(Pos.CENTER_LEFT);
        tcHeader.setPadding(new Insets(10, 0, 4, 0));

        Label tcTitle = new Label("BỘ TEST CASE CHẤM BÀI QUA MAIN.JAVA (I/O MATCHING)");
        tcTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -accent;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addTcBtn = new Button("➕ Thêm Testcase");
        addTcBtn.getStyleClass().addAll("button", "btn-primary");
        addTcBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 4 10 4 10;");
        addTcBtn.setOnAction(e -> addTestCase(false, "", ""));

        tcHeader.getChildren().addAll(tcTitle, sp, addTcBtn);

        testCasesContainer = new VBox(8);

        // Khởi tạo sẵn 2 testcase ban đầu
        addTestCase(true, "3\nNguyen Van A 3.5\nTran Thi B 3.8\nLe Van C 2.9", "Tran Thi B 3.8");
        addTestCase(false, "1\nPham Van D 4.0", "Pham Van D 4.0");

        form.getChildren().addAll(
                topRow,
                titleLabel, titleField,
                metaRow,
                oopSectionBox,
                descLabel, descriptionArea,
                inFmtLabel, inputFormatArea,
                outFmtLabel, outputFormatArea,
                conLabel, constraintsArea,
                tcHeader, testCasesContainer
        );

        return form;
    }

    private VBox buildOopSection() {
        VBox sec = new VBox(10);
        sec.setStyle("-fx-background-color: -bg-surface; -fx-border-color: -border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 12px;");

        HBox banner = new HBox(8);
        banner.setAlignment(Pos.CENTER_LEFT);
        Label infoIcon = new Label("ℹ");
        infoIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: -accent; -fx-font-weight: bold;");
        Label infoTxt = new Label("QUY TẮC CHẤM JAVA OOP: Sinh viên nộp gói .zip (hoặc code) chứa các class và Main.java. Hệ thống sẽ soi Reflection cấu trúc bên dưới trước (nếu vi phạm tính đóng gói private hoặc sai method sẽ báo CE ngay). Khi đạt 100% cấu trúc mới chạy bộ test case qua Main.java.");
        infoTxt.setWrapText(true);
        infoTxt.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-secondary;");
        banner.getChildren().addAll(infoIcon, infoTxt);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("THIẾT KẾ ĐẶC TẢ CẤU TRÚC OOP (OOP SPECIFICATION)");
        title.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: -accent;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addClassBtn = new Button("➕ Thêm Lớp / Interface");
        addClassBtn.getStyleClass().addAll("button", "btn-primary");
        addClassBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 4 10 4 10;");
        addClassBtn.setOnAction(e -> addClassCard("", "CLASS", null, new ArrayList<>()));

        header.getChildren().addAll(title, sp, addClassBtn);

        oopClassesContainer = new VBox(10);

        sec.getChildren().addAll(banner, header, oopClassesContainer);
        return sec;
    }

    private void toggleProblemTypeView() {
        boolean isOop = problemTypeCombo.getSelectionModel().getSelectedIndex() == 1;
        oopSectionBox.setVisible(isOop);
        oopSectionBox.setManaged(isOop);

        if (isOop && oopClassCards.isEmpty()) {
            // Khởi tạo mẫu 1 Class gợi ý
            ClassCardUI sampleClass = addClassCard("Student", "CLASS", null, new ArrayList<>());
            sampleClass.addField("id", "String", "private");
            sampleClass.addField("name", "String", "private");
            sampleClass.addField("gpa", "double", "private");
            sampleClass.addConstructor("public", Arrays.asList("String", "String", "double"));
            sampleClass.addMethod("display", "void", "public", new ArrayList<>());
        }
    }

    private ClassCardUI addClassCard(String name, String type, String superClass, List<String> interfaces) {
        ClassCardUI[] holder = new ClassCardUI[1];
        holder[0] = new ClassCardUI(name, type, superClass, interfaces, () -> {
            oopClassCards.remove(holder[0]);
            oopClassesContainer.getChildren().remove(holder[0].card);
        });
        oopClassCards.add(holder[0]);
        oopClassesContainer.getChildren().add(holder[0].card);
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
        oopClassesContainer.getChildren().clear();
        if (problem.isJavaOop() && problem.getOopSpec() != null && !problem.getOopSpec().isBlank()) {
            try {
                OopSpec spec = new Gson().fromJson(problem.getOopSpec(), OopSpec.class);
                if (spec != null && spec.getClasses() != null) {
                    for (OopSpec.ClassSpec cs : spec.getClasses()) {
                        ClassCardUI card = addClassCard(cs.getName(), cs.getClassType(), cs.getSuperClass(), cs.getInterfaces());
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
