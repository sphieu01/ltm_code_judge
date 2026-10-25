package com.grading.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hộp thoại hiển thị mã nguồn đã nộp.
 * - Mã nguồn văn bản thông thường: Hiển thị trực tiếp trên TextArea.
 * - Gói mã nguồn Java OOP (ZIP Base64): Tự động giải nén trong bộ nhớ và hiển thị từng file theo Tabs,
 *   đồng thời cung cấp nút "Tải file ZIP về máy".
 */
public class SourceCodeViewerDialog extends Dialog<Void> {

    public SourceCodeViewerDialog(Stage owner, String title, String timestamp, String sourceCode, boolean isZip) {
        initOwner(owner);
        setTitle("Mã nguồn đã nộp");
        setHeaderText((title != null ? title : "Bài nộp") + (timestamp != null && !timestamp.isBlank() ? " — " + timestamp : ""));
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        if (sourceCode == null || sourceCode.isBlank()) {
            TextArea emptyArea = createCodeArea("Không có mã nguồn để hiển thị.");
            getDialogPane().setContent(emptyArea);
            getDialogPane().setPrefSize(760, 500);
            return;
        }

        // Tự động nhận diện file ZIP nếu isZip = true hoặc chuỗi bắt đầu bằng ZIP Base64 header "UEsDB"
        boolean looksLikeZip = isZip || sourceCode.trim().startsWith("UEsDB");

        if (looksLikeZip) {
            try {
                byte[] zipBytes = Base64.getDecoder().decode(sourceCode.trim().replaceAll("\\s+", ""));
                Map<String, String> files = extractZipFiles(zipBytes);

                if (!files.isEmpty()) {
                    showZipTabsView(zipBytes, files, title);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Không thể giải nén gói ZIP Base64: " + e.getMessage());
            }
        }

        // Trường hợp mã nguồn đơn file (C/C++/Java I/O/Python)
        TextArea codeArea = createCodeArea(sourceCode);
        getDialogPane().setContent(codeArea);
        getDialogPane().setPrefSize(780, 520);
    }

    private void showZipTabsView(byte[] zipBytes, Map<String, String> files, String problemTitle) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Toolbar trên cùng
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label infoLabel = new Label("📦 Gói Java OOP (ZIP): " + files.size() + " tập tin nguồn");
        infoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a73e8;");

        Button downloadBtn = new Button("📥 Tải file ZIP về máy");
        downloadBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        downloadBtn.setOnAction(e -> handleDownloadZip(zipBytes, problemTitle));

        topBar.getChildren().addAll(infoLabel, downloadBtn);

        // TabPane cho từng file nguồn
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();

            Tab tab = new Tab("📄 " + fileName);
            TextArea area = createCodeArea(content);
            tab.setContent(area);
            tabPane.getTabs().add(tab);
        }

        root.getChildren().addAll(topBar, tabPane);
        getDialogPane().setContent(root);
        getDialogPane().setPrefSize(840, 580);
    }

    private Map<String, String> extractZipFiles(byte[] zipBytes) {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[2048];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    files.put(entry.getName(), baos.toString(StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi đọc file ZIP: " + e.getMessage());
        }
        return files;
    }

    private void handleDownloadZip(byte[] zipBytes, String problemTitle) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu gói mã nguồn ZIP");
        String safeName = (problemTitle != null && !problemTitle.isBlank())
                ? problemTitle.replaceAll("[^a-zA-Z0-9_-]", "_")
                : "submission";
        chooser.setInitialFileName(safeName + "_solution.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archive (*.zip)", "*.zip"));

        File target = chooser.showSaveDialog(getOwner());
        if (target != null) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(zipBytes);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Đã tải file ZIP về:\n" + target.getAbsolutePath(), ButtonType.OK);
                alert.initOwner(getOwner());
                alert.setHeaderText("Tải về thành công");
                alert.showAndWait();
            } catch (Exception ex) {
                System.err.println("Lỗi khi ghi file ZIP: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi khi lưu file: " + ex.getMessage(), ButtonType.OK);
                alert.initOwner(getOwner());
                alert.showAndWait();
            }
        }
    }

    private TextArea createCodeArea(String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("code-editor");
        area.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 13px; -fx-background-color: #f8f9fa;");
        area.setPrefSize(780, 460);
        return area;
    }
}
