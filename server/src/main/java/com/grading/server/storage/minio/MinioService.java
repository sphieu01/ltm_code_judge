package com.grading.server.storage.minio;

import com.grading.server.model.Submission;
import com.grading.server.repository.SubmissionRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dịch vụ lưu trữ tệp mã nguồn Object Storage trên MinIO.
 *
 * Thiết kế theo cơ chế Graceful Degradation / Non-blocking:
 *  - Nếu MinIO offline hoặc không cấu hình: Hệ thống tự động bỏ qua, không gây lỗi cho luồng chấm hay ghi DB.
 *  - Nếu MinIO online: Lưu trữ file nguồn chuẩn (decode ZIP nếu là gói nhiều file hoặc file .java/.py/.cpp/.c).
 *  - Tự động đồng bộ các submission cũ từ DB sang MinIO khi Server khởi động nếu MinIO online.
 */
public class MinioService {

    private static final Logger logger = LoggerFactory.getLogger(MinioService.class);

    private static final String DEFAULT_ENDPOINT = "http://localhost:9000";
    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";
    private static final String DEFAULT_BUCKET = "ltm-submissions";

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;

    private MinioClient minioClient;
    private final AtomicBoolean isAvailable = new AtomicBoolean(false);

    private static MinioService instance;

    public static synchronized MinioService getInstance() {
        if (instance == null) {
            instance = new MinioService();
        }
        return instance;
    }

    public MinioService() {
        this.endpoint = getEnv("MINIO_ENDPOINT", DEFAULT_ENDPOINT);
        this.accessKey = getEnv("MINIO_ACCESS_KEY", DEFAULT_ACCESS_KEY);
        this.secretKey = getEnv("MINIO_SECRET_KEY", DEFAULT_SECRET_KEY);
        this.bucketName = getEnv("MINIO_BUCKET", DEFAULT_BUCKET);

        initClient();
    }

    private void initClient() {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // Kiểm tra kết nối và tạo bucket nếu chưa có
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                logger.info("📦 [MINIO] Đã tự động tạo bucket '{}' trên MinIO Object Storage", bucketName);
            }
            isAvailable.set(true);
            logger.info("✅ [MINIO] Kết nối thành công tới MinIO: {} (Bucket: '{}')", endpoint, bucketName);

        } catch (Exception e) {
            isAvailable.set(false);
            logger.warn("⚠️ [MINIO] MinIO không khả dụng tại {} (Lý do: {}). Hệ thống chạy chế độ Database thuần.",
                    endpoint, e.getMessage());
        }
    }

    public boolean isAvailable() {
        return isAvailable.get();
    }

    /**
     * Tải mã nguồn của một submission lên MinIO (bất đồng bộ, không block luồng xử lý).
     */
    public void uploadSubmissionAsync(Submission submission) {
        if (!isAvailable.get() || submission == null || submission.getSourceCode() == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                uploadSubmission(submission);
            } catch (Exception e) {
                logger.warn("⚠️ [MINIO] Không thể tải submission #{} lên MinIO: {}", submission.getId(), e.getMessage());
            }
        });
    }

    /**
     * Tải mã nguồn của submission lên MinIO một cách đồng bộ.
     */
    public boolean uploadSubmission(Submission submission) {
        if (!isAvailable.get() || submission == null || submission.getSourceCode() == null) {
            return false;
        }

        String objectName = buildObjectName(submission);
        byte[] contentBytes;
        String contentType;

        if (submission.isZip()) {
            try {
                contentBytes = Base64.getDecoder().decode(submission.getSourceCode().trim().replaceAll("\\s+", ""));
                contentType = "application/zip";
            } catch (Exception e) {
                // Fallback nếu không phải base64 zip hợp lệ
                contentBytes = submission.getSourceCode().getBytes(StandardCharsets.UTF_8);
                contentType = "text/plain";
            }
        } else {
            contentBytes = submission.getSourceCode().getBytes(StandardCharsets.UTF_8);
            contentType = getContentType(submission.getLanguage());
        }

        try (InputStream is = new ByteArrayInputStream(contentBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(is, contentBytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
            logger.debug("📦 [MINIO] Đã lưu submission #{} vào object: {}", submission.getId(), objectName);
            return true;
        } catch (Exception e) {
            logger.warn("⚠️ [MINIO] Lỗi khi upload object '{}': {}", objectName, e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra một submission đã tồn tại trên MinIO chưa.
     */
    public boolean objectExists(Submission submission) {
        if (!isAvailable.get() || submission == null) return false;
        String objectName = buildObjectName(submission);
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tự động quét và đồng bộ các submission trong Database sang MinIO nếu chưa có trên MinIO.
     * Chạy trên luồng nền (background thread) khi Server khởi động.
     */
    public void syncMissingSubmissionsAsync(SubmissionRepository repository) {
        if (!isAvailable.get()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("🔄 [MINIO-SYNC] Bắt đầu kiểm tra và đồng bộ submission từ DB sang MinIO...");
                List<Submission> allSubmissions = repository.findAll();
                int total = allSubmissions.size();
                int syncedCount = 0;
                int skippedCount = 0;

                for (Submission sub : allSubmissions) {
                    if (sub.getSourceCode() == null || sub.getSourceCode().isBlank()) {
                        continue;
                    }

                    if (objectExists(sub)) {
                        skippedCount++;
                    } else {
                        boolean ok = uploadSubmission(sub);
                        if (ok) syncedCount++;
                    }
                }

                logger.info("✅ [MINIO-SYNC] Hoàn tất đồng bộ! Tổng số: {}, Đã sync mới: {}, Đã có sẵn: {}",
                        total, syncedCount, skippedCount);

            } catch (Exception e) {
                logger.warn("⚠️ [MINIO-SYNC] Gặp lỗi khi đồng bộ submission sang MinIO: {}", e.getMessage());
            }
        });
    }

    private String buildObjectName(Submission submission) {
        int problemId = submission.getProblemId();
        int subId = submission.getId();
        if (submission.isZip()) {
            return String.format("problems/%d/submissions/%d.zip", problemId, subId);
        }
        String ext = getFileExtension(submission.getLanguage());
        return String.format("problems/%d/submissions/%d.%s", problemId, subId, ext);
    }

    private String getFileExtension(String language) {
        if (language == null) return "txt";
        return switch (language.toLowerCase()) {
            case "java" -> "java";
            case "cpp" -> "cpp";
            case "c" -> "c";
            case "python" -> "py";
            default -> "txt";
        };
    }

    private String getContentType(String language) {
        if (language == null) return "text/plain";
        return switch (language.toLowerCase()) {
            case "java" -> "text/x-java-source";
            case "cpp", "c" -> "text/x-c";
            case "python" -> "text/x-python";
            default -> "text/plain";
        };
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
