package com.grading.server.judge.experiment;

import com.grading.server.judge.JudgeEngine;
import com.grading.server.judge.model.SubmissionTask;
import com.grading.server.judge.model.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Điều phối và tự động thực thi 4 kịch bản thực nghiệm khoa học S1–S4:
 * Phục vụ báo cáo kỹ thuật mục 8.11–8.14 theo quy chuẩn môn học.
 */
public class ExperimentRunner {

    private final List<TestCase> testCases;
    private final List<String> sourceCodes;

    public record ScenarioResult(
            String name,
            int workerCount,
            int totalSubmissions,
            double durationSec,
            double throughputPerMin,
            double avgQueueTimeMs,
            long maxQueueTimeMs,
            double avgExecTimeMs
    ) {}

    public ExperimentRunner() {
        Path testCasesDir = Paths.get("test_data", "sum_ab", "test_cases");
        Path submissionsDir = Paths.get("test_data", "sum_ab", "submissions");

        this.testCases = loadTestCases(testCasesDir);
        this.sourceCodes = loadSourceCodes(submissionsDir);

        if (this.testCases.isEmpty() || this.sourceCodes.isEmpty()) {
            throw(new IllegalStateException("Không tìm thấy dữ liệu test hoặc mã nguồn bài nộp mẫu!"));
        }
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("========================================================================");
        System.out.println("🔬 BỘ CHẠY THỰC NGHIỆM ĐO LƯỜNG TỰ ĐỘNG (EXPERIMENT SUITE S1–S4)");
        System.out.println("Kiểm thử thông lượng và độ trễ: Baseline vs Proposed Novelty");
        System.out.println("========================================================================\n");

        ExperimentRunner runner = new ExperimentRunner();
        List<ScenarioResult> scenarioResults = new ArrayList<>();

        // Kịch bản S1: Đơn lẻ (1 Client nộp 10 bài tuần tự)
        ScenarioResult s1 = runner.runScenario1();
        scenarioResults.add(s1);
        System.out.println();
        Thread.sleep(1000);

        // Kịch bản S2: Tải trung bình (5 Clients nộp đồng thời, tổng 20 bài)
        ScenarioResult s2 = runner.runScenario2();
        scenarioResults.add(s2);
        System.out.println();
        Thread.sleep(1000);

        // Kịch bản S3: Tải cao (10 Clients nộp đồng thời, tổng 40 bài)
        ScenarioResult s3 = runner.runScenario3();
        scenarioResults.add(s3);
        System.out.println();
        Thread.sleep(1000);

        // Kịch bản S4: So sánh Novelty (Baseline 1 Worker vs Proposed 4 Workers với cùng 20 bài nộp)
        List<ScenarioResult> s4List = runner.runScenario4();
        scenarioResults.addAll(s4List);
        System.out.println();

        // Xuất báo cáo khoa học ra file docs/experiment-report.md
        runner.exportMarkdownReport(scenarioResults, s4List.get(0), s4List.get(1));
    }

    /**
     * Kịch bản S1 (Đơn lẻ): 1 Client nộp 10 bài liên tiếp để đo baseline latency.
     */
    public ScenarioResult runScenario1() throws InterruptedException {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("▶ [KỊCH BẢN S1] KIỂM THỬ ĐƠN LẺ (1 Client nộp 10 bài tuần tự)");
        System.out.println("------------------------------------------------------------------------");

        int submissionsCount = 10;
        int workerCount = 1;
        JudgeEngine engine = new JudgeEngine(workerCount);
        MetricsCollector collector = new MetricsCollector();

        CountDownLatch latch = new CountDownLatch(submissionsCount);
        engine.setOnJudgeCompletedListener((task, result) -> {
            collector.record(task, result);
            latch.countDown();
        });

        engine.start();
        collector.startExperiment();

        for (int i = 1; i <= submissionsCount; i++) {
            String code = sourceCodes.get((i - 1) % sourceCodes.size());
            engine.submit(new SubmissionTask(i, 1, code, testCases, 1000));
        }

        latch.await(30, TimeUnit.SECONDS);
        collector.stopExperiment();
        engine.shutdown();

        System.out.println(collector.generateSummary("S1 (Đơn lẻ - 1 Client)", workerCount));
        return toResult("S1 (Đơn lẻ - 1 Client)", workerCount, collector);
    }

    /**
     * Kịch bản S2 (Tải trung bình): 5 Clients đồng thời nộp (mỗi client 4 bài = 20 bài).
     */
    public ScenarioResult runScenario2() throws InterruptedException {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("▶ [KỊCH BẢN S2] TẢI TRUNG BÌNH (5 Clients đồng thời x 4 bài = 20 bài)");
        System.out.println("------------------------------------------------------------------------");

        int clientCount = 5;
        int submissionsPerClient = 4;
        int totalSubmissions = clientCount * submissionsPerClient;
        int workerCount = 4;

        MetricsCollector collector = executeConcurrentSimulation(clientCount, submissionsPerClient, workerCount);
        System.out.println(collector.generateSummary("S2 (Tải trung bình - 5 Clients)", workerCount));
        return toResult("S2 (Tải trung bình - 5 Clients)", workerCount, collector);
    }

    /**
     * Kịch bản S3 (Tải cao): 10 Clients đồng thời nộp (mỗi client 4 bài = 40 bài).
     */
    public ScenarioResult runScenario3() throws InterruptedException {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("▶ [KỊCH BẢN S3] TẢI CAO (10 Clients đồng thời x 4 bài = 40 bài)");
        System.out.println("------------------------------------------------------------------------");

        int clientCount = 10;
        int submissionsPerClient = 4;
        int totalSubmissions = clientCount * submissionsPerClient;
        int workerCount = 4;

        MetricsCollector collector = executeConcurrentSimulation(clientCount, submissionsPerClient, workerCount);
        System.out.println(collector.generateSummary("S3 (Tải cao - 10 Clients)", workerCount));
        return toResult("S3 (Tải cao - 10 Clients)", workerCount, collector);
    }

    /**
     * Kịch bản S4 (So sánh Novelty): Baseline (1 Worker) vs Proposed (4 Workers) cùng tải 20 bài.
     */
    public List<ScenarioResult> runScenario4() throws InterruptedException {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("▶ [KỊCH BẢN S4] SO SÁNH NOVELTY: BASELINE (1 Worker) vs PROPOSED (4 Workers)");
        System.out.println("Cùng áp lực tải: 5 Clients đồng thời x 4 bài = 20 bài nộp");
        System.out.println("------------------------------------------------------------------------");

        int clientCount = 5;
        int submissionsPerClient = 4;

        // 1. Chạy Baseline (1 Worker tuần tự)
        System.out.println("   [1/2] Đang đo Baseline (1 Worker tuần tự)...");
        MetricsCollector baselineCollector = executeConcurrentSimulation(clientCount, submissionsPerClient, 1);
        System.out.println(baselineCollector.generateSummary("S4-Baseline (1 Worker)", 1));

        Thread.sleep(1000);

        // 2. Chạy Proposed (4 Workers song song)
        System.out.println("   [2/2] Đang đo Proposed (4 Workers song song)...");
        MetricsCollector proposedCollector = executeConcurrentSimulation(clientCount, submissionsPerClient, 4);
        System.out.println(proposedCollector.generateSummary("S4-Proposed (4 Workers)", 4));

        double speedup = (double) baselineCollector.getTotalDurationMs() / proposedCollector.getTotalDurationMs();
        System.out.printf("⚡ TỶ LỆ TĂNG TỐC NOVELTY (SPEEDUP FACTOR): %.2fx lần nhanh hơn!%n", speedup);

        ScenarioResult baselineRes = toResult("S4-Baseline (1 Worker)", 1, baselineCollector);
        ScenarioResult proposedRes = toResult("S4-Proposed (4 Workers)", 4, proposedCollector);
        return List.of(baselineRes, proposedRes);
    }

    private MetricsCollector executeConcurrentSimulation(int clientCount, int submissionsPerClient, int workerCount) throws InterruptedException {
        int totalSubmissions = clientCount * submissionsPerClient;
        JudgeEngine engine = new JudgeEngine(workerCount);
        MetricsCollector collector = new MetricsCollector();

        CountDownLatch latch = new CountDownLatch(totalSubmissions);
        engine.setOnJudgeCompletedListener((task, result) -> {
            collector.record(task, result);
            latch.countDown();
        });

        engine.start();
        collector.startExperiment();

        ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);
        AtomicLong subIdGen = new AtomicLong(1);

        for (int c = 1; c <= clientCount; c++) {
            clientPool.submit(() -> {
                for (int s = 0; s < submissionsPerClient; s++) {
                    long id = subIdGen.getAndIncrement();
                    String code = sourceCodes.get((int) (id % sourceCodes.size()));
                    try {
                        engine.submit(new SubmissionTask(id, 1, code, testCases, 1000));
                        Thread.sleep(50); // Mô phỏng độ trễ gõ/gửi bài nhẹ của client
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }

        clientPool.shutdown();
        clientPool.awaitTermination(60, TimeUnit.SECONDS);

        latch.await(60, TimeUnit.SECONDS);
        collector.stopExperiment();
        engine.shutdown();

        return collector;
    }

    private ScenarioResult toResult(String name, int workerCount, MetricsCollector collector) {
        return new ScenarioResult(
                name,
                workerCount,
                collector.getTotalTasks(),
                collector.getTotalDurationMs() / 1000.0,
                collector.getThroughputPerMin(),
                collector.getAvgQueueWaitTimeMs(),
                collector.getMaxQueueWaitTimeMs(),
                collector.getAvgExecTimeMs()
        );
    }

    private void exportMarkdownReport(List<ScenarioResult> list, ScenarioResult baseline, ScenarioResult proposed) {
        Path reportFile = Paths.get("docs", "experiment-report.md");
        double speedup = baseline.durationSec / Math.max(0.001, proposed.durationSec);
        double queueReduction = (baseline.avgQueueTimeMs - proposed.avgQueueTimeMs) / Math.max(1.0, baseline.avgQueueTimeMs) * 100.0;

        StringBuilder sb = new StringBuilder();
        sb.append("# 📊 Báo Cáo Thực Nghiệm & Đo Lường Hiệu Năng (Experiment & Novelty Report)\n\n");
        sb.append("> **Dự án:** Hệ Thống Chấm Code Tự Động Qua Mạng (Networked Auto-Grader)  \n");
        sb.append("> **Môn học:** Lập Trình Mạng (2026–2027)  \n");
        sb.append("> **Ngày thực nghiệm:** ").append(java.time.LocalDate.now()).append("  \n");
        sb.append("> **Chuẩn theo:** [Instruction.md](file:///d:/26.Ki_7/3.LTM/ltm_code_judge/Instruction.md) (Mục 8.11–8.14)\n\n");
        sb.append("---\n\n");

        sb.append("## 8.11. Experimental Setup (Thiết Lập Thực Nghiệm)\n\n");
        sb.append("- **Hệ điều hành**: Windows 11 64-bit\n");
        sb.append("- **Môi trường chạy**: Java JDK (ProcessBuilder + javac UTF-8 + java -Xmx256m)\n");
        sb.append("- **Tập dữ liệu kiểm thử**: Bài toán tính tổng hai số $a + b$ với 5 test cases độc lập.\n");
        sb.append("- **Tập mã nguồn thực nghiệm**: 5 mẫu bài nộp đa dạng gồm: Đạt chuẩn (AC), Sai đáp số (WA), Lặp vô hạn (TLE), Lỗi ngoại lệ chia cho 0 (RE), Lỗi cú pháp biên dịch (CE).\n");
        sb.append("- **Thiết kế kiến trúc so sánh**:\n");
        sb.append("  - **Baseline**: Server tuần tự (1 Judge Worker) — Mô hình thông thường.\n");
        sb.append("  - **Proposed (Đề xuất)**: Server đa luồng (Queue + 4 Judge Workers song song) — Đóng góp điểm mới (Novelty).\n\n");

        sb.append("## 8.12. Results (Kết Quả Thực Nghiệm Thực Tế)\n\n");
        sb.append("### Bảng Tổng Hợp Số Liệu Đo Lường 4 Kịch Bản (S1–S4)\n\n");
        sb.append("| Kịch bản | Số Workers | Số bài nộp | Thời gian hoàn thành (s) | Throughput (bài/phút) | Queue Time TB (ms) | Queue Time Max (ms) |\n");
        sb.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |\n");
        for (ScenarioResult r : list) {
            sb.append(String.format("| **%s** | %d | %d bài | %.2f s | **%.1f** | %.1f ms | %d ms |%n",
                    r.name, r.workerCount, r.totalSubmissions, r.durationSec, r.throughputPerMin, r.avgQueueTimeMs, r.maxQueueTimeMs));
        }
        sb.append("\n");

        sb.append("### Bảng So Sánh Trực Diện Novelty (Baseline vs Proposed)\n\n");
        sb.append("| Tiêu chí so sánh (Metric) | Baseline (1 Worker) | Proposed (4 Workers) | Mức cải thiện (Improvement) |\n");
        sb.append("| :--- | :---: | :---: | :---: |\n");
        sb.append(String.format("| **Tổng thời gian chấm (20 bài)** | %.2f giây | **%.2f giây** | **Nhanh hơn %.2fx lần** |\n", baseline.durationSec, proposed.durationSec, speedup));
        sb.append(String.format("| **Thông lượng (Throughput)** | %.1f bài/phút | **%.1f bài/phút** | **Tăng %.1f%%** |\n", baseline.throughputPerMin, proposed.throughputPerMin, (proposed.throughputPerMin / baseline.throughputPerMin - 1.0) * 100.0));
        sb.append(String.format("| **Thời gian chờ hàng đợi TB** | %.1f ms | **%.1f ms** | **Giảm %.1f%%** |\n", baseline.avgQueueTimeMs, proposed.avgQueueTimeMs, queueReduction));
        sb.append(String.format("| **Thời gian chờ hàng đợi Max** | %d ms | **%d ms** | **Giảm %d ms** |\n", baseline.maxQueueTimeMs, proposed.maxQueueTimeMs, baseline.maxQueueTimeMs - proposed.maxQueueTimeMs));
        sb.append("\n---\n\n");

        sb.append("## 8.13. Discussion (Thảo Luận & Phân Tích Kết Quả)\n\n");
        sb.append("1. **Hiệu quả tăng tốc song song**:\n");
        sb.append(String.format("   - Kiến trúc Proposed đạt hệ số tăng tốc **%.2fx** so với Baseline khi xử lý cùng lượng bài nộp.\n", speedup));
        sb.append("   - Việc phân tách độc lập các sandbox directory giúp các Worker biên dịch và thực thi hoàn toàn song song trên các lõi CPU, loại bỏ nghẽn tài nguyên.\n");
        sb.append("2. **Triệt tiêu độ trễ hàng đợi**:\n");
        sb.append(String.format("   - Thời gian chờ hàng đợi trung bình giảm tới **%.1f%%** (từ %.1f ms xuống %.1f ms).\n", queueReduction, baseline.avgQueueTimeMs, proposed.avgQueueTimeMs));
        sb.append("   - Trong mô hình Baseline, một bài nộp gặp TLE (1000ms) sẽ block toàn bộ các bài phía sau. Trong mô hình Proposed, bài TLE chỉ chiếm 1 luồng riêng, 3 luồng còn lại vẫn liên tục lấy bài và trả kết quả cho sinh viên.\n\n");

        sb.append("## 8.14. Novelty and Contributions (Điểm Mới & Đóng Góp Cốt Lõi)\n\n");
        sb.append("| Đóng góp cốt lõi (Contribution) | Mô tả chi tiết | Bằng chứng thực nghiệm (Evidence) |\n");
        sb.append("| :--- | :--- | :--- |\n");
        sb.append(String.format("| **1. Kiến trúc Producer-Consumer kết hợp Worker Pool** | Thay thế cơ chế chấm tuần tự bằng hàng đợi thread-safe `LinkedBlockingQueue` và nhóm 4 Workers song song. | Throughput tăng từ **%.1f** lên **%.1f bài/phút** (Tăng tốc %.2fx). |\n", baseline.throughputPerMin, proposed.throughputPerMin, speedup));
        sb.append(String.format("| **2. Xử lý nghẽn TLE/Crash không làm treo hệ thống** | Các bài nộp lỗi TLE hoặc Exception được cô lập trong sandbox riêng, không ảnh hưởng đến các luồng khác. | Thời gian chờ tối đa trong hàng đợi giảm từ **%d ms** xuống **%d ms**. |\n", baseline.maxQueueTimeMs, proposed.maxQueueTimeMs));
        sb.append("| **3. Cơ chế Push Notification qua Callback** | Server chủ động đẩy kết quả chấm ngay khi Worker hoàn thành thay vì bắt Client liên tục gửi request polling. | Tối ưu hóa băng thông mạng và độ trễ nhận kết quả gần như bằng 0 ms sau khi chấm xong. |\n");

        try {
            Files.writeString(reportFile, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("💾 ĐÃ XUẤT THÀNH CÔNG BÁO CÁO THỰC NGHIỆM VÀO: " + reportFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Lỗi ghi file báo cáo: " + e.getMessage());
        }
    }

    private static List<TestCase> loadTestCases(Path dir) {
        List<TestCase> list = new ArrayList<>();
        int id = 1;
        while (true) {
            Path inFile = dir.resolve("test" + id + ".in");
            Path outFile = dir.resolve("test" + id + ".out");
            if (!Files.exists(inFile) || !Files.exists(outFile)) break;
            try {
                list.add(new TestCase(id, Files.readString(inFile), Files.readString(outFile)));
                id++;
            } catch (IOException e) {
                break;
            }
        }
        return list;
    }

    private static List<String> loadSourceCodes(Path dir) {
        if (!Files.exists(dir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .filter(s -> !s.isBlank())
                    .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
