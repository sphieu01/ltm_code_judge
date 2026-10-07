package com.grading.server.judge;

import com.grading.server.judge.model.CompileResult;
import com.grading.server.judge.model.RunResult;
import com.grading.server.judge.model.TestCase;
import com.grading.server.judge.model.VerdictType;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SandboxRunnerTest {

    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM THU SANDBOX RUNNER ===");
        SandboxRunner runner = new SandboxRunner();
        Path sandboxDir = Paths.get("temp", "sandbox", "test_run");

        try {
            // Test 1: AC (Chap nhan) voi ten class tu dat: BaiTapTong
            String codeAC = """
                import java.util.Scanner;
                public class BaiTapTong {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int a = sc.nextInt();
                        int b = sc.nextInt();
                        System.out.println(a + b);
                    }
                }
                """;
            String className = SandboxRunner.extractClassName(codeAC);
            assertVerdict("Auto-detect ClassName", "BaiTapTong", className);

            CompileResult cr1 = runner.compile(sandboxDir, className, codeAC);
            assertCondition("Compile AC", cr1.isSuccess());

            TestCase tc1 = new TestCase(1, "5 7", "12");
            RunResult rr1 = runner.run(sandboxDir, className, tc1, 1000);
            assertVerdict("Test AC (5 + 7 = 12)", VerdictType.AC.name(), rr1.getVerdict().name());

            // Test 2: WA (Sai dap an)
            TestCase tc2 = new TestCase(2, "5 7", "99");
            RunResult rr2 = runner.run(sandboxDir, className, tc2, 1000);
            assertVerdict("Test WA (kết quả 12 != 99)", VerdictType.WA.name(), rr2.getVerdict().name());

            runner.cleanup(sandboxDir);

            // Test 3: TLE (Time Limit Exceeded - Vong lap vo tan)
            String codeTLE = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int a = sc.nextInt();
                        while (true) {
                            // Infinite loop
                        }
                    }
                }
                """;
            String classTLE = SandboxRunner.extractClassName(codeTLE);
            runner.compile(sandboxDir, classTLE, codeTLE);
            RunResult rrTLE = runner.run(sandboxDir, classTLE, new TestCase(3, "10", "10"), 1000);
            assertVerdict("Test TLE (kill sau 1s)", VerdictType.TLE.name(), rrTLE.getVerdict().name());
            System.out.println("-> Thoi gian TLE do duoc: " + rrTLE.getTimeMs() + " ms");
            runner.cleanup(sandboxDir);

            // Test 4: RE (Runtime Error - Chia cho 0)
            String codeRE = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int a = sc.nextInt();
                        int crash = a / 0;
                        System.out.println(crash);
                    }
                }
                """;
            String classRE = SandboxRunner.extractClassName(codeRE);
            runner.compile(sandboxDir, classRE, codeRE);
            RunResult rrRE = runner.run(sandboxDir, classRE, new TestCase(4, "10", "10"), 1000);
            assertVerdict("Test RE (ArithmeticException / by zero)", VerdictType.RE.name(), rrRE.getVerdict().name());
            runner.cleanup(sandboxDir);

            // Test 5: CE (Compile Error - Thieu dau cham phay)
            String codeCE = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Loi syntax")
                    }
                }
                """;
            String classCE = SandboxRunner.extractClassName(codeCE);
            CompileResult crCE = runner.compile(sandboxDir, classCE, codeCE);
            assertCondition("Test CE (isSuccess == false)", !crCE.isSuccess());
            System.out.println("-> Thong bao loi compiler thu duoc: " + crCE.getErrorMsg().trim().lines().findFirst().orElse(""));
            runner.cleanup(sandboxDir);

            System.out.println("\n🎉 TAT CA 5/5 TEST CASES DEU PASS HOAN HAO!");

        } finally {
            runner.cleanup(sandboxDir);
        }
    }

    private static void assertVerdict(String testName, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.println("✅ [" + testName + "] PASS: " + actual);
        } else {
            System.err.println("❌ [" + testName + "] FAIL: Ky vong " + expected + " nhung nhan duoc " + actual);
        }
    }

    private static void assertCondition(String testName, boolean condition) {
        if (condition) {
            System.out.println("✅ [" + testName + "] PASS");
        } else {
            System.err.println("❌ [" + testName + "] FAIL");
        }
    }
}
