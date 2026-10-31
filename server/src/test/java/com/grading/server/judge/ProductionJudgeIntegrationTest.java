package com.grading.server.judge;

import com.grading.server.judge.SandboxRunner.CompileResult;
import com.grading.server.judge.SandboxRunner.RunResult;
import com.grading.server.judge.security.SecurityScanner;
import com.grading.server.judge.security.SecurityScanner.SecurityResult;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductionJudgeIntegrationTest {

    private static SandboxRunner sandboxRunner;

    @BeforeAll
    static void setUp() {
        sandboxRunner = new SandboxRunner();
    }

    // ─── 1. VALID SOLUTIONS (ALL 4 LANGUAGES AC) ─────────────────────────────

    @Test
    @Order(1)
    void testC_ValidSolution_AC() {
        String code = "#include <stdio.h>\nint main() { int a, b; if (scanf(\"%d %d\", &a, &b) == 2) printf(\"%d\\n\", a + b); return 0; }";
        SecurityResult sec = SecurityScanner.scan(code, "c");
        assertTrue(sec.isSafe(), "C code should be safe");

        int subId = 2001;
        CompileResult cRes = sandboxRunner.compile(subId, code, "c");
        assertTrue(cRes.isSuccess(), "C compilation should succeed");

        RunResult rRes = sandboxRunner.run(subId, "c", "15 25\n", 2000);
        assertNull(rRes.getVerdict(), "Should not have TLE or RE");
        assertEquals("40", rRes.getStdout().trim());
        sandboxRunner.cleanup(subId);
    }

    @Test
    @Order(2)
    void testCpp_ValidSolution_AC() {
        String code = "#include <iostream>\nusing namespace std;\nint main() { int a, b; if (cin >> a >> b) cout << (a * b) << endl; return 0; }";
        SecurityResult sec = SecurityScanner.scan(code, "cpp");
        assertTrue(sec.isSafe(), "C++ code should be safe");

        int subId = 2002;
        CompileResult cRes = sandboxRunner.compile(subId, code, "cpp");
        assertTrue(cRes.isSuccess(), "C++ compilation should succeed");

        RunResult rRes = sandboxRunner.run(subId, "cpp", "7 50\n", 2000);
        assertNull(rRes.getVerdict(), "Should not have TLE or RE");
        assertEquals("350", rRes.getStdout().trim());
        sandboxRunner.cleanup(subId);
    }

    @Test
    @Order(3)
    void testPython_ValidSolution_AC() {
        String code = "import sys\na, b = map(int, sys.stdin.read().split())\nprint(a + b)\n";
        SecurityResult sec = SecurityScanner.scan(code, "python");
        assertTrue(sec.isSafe(), "Python code should be safe");

        int subId = 2003;
        CompileResult cRes = sandboxRunner.compile(subId, code, "python");
        assertTrue(cRes.isSuccess(), "Python syntax check should succeed");

        RunResult rRes = sandboxRunner.run(subId, "python", "40 60\n", 2000);
        assertNull(rRes.getVerdict(), "Should not have TLE or RE");
        assertEquals("100", rRes.getStdout().trim());
        sandboxRunner.cleanup(subId);
    }

    @Test
    @Order(4)
    void testJava_ValidSolution_AC() {
        String code = "import java.util.Scanner;\npublic class Solution {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int a = sc.nextInt();\n        int b = sc.nextInt();\n        System.out.println(a * b);\n    }\n}";
        SecurityResult sec = SecurityScanner.scan(code, "java");
        assertTrue(sec.isSafe(), "Java code should be safe");

        int subId = 2004;
        CompileResult cRes = sandboxRunner.compile(subId, code, "java");
        assertTrue(cRes.isSuccess(), "Java compilation should succeed");

        RunResult rRes = sandboxRunner.run(subId, "java", "25 40\n", 2000);
        assertNull(rRes.getVerdict(), "Should not have TLE or RE");
        assertEquals("1000", rRes.getStdout().trim());
        sandboxRunner.cleanup(subId);
    }

    // ─── 2. LAYER 1 SECURITY VIOLATION TESTS ─────────────────────────────────

    @Test
    @Order(5)
    void testC_SecurityViolation_SystemCommand() {
        String code = "#include <stdlib.h>\nint main() { system(\"shutdown /s\"); return 0; }";
        SecurityResult sec = SecurityScanner.scan(code, "c");
        assertFalse(sec.isSafe(), "Malicious C code should be caught by SecurityScanner");
        assertTrue(sec.reason().contains("system"), "Reason should identify forbidden call");
    }

    @Test
    @Order(6)
    void testCpp_SecurityViolation_ForbiddenHeader() {
        String code = "#include <windows.h>\nint main() { return 0; }";
        SecurityResult sec = SecurityScanner.scan(code, "cpp");
        assertFalse(sec.isSafe(), "Forbidden header <windows.h> should be caught");
        assertTrue(sec.reason().contains("windows.h"), "Reason should specify windows.h");
    }

    @Test
    @Order(7)
    void testPython_SecurityViolation_OsSystem() {
        String code = "import os\nos.system('whoami')";
        SecurityResult sec = SecurityScanner.scan(code, "python");
        assertFalse(sec.isSafe(), "os.system in Python must be caught");
        assertTrue(sec.reason().contains("os.system"), "Reason should specify os.system");
    }

    @Test
    @Order(8)
    void testJava_SecurityViolation_RuntimeExec() {
        String code = "public class Solution {\n    public static void main(String[] args) throws Exception {\n        Runtime.getRuntime().exec(\"calc.exe\");\n    }\n}";
        SecurityResult sec = SecurityScanner.scan(code, "java");
        assertFalse(sec.isSafe(), "Runtime.getRuntime().exec must be caught");
        assertTrue(sec.reason().contains("Runtime"), "Reason should specify Runtime.getRuntime");
    }

    // ─── 3. ERROR & TIMEOUT TESTS ─────────────────────────────────────────────

    @Test
    @Order(9)
    void testC_CompileError_CE() {
        String code = "int main() { syntax error here; return 0; }";
        int subId = 2005;
        CompileResult cRes = sandboxRunner.compile(subId, code, "c");
        assertFalse(cRes.isSuccess(), "Invalid C syntax must produce Compile Error");
        assertNotNull(cRes.getErrorOutput());
        sandboxRunner.cleanup(subId);
    }

    @Test
    @Order(10)
    void testPython_Timeout_TLE() {
        String code = "while True:\n    pass\n";
        int subId = 2006;
        CompileResult cRes = sandboxRunner.compile(subId, code, "python");
        assertTrue(cRes.isSuccess());

        RunResult rRes = sandboxRunner.run(subId, "python", "", 1000);
        assertEquals("TLE", rRes.getVerdict(), "Infinite loop must result in TLE");
        sandboxRunner.cleanup(subId);
    }

    @Test
    @Order(11)
    void testCpp_RuntimeError_RE() {
        String code = "#include <cstdlib>\nint main() { exit(42); }";
        int subId = 2007;
        CompileResult cRes = sandboxRunner.compile(subId, code, "cpp");
        assertTrue(cRes.isSuccess());

        RunResult rRes = sandboxRunner.run(subId, "cpp", "", 2000);
        assertEquals("RE", rRes.getVerdict(), "Non-zero exit code must result in RE");
        sandboxRunner.cleanup(subId);
    }
    // ─── 4. AI-GENERATED ARTIFACT DETECTION TESTS ─────────────────────────────

    @Test
    @Order(12)
    void testAI_Detection_ChatGPT_Comment() {
        String code = "// Here is the complete Java solution for the problem:\r\n// Generated by ChatGPT\r\npublic class Solution {\r\n    public static void main(String[] args) {}\r\n}";
        SecurityResult sec = SecurityScanner.scan(code, "java");
        assertFalse(sec.isSafe(), "ChatGPT comment must be caught");
        assertTrue(sec.reason().contains("AI"), "Reason should specify AI");
    }

    @Test
    @Order(13)
    void testAI_Detection_Markdown_Fence() {
        String code = "`python\r\n# Solution\r\nprint('Hello')\r\n`";
        SecurityResult sec = SecurityScanner.scan(code, "python");
        assertFalse(sec.isSafe(), "Markdown code fence from AI chat must be caught");
        assertTrue(sec.reason().contains("Markdown"), "Reason should specify Markdown fence");
    }
}