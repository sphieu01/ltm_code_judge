package com.grading.server.judge.oop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grading.server.judge.OutputComparator;
import com.grading.server.judge.SandboxRunner;
import com.grading.server.judge.security.SecurityScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises the exact ZIP files offered to students, using the production grading stages. */
class BookOopSamplesTest {
    private static final Path FIXTURES = Path.of("../test_codes/java/book_oop");
    private static final AtomicInteger IDS = new AtomicInteger(870100);

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "ac, AC, NONE",
            "missing_constructor, CE, constructor",
            "invalid_setter, CE, price",
            "fake_to_string, CE, toString()"
    })
    void gradesZippedSamplesAndProvesOutputAloneCannotDetectTheErrors(
            String variant, String expectedVerdict, String errorFragment) throws Exception {
        assumeTrue(Files.isRegularFile(FIXTURES.resolve("problem/create_problem.json")),
                "Local Book OOP problem data is excluded from Git; restore it to run these sample checks.");
        Path folder = FIXTURES.resolve(variant);
        Path zipPath = folder.resolve(variant.equals("ac") ? "submission_ac.zip" : "submission.zip");
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            assertEquals(Set.of("Main.java", "Book.java"),
                    zip.stream().map(entry -> entry.getName()).collect(Collectors.toSet()));
            for (String name : List.of("Main.java", "Book.java")) {
                String source = Files.readString(folder.resolve(name));
                try (var contents = zip.getInputStream(zip.getEntry(name))) {
                    assertEquals(source.replace("\r\n", "\n"),
                            new String(contents.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
                }
                assertTrue(SecurityScanner.scan(source, "java").isSafe(), name + " must pass the real security scanner");
            }
        }

        JsonObject problem = JsonParser.parseString(Files.readString(FIXTURES.resolve("problem/create_problem.json"))).getAsJsonObject();
        String spec = problem.get("oopSpec").getAsString();
        assertEquals(JsonParser.parseString(Files.readString(FIXTURES.resolve("problem/oop_spec.json"))), JsonParser.parseString(spec));
        assertEquals(Files.readString(FIXTURES.resolve("problem/statement.md")).replace("\r\n", "\n").stripTrailing(),
                problem.get("description").getAsString().stripTrailing());
        JsonArray tests = problem.getAsJsonArray("testCases");
        assertEquals(JsonParser.parseString(Files.readString(FIXTURES.resolve("problem/test_cases.json"))), tests);

        int id = IDS.incrementAndGet();
        SandboxRunner runner = new SandboxRunner();
        try {
            String archive = Base64.getEncoder().encodeToString(Files.readAllBytes(zipPath));
            var compile = runner.compile(id, archive, "java", true);
            assertTrue(compile.isSuccess(), "All samples must compile; the three CE verdicts must come from OOP checks: " + compile.getErrorOutput());

            var reflection = OopReflectionGrader.check(Path.of(runner.getWorkDir(id)).toFile(), spec);
            OopCheckResult oop = reflection;
            if (reflection.isPassed()) oop = OopDynamicTester.test(Path.of(runner.getWorkDir(id)).toFile(), spec);

            if (variant.equals("missing_constructor")) assertFalse(reflection.isPassed(), "Missing constructor must fail structure checks");
            else assertTrue(reflection.isPassed(), "Other samples must have the correct structure");

            String actualVerdict = oop.isPassed() ? "AC" : "CE";
            assertEquals(expectedVerdict, actualVerdict);
            if (!oop.isPassed()) assertTrue(oop.getErrorMessage().contains(errorFragment), oop.getErrorMessage());

            // Intentionally run I/O even for rejected OOP samples: all four print correct output.
            // The production worker stops at OOP_VIOLATION instead, before running these test cases.
            for (var element : tests) {
                JsonObject test = element.getAsJsonObject();
                var result = runner.run(id, "java", test.get("inputData").getAsString(), problem.get("timeLimitMs").getAsInt());
                assertNull(result.getVerdict(), variant + ": " + test.get("name") + " runtime failure");
                assertTrue(new OutputComparator().compare(result.getStdout(), test.get("expectedOutput").getAsString()),
                        variant + ": " + test.get("name") + " output mismatch: " + result.getStdout());
            }
            System.out.printf("BOOK_OOP %s => %s | I/O=%d/%d | %s%n", variant, actualVerdict,
                    tests.size(), tests.size(), oop.isPassed() ? "All OOP checks passed" : "[OOP_VIOLATION] " + oop.getErrorMessage());
        } finally {
            runner.cleanup(id);
        }
    }
}
