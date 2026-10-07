package com.grading.server.judge;

import com.grading.server.judge.model.CompileResult;
import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.RunResult;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Demo chấm bài hàng loạt cho 1 bài toán (a + b) với 5 test cases từ file riêng
 * và nhiều bài làm sinh viên từ các file riêng biệt.
 */
public class BatchJudgeDemo {

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        Path testCasesDir = Paths.get("test_data", "sum_ab", "test_cases");
        Path submissionsDir = Paths.get("test_data", "sum_ab", "submissions");

        System.out.println("========================================================================");
        System.out.println("🚀 HỆ THỐNG CHẤM CODE TỰ ĐỘNG - DEMO CHẤM HÀNG LOẠT (BATCH JUDGE)");
        System.out.println("Bài toán: Tính tổng hai số a + b");
        System.out.println("========================================================================\n");

        // 1. Tải danh sách 5 test cases từ thư mục file riêng
        List<TestCase> testCases = loadTestCases(testCasesDir);
        System.out.println("📂 Đã nạp thành công " + testCases.size() + " test cases từ: " + testCasesDir.toAbsolutePath());
        for (TestCase tc : testCases) {
            String inSnippet = tc.getInput().replace("\n", " ").trim();
            String outSnippet = tc.getExpectedOutput().replace("\n", " ").trim();
            System.out.printf("   • Test #%d: Input=[%s] -> Expected Output=[%s]%n", tc.getId(), inSnippet, outSnippet);
        }
        System.out.println();

        // 2. Tải danh sách các file bài nộp của sinh viên
        List<Path> submissionFiles = loadSubmissions(submissionsDir);
        System.out.println("👥 Tìm thấy " + submissionFiles.size() + " bài nộp trong: " + submissionsDir.toAbsolutePath() + "\n");

        // 3. Khởi tạo SandboxRunner và bắt đầu chấm từng bài
        SandboxRunner runner = new SandboxRunner();
        List<JudgeSummary> summaries = new ArrayList<>();

        int subIndex = 1;
        for (Path subFile : submissionFiles) {
            String fileName = subFile.getFileName().toString();
            System.out.println("------------------------------------------------------------------------");
            System.out.printf("📝 [%d/%d] BẮT ĐẦU CHẤM BÀI NỘP: %s%n", subIndex++, submissionFiles.size(), fileName);
            System.out.println("------------------------------------------------------------------------");

            JudgeSummary summary = judgeSingleSubmission(runner, subFile, testCases);
            summaries.add(summary);
            System.out.println();
        }

        // 4. Bảng tổng kết kết quả toàn bộ sinh viên
        printFinalReport(summaries);
    }

    private static JudgeSummary judgeSingleSubmission(SandboxRunner runner, Path subFile, List<TestCase> testCases) {
        String fileName = subFile.getFileName().toString();
        Path sandboxDir = Paths.get("temp", "sandbox", "sub_" + System.currentTimeMillis() + "_" + fileName.replace(".java", ""));

        try {
            String sourceCode = Files.readString(subFile);
            String className = SandboxRunner.extractClassName(sourceCode);
            System.out.println("   ⚙️  Nhận diện Class Name : " + className);
            System.out.print("   🔨 Đang biên dịch javac  : ");

            // Bước 1: Biên dịch
            CompileResult compileResult = runner.compile(sandboxDir, className, sourceCode);
            if (!compileResult.isSuccess()) {
                System.out.println("❌ THẤT BẠI (Compile Error)");
                String errorSummary = compileResult.getErrorMsg().trim().lines().findFirst().orElse("Syntax Error");
                System.out.println("      Chi tiết lỗi compiler: " + errorSummary);
                System.out.println("   🏁 KẾT QUẢ CUỐI CÙNG     : CE | Điểm: 0/100 (0/" + testCases.size() + " tests)");
                return new JudgeSummary(fileName, VerdictType.CE, 0, 0, testCases.size(), 0);
            }
            System.out.println("✅ THÀNH CÔNG");

            // Bước 2: Chạy đầy đủ 100% test cases (Chế độ 1)
            int passedCount = 0;
            long maxTimeMs = 0;
            VerdictType finalVerdict = VerdictType.AC;

            for (TestCase tc : testCases) {
                System.out.printf("   ▶  Chạy Test %d/%d... ", tc.getId(), testCases.size());
                RunResult runResult = runner.run(sandboxDir, className, tc, 1000); // 1000ms timeout
                long timeMs = runResult.getTimeMs();
                maxTimeMs = Math.max(maxTimeMs, timeMs);

                VerdictType v = runResult.getVerdict();
                if (v == VerdictType.AC) {
                    passedCount++;
                    System.out.printf("✅ AC (%d ms)%n", timeMs);
                } else if (v == VerdictType.WA) {
                    if (finalVerdict == VerdictType.AC) finalVerdict = VerdictType.WA;
                    String actualSnippet = runResult.getStdout().replace("\n", " ").trim();
                    String expectedSnippet = tc.getExpectedOutput().replace("\n", " ").trim();
                    System.out.printf("❌ WA (%d ms) [Thực tế: '%s' | Kỳ vọng: '%s']%n", timeMs, actualSnippet, expectedSnippet);
                } else if (v == VerdictType.TLE) {
                    if (finalVerdict == VerdictType.AC) finalVerdict = VerdictType.TLE;
                    System.out.printf("⏳ TLE (> %d ms - Quá thời gian quy định)%n", timeMs);
                } else if (v == VerdictType.RE) {
                    if (finalVerdict == VerdictType.AC) finalVerdict = VerdictType.RE;
                    String errLine = runResult.getStderr().lines().findFirst().orElse("Crash");
                    System.out.printf("💥 RE (%d ms) [Lỗi: %s]%n", timeMs, errLine);
                }
            }

            int score = (int) Math.round((double) passedCount / testCases.size() * 100.0);
            System.out.printf("   🏁 KẾT QUẢ CUỐI CÙNG     : %s | Điểm: %d/100 (%d/%d tests) | Thời gian tối đa: %d ms%n",
                    finalVerdict, score, passedCount, testCases.size(), maxTimeMs);

            return new JudgeSummary(fileName, finalVerdict, score, passedCount, testCases.size(), maxTimeMs);

        } catch (IOException e) {
            System.err.println("   ❌ Lỗi đọc file: " + e.getMessage());
            return new JudgeSummary(fileName, VerdictType.RE, 0, 0, testCases.size(), 0);
        } finally {
            runner.cleanup(sandboxDir);
        }
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

    private static void printFinalReport(List<JudgeSummary> list) {
        System.out.println("\n========================================================================");
        System.out.println("📊 BẢNG TỔNG KẾT KẾT QUẢ CHẤM BÀI CỦA TẤT CẢ SINH VIÊN");
        System.out.println("========================================================================");
        System.out.printf("%-26s | %-8s | %-8s | %-12s | %-10s%n", "File Bài Nộp", "Verdict", "Điểm", "Passed Tests", "Max Time");
        System.out.println("------------------------------------------------------------------------");
        for (JudgeSummary s : list) {
            System.out.printf("%-26s | %-8s | %3d/100  |    %d/%d tests   | %4d ms%n",
                    s.fileName, s.verdict, s.score, s.passed, s.total, s.maxTimeMs);
        }
        System.out.println("========================================================================\n");
    }

    private record JudgeSummary(String fileName, VerdictType verdict, int score, int passed, int total, long maxTimeMs) {}
}
