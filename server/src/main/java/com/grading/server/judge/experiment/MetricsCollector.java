package com.grading.server.judge.experiment;

import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.SubmissionTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thu thập và tính toán các chỉ số thực nghiệm (Metrics):
 * - Thời gian chờ hàng đợi (Queue Waiting Time)
 * - Thời gian thực thi (Execution Time)
 * - Thông lượng (Throughput)
 * - Tỷ lệ tăng tốc (Speedup)
 */
public class MetricsCollector {

    public record TaskMetric(
            long submissionId,
            long submittedAt,
            long completedAt,
            long queueWaitTimeMs,
            long execTimeMs,
            String verdict
    ) {}

    private final ConcurrentLinkedQueue<TaskMetric> records = new ConcurrentLinkedQueue<>();
    private volatile long experimentStartMs = 0;
    private volatile long experimentEndMs = 0;

    public void startExperiment() {
        records.clear();
        experimentStartMs = System.currentTimeMillis();
        experimentEndMs = 0;
    }

    public void record(SubmissionTask task, JudgeResult result) {
        long now = System.currentTimeMillis();
        long queueWaitTime = Math.max(0, now - task.getSubmittedAt() - result.getMaxTimeMs());
        long execTime = result.getMaxTimeMs();

        records.add(new TaskMetric(
                task.getSubmissionId(),
                task.getSubmittedAt(),
                now,
                queueWaitTime,
                execTime,
                result.getVerdict().name()
        ));
    }

    public void stopExperiment() {
        experimentEndMs = System.currentTimeMillis();
    }

    public List<TaskMetric> getRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public long getTotalDurationMs() {
        if (experimentStartMs == 0) return 0;
        long end = experimentEndMs > 0 ? experimentEndMs : System.currentTimeMillis();
        return Math.max(1, end - experimentStartMs);
    }

    public int getTotalTasks() {
        return records.size();
    }

    public double getThroughputPerSec() {
        long durationMs = getTotalDurationMs();
        if (durationMs <= 0 || records.isEmpty()) return 0.0;
        return (double) records.size() / (durationMs / 1000.0);
    }

    public double getThroughputPerMin() {
        return getThroughputPerSec() * 60.0;
    }

    public double getAvgQueueWaitTimeMs() {
        return records.stream().mapToLong(TaskMetric::queueWaitTimeMs).average().orElse(0.0);
    }

    public long getMaxQueueWaitTimeMs() {
        return records.stream().mapToLong(TaskMetric::queueWaitTimeMs).max().orElse(0);
    }

    public double getAvgExecTimeMs() {
        return records.stream().mapToLong(TaskMetric::execTimeMs).average().orElse(0.0);
    }

    public String generateSummary(String scenarioName, int workerCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 KẾT QUẢ THỰC NGHIỆM: %s%n", scenarioName));
        sb.append(String.format("   • Số Workers song song     : %d%n", workerCount));
        sb.append(String.format("   • Tổng số bài nộp          : %d bài%n", getTotalTasks()));
        sb.append(String.format("   • Tổng thời gian hoàn thành: %.2f giây (%d ms)%n", getTotalDurationMs() / 1000.0, getTotalDurationMs()));
        sb.append(String.format("   • Throughput (Thông lượng) : %.2f bài/giây (%.1f bài/phút)%n", getThroughputPerSec(), getThroughputPerMin()));
        sb.append(String.format("   • Queue Waiting Time TB    : %.1f ms (Max: %d ms)%n", getAvgQueueWaitTimeMs(), getMaxQueueWaitTimeMs()));
        sb.append(String.format("   • Execution Time TB        : %.1f ms%n", getAvgExecTimeMs()));
        return sb.toString();
    }
}
