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
        // Sinh viên tách đúng Main.java và Student.java riêng biệt, có getters đầy đủ
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
                    public String getName() { return name; }
                    public double getGpa() { return gpa; }
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
                    public String getName() { return name; }
                    public double getGpa() { return gpa; }
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

    @Test
    void testImmutableFieldWithSetter_ShouldFail() throws Exception {
        // Spec: id là immutable (mutable = false) -> cấm có setter
        String specJson = """
                {
                  "classes": [
                    {
                      "name": "Point",
                      "classType": "CLASS",
                      "fields": [
                        { "name": "id", "type": "int", "modifier": "private", "mutable": false }
                      ],
                      "constructors": [
                        { "parameterTypes": ["int"], "modifier": "public" }
                      ]
                    }
                  ]
                }
                """;
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        String pointCode = """
                public class Point {
                    private int id;
                    public Point(int id) { this.id = id; }
                    public int getId() { return id; }
                    public void setId(int id) { this.id = id; }
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Point.java"), pointCode);

        Process p = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Point.java")
                .directory(tempWorkDir.toFile()).start();
        assertEquals(0, p.waitFor());

        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), specJson);
        assertFalse(result.isPassed(), "Immutable field có setter phải bị từ chối.");
        assertTrue(result.getErrorMessage().contains("immutable") || result.getErrorMessage().contains("không được phép có setter"),
                "Lỗi phải nêu rõ immutable: " + result.getErrorMessage());
    }

    @Test
    void testMutableFieldWithoutSetter_ShouldFail() throws Exception {
        // Spec: gpa là mutable (mutable = true) -> bắt buộc có setter
        String specJson = """
                {
                  "classes": [
                    {
                      "name": "Point",
                      "classType": "CLASS",
                      "fields": [
                        { "name": "x", "type": "double", "modifier": "private", "mutable": true }
                      ],
                      "constructors": [
                        { "parameterTypes": ["double"], "modifier": "public" }
                      ]
                    }
                  ]
                }
                """;
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        String pointCode = """
                public class Point {
                    private double x;
                    public Point(double x) { this.x = x; }
                    public double getX() { return x; }
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Point.java"), pointCode);

        Process p = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Point.java")
                .directory(tempWorkDir.toFile()).start();
        assertEquals(0, p.waitFor());

        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), specJson);
        assertFalse(result.isPassed(), "Mutable field thiếu setter phải bị từ chối.");
        assertTrue(result.getErrorMessage().contains("bắt buộc phải có setter"),
                "Lỗi phải yêu cầu setter: " + result.getErrorMessage());
    }

    @Test
    void testConstructorCount_ShouldFailWhenExtraConstructorAdded() throws Exception {
        // Spec chỉ cho phép 1 constructor
        String specJson = """
                {
                  "classes": [
                    {
                      "name": "Point",
                      "classType": "CLASS",
                      "fields": [
                        { "name": "x", "type": "double", "modifier": "private" }
                      ],
                      "constructors": [
                        { "parameterTypes": ["double"], "modifier": "public" }
                      ]
                    }
                  ]
                }
                """;
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        // Sinh viên định nghĩa 2 constructor (thừa default constructor không được phép)
        String pointCode = """
                public class Point {
                    private double x;
                    public Point() { this.x = 0.0; }
                    public Point(double x) { this.x = x; }
                    public double getX() { return x; }
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Point.java"), pointCode);

        Process p = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Point.java")
                .directory(tempWorkDir.toFile()).start();
        assertEquals(0, p.waitFor());

        OopCheckResult result = OopReflectionGrader.check(tempWorkDir.toFile(), specJson);
        assertFalse(result.isPassed(), "Sinh viên viết thừa constructor phải bị từ chối.");
        assertTrue(result.getErrorMessage().contains("phải có chính xác 1 constructor"),
                "Lỗi phải nêu rõ số lượng constructor: " + result.getErrorMessage());
    }

    @Test
    void testOopDynamicTester_DefaultValueValidation() throws Exception {
        // Spec yêu cầu default constructor khởi tạo defaultValue = "0.0"
        String specJson = """
                {
                  "classes": [
                    {
                      "name": "Point",
                      "classType": "CLASS",
                      "fields": [
                        { "name": "x", "type": "double", "modifier": "private", "defaultValue": "0.0" }
                      ],
                      "constructors": [
                        { "parameterTypes": [], "modifier": "public" }
                      ]
                    }
                  ]
                }
                """;
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        // Sinh viên khởi tạo x = 99.0 thay vì 0.0
        String pointCode = """
                public class Point {
                    private double x;
                    public Point() { this.x = 99.0; }
                    public double getX() { return x; }
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Point.java"), pointCode);

        Process p = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Point.java")
                .directory(tempWorkDir.toFile()).start();
        assertEquals(0, p.waitFor());

        OopCheckResult dynamicRes = OopDynamicTester.test(tempWorkDir.toFile(), specJson);
        assertFalse(dynamicRes.isPassed(), "Khởi tạo sai giá trị mặc định phải bị Dynamic Tester phát hiện.");
        assertTrue(dynamicRes.getErrorMessage().contains("giá trị mặc định"),
                "Lỗi phải nhắc tới giá trị mặc định: " + dynamicRes.getErrorMessage());
    }

    @Test
    void testOopDynamicTester_MinMaxValidation() throws Exception {
        // Spec yêu cầu min = 0.0, max = 10.0 cho điểm số
        String specJson = """
                {
                  "classes": [
                    {
                      "name": "Score",
                      "classType": "CLASS",
                      "fields": [
                        { "name": "val", "type": "double", "modifier": "private", "mutable": true, "min": 0.0, "max": 10.0 }
                      ],
                      "constructors": [
                        { "parameterTypes": [], "modifier": "public" }
                      ]
                    }
                  ]
                }
                """;
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        // Sinh viên không kiểm tra min max trong setter, nhận bừa mọi giá trị
        String scoreCode = """
                public class Score {
                    private double val;
                    public Score() {}
                    public double getVal() { return val; }
                    public void setVal(double val) { this.val = val; }
                }
                """;

        Files.writeString(tempWorkDir.resolve("Main.java"), mainCode);
        Files.writeString(tempWorkDir.resolve("Score.java"), scoreCode);

        Process p = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java", "Score.java")
                .directory(tempWorkDir.toFile()).start();
        assertEquals(0, p.waitFor());

        OopCheckResult dynamicRes = OopDynamicTester.test(tempWorkDir.toFile(), specJson);
        assertFalse(dynamicRes.isPassed(), "Setter không validate min/max phải bị Dynamic Tester phát hiện.");
        assertTrue(dynamicRes.getErrorMessage().contains("min") || dynamicRes.getErrorMessage().contains("max"),
                "Lỗi phải báo vi phạm ràng buộc min/max: " + dynamicRes.getErrorMessage());
    }
}
