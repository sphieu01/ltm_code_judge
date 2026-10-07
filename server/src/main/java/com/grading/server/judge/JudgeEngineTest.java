package com.grading.server.judge;

import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Kiểm thử tích hợp đa luồng cho JudgeEngine, JudgeQueue và JudgeWorker Pool.
 */
public class JudgeEngineTest {

    public static void main(String[] args) throws InterruptedException {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("========================================================================");
        System.out.println("🚀 KIỂM THỬ ĐA LUỒNG: JUDGE ENGINE - QUEUE & WORKER POOL");
        System.out.println("========================================================================\n");

        int workerCount = 4;
        JudgeEngine engine = new JudgeEngine(workerCount);

        List<TestCase> testCases = List.of(
                new TestCase(1, "2 3", "5"),
                new TestCase(2, "10 20", "30")
        );

        int totalSubmissions = 5;
        CountDownLatch latch = new CountDownLatch(totalSubmissions);
        Map<Long, JudgeResult> results = new ConcurrentHashMap<>();

        // Đăng ký callback toàn cục
        engine.setOnJudgeCompletedListener((task, result) -> {
            long waitTimeMs = System.currentTimeMillis() - task.getSubmittedAt();
            results.put(task.getSubmissionId(), result);
            System.out.printf("🔔 [CALLBACK] Hoàn thành bài #%d | Thread: %s | Verdict: %-4s | Điểm: %3d/100 | Queue Time: %d ms%n",
                    task.getSubmissionId(),
                    Thread.currentThread().getName(),
                    result.getVerdict(),
                    result.getScore(),
                    waitTimeMs);
            latch.countDown();
        });

        // Khởi động Engine
        engine.start();
        System.out.println("✅ JudgeEngine đã khởi động với " + engine.getWorkerCount() + " workers song song.\n");

        // Chuẩn bị 5 bài nộp tương ứng 5 kịch bản verdicts
        String codeAC = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextInt() + sc.nextInt());
                    }
                }
                """;

        String codeWA = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        sc.nextInt(); sc.nextInt();
                        System.out.println(999);
                    }
                }
                """;

        String codeTLE = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        while (true) {}
                    }
                }
                """;

        String codeRE = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int a = sc.nextInt();
                        System.out.println(a / 0);
                    }
                }
                """;

        String codeCE = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Syntax Error")
                    }
                }
                """;

        // Đẩy 5 bài đồng thời vào Queue
        System.out.println("📥 Đang đẩy đồng loạt 5 bài nộp vào JudgeQueue...");
        engine.submit(new SubmissionTask(1, 101, codeAC, testCases, 1000));
        engine.submit(new SubmissionTask(2, 101, codeWA, testCases, 1000));
        engine.submit(new SubmissionTask(3, 101, codeTLE, testCases, 1000));
        engine.submit(new SubmissionTask(4, 101, codeRE, testCases, 1000));
        engine.submit(new SubmissionTask(5, 101, codeCE, testCases, 1000));

        System.out.println("⏳ Đang chờ toàn bộ 5 bài chấm xong qua Worker Pool...\n");
        boolean finishedInTime = latch.await(15, TimeUnit.SECONDS);

        if (!finishedInTime) {
            System.err.println("❌ Quá thời gian chờ kiểm thử!");
        } else {
            System.out.println("\n🎉 Toàn bộ bài nộp đã được chấm xong!");
        }

        // Kiểm tra tính chính xác của từng bài
        System.out.println("\n------------------------------------------------------------------------");
        System.out.println("🔍 ĐỐI CHIẾU KẾT QUẢ VỚI KỲ VỌNG:");
        System.out.println("------------------------------------------------------------------------");
        checkAssert("Bài #1 (Chuẩn)", VerdictType.AC, results.get(1L).getVerdict(), results.get(1L).getScore() == 100);
        checkAssert("Bài #2 (Sai đáp án)", VerdictType.WA, results.get(2L).getVerdict(), results.get(2L).getScore() == 0);
        checkAssert("Bài #3 (Lặp vô tận)", VerdictType.TLE, results.get(3L).getVerdict(), true);
        checkAssert("Bài #4 (Chia cho 0)", VerdictType.RE, results.get(4L).getVerdict(), true);
        checkAssert("Bài #5 (Lỗi cú pháp)", VerdictType.CE, results.get(5L).getVerdict(), results.get(5L).getScore() == 0);

        // Dọn dẹp engine
        System.out.println("\n🛑 Đang dừng JudgeEngine...");
        engine.shutdown();
        System.out.println("✅ JudgeEngine đã dừng an toàn!");
    }

    private static void checkAssert(String title, VerdictType expected, VerdictType actual, boolean extraCondition) {
        if (expected == actual && extraCondition) {
            System.out.printf("  ✅ [%s] PASS: Kì vọng %s -> Nhận %s%n", title, expected, actual);
        } else {
            System.err.printf("  ❌ [%s] FAIL: Kì vọng %s -> Nhận %s%n", title, expected, actual);
        }
    }
}
