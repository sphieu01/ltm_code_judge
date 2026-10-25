package com.grading.client.proctor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * AiToolDetector — Bộ phát hiện các công cụ trí tuệ nhân tạo (ChatGPT, Gemini, Claude, DeepSeek, Copilot...)
 *
 * Tiêu chí thiết kế:
 * - Cho phép sinh viên sử dụng IDE ngoài (VS Code, CLion, Dev-C++, Code::Blocks...) để lập trình bình thường.
 * - Phát hiện khi sinh viên mở trình duyệt vào web AI hoặc mở các AI IDE độc lập (Cursor, Windsurf, Trae...).
 * - Phát hiện khi sinh viên sử dụng VS Code kèm các Extension AI (GitHub Copilot, Tabnine, Codeium, Continue...).
 * - Kiểm tra chữ ký AI trong mã nguồn khi sinh viên dán (paste) hoặc nộp code.
 */
public class AiToolDetector {

    // Danh sách trình duyệt phổ biến cần kiểm tra tiêu đề tab đang mở
    private static final String[] TARGET_BROWSERS = {
            "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe"
    };

    // Danh sách ứng dụng AI độc lập hoặc công cụ AI trên IDE
    private static final String[] AI_PROCESSES = {
            "chatgpt.exe", "claude.exe", "cursor.exe", "ollama.exe",
            "windsurf.exe", "trae.exe", "tabnine.exe", "tabnine-deep-local.exe",
            "copilot-runtime.exe", "copilot-agent.exe", "aider.exe", "codex.exe",
            "gemini.exe", "antigravity ide.exe"
    };

    // Đường dẫn tương đối lưu extension của VS Code
    private static final String[] VSCODE_EXTENSION_PATHS = {
            ".vscode/extensions",
            ".vscode-insiders/extensions",
            ".vscode-oss/extensions",
            ".antigravity-ide/extensions"
    };

    // Tiền tố tên thư mục các extension AI phổ biến trên VS Code
    private static final String[] AI_EXTENSION_PREFIXES = {
            "github.copilot",
            "codeium.codeium",
            "tabnine.tabnine",
            "continue.continue",
            "saoudrizwan.claude-dev",
            "rooveterinaryinc.roo-cline",
            "blackboxapp.blackbox",
            "sourcegraph.cody-ai",
            "genieai.chatgpt",
            "openai.chatgpt"
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

    private static final ExecutorService executor = Executors.newFixedThreadPool(12, r -> {
        Thread t = new Thread(r, "AiToolDetector-Worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Quét siêu tốc qua ProcessHandle native của Java (chỉ tốn ~30-45ms).
     * Phát hiện:
     * - GitHub Copilot tích hợp trong VS Code (copilot-runtime.exe, copilot-agent.exe...)
     * - Các IDE AI chuyên dụng (Cursor, Windsurf, Trae)
     * - Tabnine, Codeium, ChatGPT desktop, Claude desktop, Ollama, Aider...
     */
    private static String checkNativeAiProcesses() {
        return ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse("").toLowerCase(Locale.ROOT))
                .filter(cmd -> !cmd.isEmpty())
                .map(cmd -> {
                    // 1. Tiến trình Copilot trong VS Code (copilot-runtime.exe, copilot-agent.exe...)
                    if (cmd.endsWith("copilot-runtime.exe") || cmd.endsWith("copilot-agent.exe")
                            || (cmd.contains("copilot") && (cmd.contains("code") || cmd.contains("vscode")))) {
                        return "GitHub Copilot (VS Code)";
                    }
                    // 2. Các ứng dụng AI độc lập / AI IDE
                    if (cmd.endsWith("cursor.exe")) return "Cursor AI Editor";
                    if (cmd.endsWith("windsurf.exe")) return "Windsurf AI Editor";
                    if (cmd.endsWith("trae.exe")) return "Trae AI Editor";
                    if (cmd.endsWith("antigravity ide.exe")) return "Antigravity AI IDE";
                    if (cmd.endsWith("codex.exe")) return "OpenAI Codex";
                    if (cmd.endsWith("gemini.exe")) return "Google Gemini CLI";
                    if (cmd.endsWith("tabnine.exe") || cmd.endsWith("tabnine-deep-local.exe")) return "Tabnine AI Completion";
                    if (cmd.endsWith("chatgpt.exe")) return "ChatGPT Desktop App";
                    if (cmd.endsWith("claude.exe")) return "Claude Desktop App";
                    if (cmd.endsWith("ollama.exe")) return "Ollama Local AI";
                    if (cmd.endsWith("aider.exe")) return "Aider AI Agent";
                    // 3. Plugin Codeium language server
                    if (cmd.contains("codeium") && cmd.endsWith(".exe")) return "Codeium AI Extension";
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Quét bất đồng bộ hệ thống để phát hiện xem có cửa sổ trình duyệt nào đang mở ChatGPT / Gemini,
     * ứng dụng AI chuyên dụng (Cursor, Windsurf, Trae...) hoặc VS Code bật AI Extension hay không.
     *
     * @return CompletableFuture trả về chuỗi nhận diện AI phát hiện được,
     *         hoặc null nếu sinh viên chỉ mở IDE bình thường (VS Code sạch, CLion, Dev-C++...).
     */
    public static CompletableFuture<String> detectRunningAiToolAsync() {
        return CompletableFuture.supplyAsync(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (!os.contains("win")) {
                // Môi trường không phải Windows: bỏ qua quét tiến trình native
                return null;
            }

            try {
                // 1. Quét siêu tốc qua ProcessHandle native trước (~40ms)
                String nativeAi = checkNativeAiProcesses();
                if (nativeAi != null) {
                    return nativeAi;
                }

                // 2. Quét song song các trình duyệt để kiểm tra tiêu đề cửa sổ (ChatGPT, Gemini web...)
                List<Future<String>> browserFutures = new ArrayList<>();
                for (String browser : TARGET_BROWSERS) {
                    browserFutures.add(executor.submit(() -> checkBrowserWindowTitle(browser)));
                }

                // 3. Quét các tiến trình ứng dụng AI độc lập qua tasklist
                List<Future<String>> appFutures = new ArrayList<>();
                for (String app : AI_PROCESSES) {
                    appFutures.add(executor.submit(() -> checkProcessRunning(app)));
                }

                // 4. Quét VS Code có kèm extension AI đang hoạt động không
                Future<String> vsCodeAiFuture = executor.submit(AiToolDetector::checkVsCodeAi);

                // Chờ kết quả từ trình duyệt
                for (Future<String> f : browserFutures) {
                    try {
                        String res = f.get(2000, TimeUnit.MILLISECONDS);
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

                // Chờ kết quả từ VS Code AI
                try {
                    String res = vsCodeAiFuture.get(1500, TimeUnit.MILLISECONDS);
                    if (res != null && !res.isEmpty()) {
                        return res;
                    }
                } catch (Exception ignored) {}

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
        if (appExe.equalsIgnoreCase("windsurf.exe")) return "Windsurf AI Editor";
        if (appExe.equalsIgnoreCase("trae.exe")) return "Trae AI Editor";
        if (appExe.equalsIgnoreCase("tabnine.exe") || appExe.equalsIgnoreCase("tabnine-deep-local.exe")) return "Tabnine AI Completion";
        if (appExe.equalsIgnoreCase("copilot-agent.exe")) return "GitHub Copilot Agent";
        if (appExe.equalsIgnoreCase("ollama.exe")) return "Ollama Local AI";
        if (appExe.equalsIgnoreCase("aider.exe")) return "Aider AI Coding Agent";
        if (appExe.equalsIgnoreCase("codex.exe")) return "OpenAI Codex";
        if (appExe.equalsIgnoreCase("gemini.exe")) return "Google Gemini CLI";
        if (appExe.equalsIgnoreCase("antigravity ide.exe")) return "Antigravity AI IDE";
        return appExe;
    }

    /**
     * Kiểm tra xem trong máy có cài đặt extension AI nào cho VS Code hay không.
     * @return Tên tiền tố extension AI đầu tiên tìm thấy, hoặc null nếu không có.
     */
    public static String getInstalledVsCodeAiExtension() {
        String userHome = System.getProperty("user.home", "");
        if (userHome.isEmpty()) return null;

        for (String relPath : VSCODE_EXTENSION_PATHS) {
            File extDir = new File(userHome, relPath);
            if (extDir.exists() && extDir.isDirectory()) {
                File[] files = extDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().toLowerCase(Locale.ROOT);
                        for (String prefix : AI_EXTENSION_PREFIXES) {
                            if (name.startsWith(prefix)) {
                                return prefix;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra xem VS Code có đang mở kèm theo extension AI hay không.
     */
    private static String checkVsCodeAi() {
        String ext = getInstalledVsCodeAiExtension();
        if (ext == null) {
            return null; // Không có extension AI nào trong VS Code
        }
        // Nếu có cài extension AI, kiểm tra các IDE cùng họ VS Code đang chạy.
        if (isProcessRunning("Code.exe") || isProcessRunning("VSCodium.exe")
                || isProcessRunning("Antigravity IDE.exe")) {
            return "VS Code AI Extension (" + ext + ")";
        }
        return null;
    }

    /**
     * Kiểm tra xem một tiến trình cụ thể có đang hoạt động trên hệ thống hay không.
     */
    private static boolean isProcessRunning(String exeName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tasklist", "/fi", "IMAGENAME eq " + exeName, "/fo", "csv"
            );
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase(Locale.ROOT).contains(exeName.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
            }
            process.waitFor(1500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        return false;
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
