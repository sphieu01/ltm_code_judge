package com.grading.server.judge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SecurityScanner — Bộ lọc bảo mật tĩnh (Static Code Security Scanner) - Tầng 1.
 *
 * Kiểm tra mã nguồn trước khi biên dịch hoặc thực thi:
 * - Chặn các lệnh gọi shell hệ điều hành (system, os.system, exec, subprocess, Runtime.getRuntime)
 * - Chặn thao tác file nhạy cảm và phá hoại ổ đĩa (remove, unlink, rmtree)
 * - Chặn kết nối mạng mở socket ra ngoài (socket, connect, urllib)
 * - Chặn header nguy hiểm can thiệp OS (windows.h, sys/socket.h, unistd.h, filesystem)
 */
public class SecurityScanner {

    private static final Logger logger = LoggerFactory.getLogger(SecurityScanner.class);

    public record SecurityResult(boolean isSafe, String reason, String matchedPattern) {
        public static SecurityResult safe() {
            return new SecurityResult(true, null, null);
        }

        public static SecurityResult violation(String reason, String matchedPattern) {
            return new SecurityResult(false, reason, matchedPattern);
        }
    }

    private static record Rule(Pattern pattern, String reason) {}

    private static final List<Rule> CPP_RULES = new ArrayList<>();
    private static final List<Rule> C_RULES = new ArrayList<>();
    private static final List<Rule> PYTHON_RULES = new ArrayList<>();
    private static final List<Rule> JAVA_RULES = new ArrayList<>();

    static {
        // ─── 1. Quy tắc cho C / C++ ──────────────────────────────────────────
        // Chặn header nguy hiểm can thiệp hệ thống & mạng
        addRule(CPP_RULES, "(?i)#\\s*include\\s*<\\s*(windows\\.h|sys/socket\\.h|sys/types\\.h|unistd\\.h|filesystem|dirent\\.h|arpa/inet\\.h|netinet/in\\.h|conio\\.h|shellapi\\.h)\\s*>",
                "Chặn header hệ thống / mạng bị cấm");

        // Chặn gọi lệnh shell hệ điều hành & tạo tiến trình con
        addRule(CPP_RULES, "(?i)\\b(system|popen|_popen|fork|vfork|execvp|execve|execl|execlp|execle|execv)\\s*\\(",
                "Chặn lệnh gọi shell / tiến trình con của hệ điều hành");

        // Chặn Windows APIs can thiệp tiến trình
        addRule(CPP_RULES, "(?i)\\b(CreateProcess[AW]?|ShellExecute[AW]?|WinExec|TerminateProcess)\\s*\\(",
                "Chặn gọi API can thiệp hệ điều hành");

        // Chặn xóa file hệ thống
        addRule(CPP_RULES, "(?i)\\b(remove|unlink|rmdir)\\s*\\(",
                "Chặn thao tác xóa file / thư mục hệ thống");

        // Chặn mở kết nối mạng
        addRule(CPP_RULES, "(?i)\\b(socket|connect|bind|listen|accept)\\s*\\(",
                "Chặn thao tác kết nối mạng");

        // C dùng chung tập luật với C++
        C_RULES.addAll(CPP_RULES);

        // ─── 2. Quy tắc cho Python ───────────────────────────────────────────
        // Chặn gọi shell & thực thi lệnh hệ thống
        addRule(PYTHON_RULES, "(?i)\\b(os\\.(system|popen|spawn|exec)|subprocess\\.(run|Popen|call|check_output|check_call)|pty\\.spawn)\\b",
                "Chặn module gọi shell / tiến trình hệ thống (os.system, subprocess)");

        // Chặn thực thi mã động nguy hiểm & nạp module ngầm
        addRule(PYTHON_RULES, "\\b(eval|exec|__import__)\\s*\\(",
                "Chặn thực thi mã động hoặc nạp module ngầm (eval, exec, __import__)");

        // Chặn thao tác xóa file & thư mục
        addRule(PYTHON_RULES, "(?i)\\b(shutil\\.(rmtree|move)|os\\.(remove|unlink|rmdir))\\b",
                "Chặn lệnh xóa file / thư mục hệ thống (shutil, os.remove)");

        // Chặn kết nối mạng
        addRule(PYTHON_RULES, "(?i)\\b(socket\\.(socket|create_connection)|urllib\\.(request|parse)|requests\\.(get|post)|http\\.client)\\b",
                "Chặn mở kết nối mạng (socket, urllib, requests)");

        // ─── 3. Quy tắc cho Java ─────────────────────────────────────────────
        // Chặn Runtime exec & ProcessBuilder
        addRule(JAVA_RULES, "\\b(Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder)\\b",
                "Chặn gọi tiến trình con hệ điều hành (Runtime.exec, ProcessBuilder)");

        // Chặn kết nối mạng Socket
        addRule(JAVA_RULES, "\\b(java\\.net\\.Socket|java\\.net\\.ServerSocket|java\\.net\\.URL|java\\.net\\.HttpURLConnection)\\b",
                "Chặn mở kết nối mạng (Socket, ServerSocket, URL)");

        // Chặn xóa file
        addRule(JAVA_RULES, "\\b(java\\.io\\.File|java\\.nio\\.file\\.Files|java\\.nio\\.file\\.Paths)\\b",
                "Chặn can thiệp hệ thống file (java.io.File, java.nio.file.Files)");

        // Chặn cố tình tắt JVM server
        addRule(JAVA_RULES, "\\b(System\\.exit)\\s*\\(",
                "Chặn lệnh tắt tiến trình JVM (System.exit)");

        // ─── 4. Quy tắc phát hiện mã nguồn / chú thích do AI sinh (Generative AI Artifacts) ───
        addAiDetectionRules(CPP_RULES);
        addAiDetectionRules(C_RULES);
        addAiDetectionRules(PYTHON_RULES);
        addAiDetectionRules(JAVA_RULES);
    }

    private static void addAiDetectionRules(List<Rule> list) {
        // Nhãn / tên các mô hình AI phổ biến
        addRule(list, "(?i)\\b(chatgpt|openai|anthropic|claude|gemini|copilot|github\\s+copilot)\\b",
                "Phát hiện dấu vết / nhãn mô hình AI (ChatGPT, Claude, Gemini, Copilot)");

        // Câu mở đầu phản hồi đặc trưng của LLM
        addRule(list, "(?i)\\b(here\\s+is\\s+(the|a)\\s+(complete\\s+|optimized\\s+)?(solution|code|implementation)|here's\\s+(the|a)\\s+(solution|code)|as\\s+an\\s+ai\\s+language\\s+model)\\b",
                "Phát hiện văn phong mở đầu sinh mã tự động của LLM (Here is the solution...)");

        // Chú thích xác nhận sinh mã bởi AI
        addRule(list, "(?i)\\b(generated\\s+by\\s+(an\\s+)?(ai|chatgpt|claude|gemini|copilot))\\b",
                "Phát hiện chú thích sinh mã tự động bởi AI");

        // Ký hiệu khối mã Markdown (`) do sinh viên copy-paste nguyên khối từ chat web
        addRule(list, "(?m)^\\s*`(java|cpp|c\\+\\+|python|c)?\\s*$",
                "Phát hiện ký hiệu khối mã Markdown (`) sao chép từ cửa sổ chat AI");
    }

    private static void addRule(List<Rule> list, String regex, String reason) {
        list.add(new Rule(Pattern.compile(regex), reason));
    }

    /**
     * Quét kiểm tra mã nguồn theo ngôn ngữ lập trình.
     *
     * @param sourceCode mã nguồn sinh viên nộp
     * @param language   mã ngôn ngữ ("c", "cpp", "python", "java")
     * @return SecurityResult (isSafe = true nếu an toàn; isSafe = false nếu phát hiện vi phạm)
     */
    public static SecurityResult scan(String sourceCode, String language) {
        if (sourceCode == null || sourceCode.isBlank() || language == null) {
            return SecurityResult.safe();
        }

        List<Rule> rules = switch (language.toLowerCase()) {
            case "c" -> C_RULES;
            case "cpp" -> CPP_RULES;
            case "python" -> PYTHON_RULES;
            case "java" -> JAVA_RULES;
            default -> List.of();
        };

        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(sourceCode);
            if (matcher.find()) {
                String matchedText = matcher.group();
                String violationMsg = String.format("CẢNH BÁO BẢO MẬT: %s. Phát hiện từ khóa vi phạm: '%s'",
                        rule.reason(), matchedText);
                logger.warn("Security violation detected for language {}: {}", language, violationMsg);
                return SecurityResult.violation(violationMsg, matchedText);
            }
        }

        return SecurityResult.safe();
    }
}
