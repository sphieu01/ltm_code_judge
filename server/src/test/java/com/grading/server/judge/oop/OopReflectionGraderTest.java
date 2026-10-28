package com.grading.server.judge.oop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OopReflectionGraderTest {

    private Path tempWorkDir;

    private final String sampleOopSpecJson = """
            {
              "classes": [
                {
                  "name": "Student",
                  "classType": "CLASS",
                  "fields": [
                    { "name": "name", "type": "String", "modifier": "private" },
                    { "name": "gpa", "type": "double", "modifier": "private" }
                  ],
                  "constructors": [
                    { "parameterTypes": ["String", "double"], "modifier": "public" }
                  ],
                  "methods": [
                    { "name": "display", "returnType": "void", "parameterTypes": [], "modifier": "public" }
                  ]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkDir = Files.createTempDirectory("oop_test_dir_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempWorkDir != null && Files.exists(tempWorkDir)) {
            Files.walk(tempWorkDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testSingleFileWithMultipleClasses_ShouldFailDueToMissingSeparateFile() throws Exception {
        // Sinh viên dồn cả Main và Student vào 1 file Main.java duy nhất
        String combinedCode = """
                public class Main {
                    public static void main(String[] args) {}
                }
                class Student {
                    private String name;
                    private double gpa;
                    public Student(String name, double gpa) { this.name = name; this.gpa = gpa; }
                    public void display() {}
                }
                """;
        Files.writeString(tempWorkDir.resolve("Main.java"), combinedCode);

        // Compile ra .class
        Process compileProcess = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java")
                .directory(tempWorkDir.toFile())
                .start();
        assertEquals(0, compileProcess.waitFor());

        // Chấm với OopReflectionGrader
        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), sampleOopSpecJson);
        assertFalse(result.isPassed(), "Sinh viên dồn class vào Main.java phải bị từ chối.");
        assertTrue(result.getErrorMessage().contains("Student.java") || result.getErrorMessage().contains("Main.java"),
                "Thông báo lỗi phải chỉ rõ yêu cầu file riêng biệt: " + result.getErrorMessage());
    }

    @Test
    void testSeparateFiles_ShouldPass() throws Exception {
        // Sinh viên tách đúng Main.java và Student.java riêng biệt
        String mainCode = """
                public class Main {
                    public static void main(String[] args) {}
                }
                """;
        String studentCode = """
                public class Student {
                    private String name;
                    private double gpa;
                    public Student(String name, double gpa) {
                        this.name = name;
                        this.gpa = gpa;
                    }
                    public void display() {}
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Student.java"), studentCode);

        // Compile cả 2 file
        Process compileProcess = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Student.java")
                .directory(tempWorkDir.toFile())
                .start();
        assertEquals(0, compileProcess.waitFor());

        // Chấm với OopReflectionGrader
        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), sampleOopSpecJson);
        assertTrue(result.isPassed(), "Tách file riêng chuẩn và đúng spec phải PASS: " + result.getErrorMessage());
    }

    @Test
    void testSeparateFileButNonPublicClass_ShouldFail() throws Exception {
        // Sinh viên tạo file Student.java riêng nhưng quên modifier public (để package-private)
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        String studentCode = """
                class Student {
                    private String name;
                    private double gpa;
                    public Student(String name, double gpa) { this.name = name; this.gpa = gpa; }
                    public void display() {}
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Student.java"), studentCode);

        Process compileProcess = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Student.java")
                .directory(tempWorkDir.toFile())
                .start();
        assertEquals(0, compileProcess.waitFor());

        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), sampleOopSpecJson);
        assertFalse(result.isPassed(), "Class trong file riêng không khai báo public phải bị từ chối.");
        assertTrue(result.getErrorMessage().contains("public"), "Thông báo phải yêu cầu access modifier là 'public'.");
    }
}
