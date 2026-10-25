package com.grading.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility giải nén an toàn file zip nộp từ sinh viên (chứa nhiều file .java cho bài Java OOP).
 */
public class ZipUtils {

    private static final Logger logger = LoggerFactory.getLogger(ZipUtils.class);

    /**
     * Giải nén chuỗi Base64 zip vào thư mục targetDir.
     * Tự động flatten các file mã nguồn vào thẳng thư mục gốc để thuận tiện cho javac và reflection.
     */
    public static void extractBase64Zip(String base64Zip, File targetDir) throws IOException {
        if (base64Zip == null || base64Zip.isBlank()) {
            throw new IllegalArgumentException("Dữ liệu zip rỗng");
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        byte[] zipBytes;
        try {
            // Hỗ trợ cả Base64 chuẩn và URL-safe, bỏ qua whitespace hoặc data: URL prefix nếu có
            String cleanBase64 = base64Zip.trim();
            if (cleanBase64.contains(",")) {
                cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
            }
            zipBytes = Base64.getDecoder().decode(cleanBase64);
        } catch (IllegalArgumentException e) {
            throw new IOException("Chuỗi Base64 không hợp lệ: " + e.getMessage(), e);
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String rawName = entry.getName();
                // Bỏ qua các file rác của hệ thống (macOS __MACOSX, .DS_Store...)
                if (rawName.startsWith("__MACOSX") || rawName.contains(".DS_Store") || rawName.endsWith(".class")) {
                    continue;
                }

                // Lấy tên file đơn thuần (flatten)
                String fileName = new File(rawName).getName();
                if (fileName.isBlank()) continue;

                File outFile = new File(targetDir, fileName);
                // Phòng chống tấn công Zip Slip
                if (!outFile.toPath().normalize().startsWith(targetDir.toPath().normalize())) {
                    logger.warn("Phát hiện Zip Slip attempt trong entry: {}", rawName);
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
                logger.debug("Extracted zip entry '{}' -> '{}'", rawName, outFile.getAbsolutePath());
            }
        }
    }
}
