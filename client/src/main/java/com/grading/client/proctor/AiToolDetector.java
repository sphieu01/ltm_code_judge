package com.grading.client.proctor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * AiToolDetector — Bộ phát hiện các công cụ trí tuệ nhân tạo (ChatGPT, Gemini, Claude, DeepSeek...)
 *
 * Tiêu chí thiết kế:
 * - KHÔNG phạt việc sinh viên chuyển sang IDE ngoài (VS Code, CLion, Dev-C++, Code::Blocks, v.v.).
 * - CHỈ phát hiện và cảnh báo khi sinh viên mở trình duyệt vào ChatGPT / Gemini hoặc các ứng dụng AI.
 * - Kiểm tra chữ ký AI trong mã nguồn khi sinh viên dán (paste) hoặc nộp code.
 */
public class AiToolDetector {

    // Danh sách trình duyệt phổ biến cần kiểm tra tiêu đề tab đang mở
    private static final String[] TARGET_BROWSERS = {
            "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe"
    };

    // Danh sách ứng dụng AI độc lập
    private static final String[] AI_PROCESSES = {
            "chatgpt.exe", "claude.exe", "cursor.exe", "ollama.exe"
    };

    // Từ khóa nhận diện công cụ AI trong tiêu đề cửa sổ / tab trình duyệt
    private static final String[] AI_TITLE_KEYWORDS = {
            "chatgpt", "gemini", "claude", "deepseek", "copilot", "chat.openai", "perplexity", "poe.com"
    };

    // Từ khóa câu thoại / giải thích thường thấy trong câu trả lời từ AI
    private static final String[] AI_PHRASES = {
            "dưới đây là mã nguồn",
            "dưới đây là chương trình",
            "dưới đây là lời giải",
            "dưới đây là code",
            "chắc chắn rồi",
            "here is the complete",
            "here is the solution",
            "here is the code",
            "as an ai language model",
            "i hope this helps",
            "độ phức tạp thời gian",
            "time complexity:",
            "space complexity:"
    };

    private static final Pattern MARKDOWN_CODEBLOCK_PATTERN = Pattern.compile("^\\s*```(?:cpp|c|java|python|c\\+\\+)?", Pattern.MULTILINE);

    private static final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "AiToolDetector-Worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Quét bất đồng bộ hệ thống để phát hiện xem có cửa sổ trình duyệt nào đang mở ChatGPT / Gemini
     * hoặc phần mềm AI hay không.
     *
     * @return CompletableFuture trả về tên công cụ AI phát hiện được (VD: "ChatGPT - Google Chrome"),
     *         hoặc null nếu sinh viên chỉ mở IDE bình thường (VS Code, CLion, Dev-C++...).
     */
    public static CompletableFuture<String> detectRunningAiToolAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (!os.contains("win")) {
                // Môi trường không phải Windows: bỏ qua quét tiến trình native
                return null;
            }

            try {
                // 1. Quét song song các trình duyệt để kiểm tra tiêu đề cửa sổ
                List<Future<String>> browserFutures = new ArrayList<>();
                for (String browser : TARGET_BROWSERS) {
                    browserFutures.add(executor.submit(() -> checkBrowserWindowTitle(browser)));
                }

                // 2. Quét các tiến trình ứng dụng AI độc lập
                List<Future<String>> appFutures = new ArrayList<>();
                for (String app : AI_PROCESSES) {
                    appFutures.add(executor.submit(() -> checkProcessRunning(app)));
                }

                // Chờ kết quả từ trình duyệt
                for (Future<String> f : browserFutures) {
                    try {
                        String res = f.get(2500, TimeUnit.MILLISECONDS);
                        if (res != null && !res.isEmpty()) {
                            return res;
                        }
                    } catch (Exception ignored) {}
                }

                // Chờ kết quả từ ứng dụng AI
                for (Future<String> f : appFutures) {
                    try {
                        String res = f.get(1500, TimeUnit.MILLISECONDS);
                        if (res != null && !res.isEmpty()) {
                            return res;
                        }
                    } catch (Exception ignored) {}
                }

            } catch (Exception ignored) {}

            return null;
        }, executor);
    }

    /**
     * Dùng tasklist truy vấn tiêu đề cửa sổ của một trình duyệt cụ thể.
     * Nhờ có bộ lọc IMAGENAME nên lệnh chạy rất nhanh (~200ms - 350ms).
     */
    private static String checkBrowserWindowTitle(String browserExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tasklist", "/fi", "IMAGENAME eq " + browserExe, "/v", "/fo", "csv"
            );
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    for (String kw : AI_TITLE_KEYWORDS) {
                        if (lower.contains(kw)) {
                            return extractWindowDetail(line, browserExe, kw);
                        }
                    }
                }
            }
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Kiểm tra xem tiến trình ứng dụng AI độc lập (VD: ChatGPT.exe, Cursor.exe) có đang chạy không.
     */
    private static String checkProcessRunning(String appExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tasklist", "/fi", "IMAGENAME eq " + appExe, "/fo", "csv"
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase(Locale.ROOT).contains(appExe.toLowerCase(Locale.ROOT))) {
                        return formatAppName(appExe);
                    }
                }
            }
            process.waitFor(1500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractWindowDetail(String csvLine, String browserExe, String matchedKeyword) {
        String[] parts = csvLine.split("\",\"");
        if (parts.length >= 9) {
            String title = parts[8].replace("\"", "").trim();
            if (!title.isEmpty() && !"N/A".equalsIgnoreCase(title)) {
                return title;
            }
        }
        String browserName = browserExe.replace(".exe", "").toUpperCase();
        return matchedKeyword.toUpperCase() + " (" + browserName + ")";
    }

    private static String formatAppName(String appExe) {
        if (appExe.equalsIgnoreCase("chatgpt.exe")) return "ChatGPT Desktop App";
        if (appExe.equalsIgnoreCase("claude.exe")) return "Claude Desktop App";
        if (appExe.equalsIgnoreCase("cursor.exe")) return "Cursor AI Editor";
        if (appExe.equalsIgnoreCase("ollama.exe")) return "Ollama Local AI";
        return appExe;
    }

    /**
     * Phát hiện dấu hiệu mã nguồn được sao chép nguyên văn từ công cụ AI (ChatGPT/Gemini).
     *
     * @param code Nội dung mã nguồn cần kiểm tra.
     * @return Chuỗi mô tả vi phạm nếu phát hiện, hoặc null nếu bình thường.
     */
    public static String detectAiPastedCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        String trimmed = code.trim();

        // 1. Dấu hiệu khối mã Markdown (```cpp, ```c, ```)
        if (MARKDOWN_CODEBLOCK_PATTERN.matcher(trimmed).find() || trimmed.startsWith("```") || trimmed.endsWith("```")) {
            return "Chứa cú pháp khối mã Markdown (```) từ câu trả lời của AI";
        }

        // 2. Dấu hiệu câu thoại dẫn dắt hoặc giải thích của AI
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String phrase : AI_PHRASES) {
            if (lower.contains(phrase)) {
                return "Chứa câu thoại đặc trưng của AI (\"" + phrase + "\")";
            }
        }

        return null;
    }
}
