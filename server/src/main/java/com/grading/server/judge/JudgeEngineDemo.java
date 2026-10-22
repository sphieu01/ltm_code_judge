package com.grading.server.judge;

import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Giai Đoạn 4: Kiểm thử độc lập toàn diện Judge Engine với dữ liệu bài nộp và test cases thực tế.
 * Tích hợp đầy đủ: JudgeQueue + Worker Pool (4 workers) + OnJudgeCompletedListener Callback.
 */
public class JudgeEngineDemo {

    public static void main(String[] args) throws InterruptedException {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));

        Path testCasesDir = Paths.get("test_data", "sum_ab", "test_cases");
        Path submissionsDir = Paths.get("test_data", "sum_ab", "submissions");

        System.out.println("========================================================================");
        System.out.println("🚀 GIAI ĐOẠN 4: KIỂM THỬ ĐỘC LẬP TOÀN BỘ JUDGE ENGINE (PARALLEL)");
        System.out.println("Bài toán: Tính tổng hai số a + b");
        System.out.println("Kiến trúc: JudgeEngine (4 Workers song song) + Thread-Safe BlockingQueue");
        System.out.println("========================================================================\n");

        // 1. Nạp test cases từ thư mục test_data/sum_ab/test_cases
        List<TestCase> testCases = loadTestCases(testCasesDir);
        System.out.println("📂 Đã nạp thành công " + testCases.size() + " test cases từ: " + testCasesDir.toAbsolutePath());
        for (TestCase tc : testCases) {
            String inSnippet = tc.getInput().replace("\n", " ").trim();
            String outSnippet = tc.getExpectedOutput().replace("\n", " ").trim();
            System.out.printf("   • Test #%d: Input=[%s] -> Expected=[%s]%n", tc.getId(), inSnippet, outSnippet);
        }
        System.out.println();

        // 2. Nạp danh sách file bài nộp từ test_data/sum_ab/submissions
        List<Path> submissionFiles = loadSubmissions(submissionsDir);
        System.out.println("👥 Tìm thấy " + submissionFiles.size() + " bài nộp sinh viên trong: " + submissionsDir.toAbsolutePath());
        for (Path p : submissionFiles) {
            System.out.println("   • " + p.getFileName());
        }
        System.out.println();

        // 3. Khởi tạo JudgeEngine với 4 Workers song song
        int workerCount = 4;
        JudgeEngine engine = new JudgeEngine(workerCount);

        CountDownLatch latch = new CountDownLatch(submissionFiles.size());
        Map<Long, SubmissionRecord> recordMap = new ConcurrentHashMap<>();

        // 4. Đăng ký Callback lắng nghe kết quả chấm
        engine.setOnJudgeCompletedListener((task, result) -> {
            long queueWaitTime = System.currentTimeMillis() - task.getSubmittedAt();
            String threadName = Thread.currentThread().getName();

            SubmissionRecord record = recordMap.get(task.getSubmissionId());
            if (record != null) {
                record.result = result;
                record.workerThread = threadName;
                record.queueWaitTimeMs = queueWaitTime;
            }

            System.out.println("------------------------------------------------------------------------");
            System.out.printf("🔔 [CALLBACK] Hoàn tất bài nộp: %s%n", record != null ? record.fileName : "#" + task.getSubmissionId());
            System.out.printf("   ⚙️  Xử lý bởi luồng      : %s%n", threadName);
            System.out.printf("   ⏱️  Thời gian chờ hàng đợi: %d ms%n", queueWaitTime);
            System.out.printf("   🏁 Verdict               : %s%n", result.getVerdict());
            System.out.printf("   🎯 Điểm số               : %d/100 (%d/%d tests pass)%n",
                    result.getScore(), result.getPassedTests(), result.getTotalTests());
            System.out.printf("   ⌛ Max Execution Time    : %d ms%n", result.getMaxTimeMs());
            if (result.getVerdict() == VerdictType.CE) {
                String firstErrLine = result.getCompileError().trim().lines().findFirst().orElse("");
                System.out.printf("   ❌ Compiler Error        : %s%n", firstErrLine);
            }
            System.out.println("------------------------------------------------------------------------");

            latch.countDown();
        });

        // Khởi động Engine
        engine.start();
        System.out.println("⚡ JudgeEngine đã sẵn sàng với " + engine.getWorkerCount() + " workers trong pool.\n");

        // 5. Đẩy đồng thời toàn bộ bài nộp vào Queue
        System.out.println("📥 Đang đẩy đồng thời " + submissionFiles.size() + " bài nộp vào JudgeQueue...");
        long subIdCounter = 1;
        for (Path subFile : submissionFiles) {
            try {
                String code = Files.readString(subFile);
                SubmissionRecord rec = new SubmissionRecord(subIdCounter, subFile.getFileName().toString(), code);
                recordMap.put(subIdCounter, rec);

                // Timeout mỗi test là 1000ms
                SubmissionTask task = new SubmissionTask(subIdCounter, 1, code, testCases, 1000);
                engine.submit(task);
                subIdCounter++;
            } catch (IOException e) {
                System.err.println("❌ Lỗi nạp file " + subFile + ": " + e.getMessage());
                latch.countDown();
            }
        }

        System.out.println("⏳ Đang chờ toàn bộ bài nộp được xử lý song song trong Worker Pool...\n");
        boolean completed = latch.await(20, TimeUnit.SECONDS);

        if (!completed) {
            System.err.println("⚠️ Cảnh báo: Quá thời gian chờ hoàn thành!");
        }

        // 6. Bảng tổng kết và đối chiếu với kỳ vọng Giai đoạn 4
        printSummaryReport(recordMap, testCases.size());

        // 7. Dừng an toàn Engine
        System.out.println("🛑 Đang đóng JudgeEngine...");
        engine.shutdown();
        System.out.println("✅ JudgeEngine đã shutdown thành công!");
    }

    private static void printSummaryReport(Map<Long, SubmissionRecord> recordMap, int totalTests) {
        System.out.println("\n==========================================================================================================");
        System.out.println("📊 BẢNG TỔNG KẾT KẾT QUẢ KIỂM THỬ ĐỘC LẬP JUDGE ENGINE (GIAI ĐOẠN 4)");
        System.out.println("==========================================================================================================");
        System.out.printf("%-24s | %-14s | %-8s | %-8s | %-12s | %-10s | %-10s%n",
                "File Bài Nộp", "Worker Thread", "Verdict", "Điểm", "Passed Tests", "Max Exec", "Queue Time");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        List<SubmissionRecord> sortedList = new ArrayList<>(recordMap.values());
        sortedList.sort(Comparator.comparing(r -> r.fileName));

        for (SubmissionRecord rec : sortedList) {
            if (rec.result != null) {
                System.out.printf("%-24s | %-14s | %-8s | %3d/100  |    %d/%d tests   | %5d ms   | %5d ms%n",
                        rec.fileName,
                        rec.workerThread,
                        rec.result.getVerdict(),
                        rec.result.getScore(),
                        rec.result.getPassedTests(),
                        rec.result.getTotalTests(),
                        rec.result.getMaxTimeMs(),
                        rec.queueWaitTimeMs);
            }
        }
        System.out.println("==========================================================================================================\n");

        // Đối chiếu chi tiết với kịch bản Giai đoạn 4
        System.out.println("🔍 ĐỐI CHIẾU VỚI KỊCH BẢN NGHIỆM THU GIAI ĐOẠN 4:");
        for (SubmissionRecord rec : sortedList) {
            if (rec.result == null) continue;
            VerdictType v = rec.result.getVerdict();
            String name = rec.fileName;
            boolean pass = false;

            if (name.contains("ChuanAC") && v == VerdictType.AC && rec.result.getScore() == 100) {
                pass = true;
            } else if (name.contains("SaiSoAm") && v == VerdictType.WA && rec.result.getScore() > 0) {
                pass = true;
            } else if (name.contains("LapVoHan") && v == VerdictType.TLE) {
                pass = true;
            } else if (name.contains("ChiaChoKhong") && v == VerdictType.RE) {
                pass = true;
            } else if (name.contains("LoiCuPhap") && v == VerdictType.CE && rec.result.getScore() == 0) {
                pass = true;
            }

            if (pass) {
                System.out.printf("  ✅ [%-22s] ĐẠT KỲ VỌNG: %s (Điểm: %d/100)%n", name, v, rec.result.getScore());
            } else {
                System.err.printf("  ❌ [%-22s] CHƯA ĐẠT: Nhận %s (Điểm: %d/100)%n", name, v, rec.result.getScore());
            }
        }
        System.out.println();
    }

    private static List<TestCase> loadTestCases(Path dir) {
        List<TestCase> list = new ArrayList<>();
        int id = 1;
        while (true) {
            Path inFile = dir.resolve("test" + id + ".in");
            Path outFile = dir.resolve("test" + id + ".out");
            if (!Files.exists(inFile) || !Files.exists(outFile)) {
                break;
            }
            try {
                String in = Files.readString(inFile);
                String out = Files.readString(outFile);
                list.add(new TestCase(id, in, out));
                id++;
            } catch (IOException e) {
                break;
            }
        }
        return list;
    }

    private static List<Path> loadSubmissions(Path dir) {
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static class SubmissionRecord {
        final long id;
        final String fileName;
        final String sourceCode;
        volatile JudgeResult result;
        volatile String workerThread = "N/A";
        volatile long queueWaitTimeMs = 0;

        SubmissionRecord(long id, String fileName, String sourceCode) {
            this.id = id;
            this.fileName = fileName;
            this.sourceCode = sourceCode;
        }
    }
}
