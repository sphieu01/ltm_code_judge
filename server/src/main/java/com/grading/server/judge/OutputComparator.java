package com.grading.server.judge;

/**
 * OutputComparator — So sánh output thực tế với expected output.
 *
 * Chiến lược so sánh:
 *   - Trim whitespace ở đầu/cuối mỗi dòng
 *   - Bỏ qua dòng trống ở cuối
 *   - So sánh case-sensitive
 *   - Chuẩn hóa line endings (CRLF → LF)
 *
 * Đây là "lenient comparison" — phổ biến trong các OJ thật
 * để tránh fail vì trailing newline hay space thừa.
 */
public class OutputComparator {

    /**
     * So sánh actual output với expected output.
     *
     * @param actual   stdout từ chương trình của student
     * @param expected expected output từ test case trong DB
     * @return true nếu output khớp
     */
    public boolean compare(String actual, String expected) {
        if (actual == null) actual = "";
        if (expected == null) expected = "";

        // Chuẩn hóa: CRLF → LF
        actual = actual.replace("\r\n", "\n").replace("\r", "\n");
        expected = expected.replace("\r\n", "\n").replace("\r", "\n");

        // Split thành lines
        String[] actualLines = actual.split("\n", -1);
        String[] expectedLines = expected.split("\n", -1);

        // Trim trailing empty lines
        int actualLen = trimTrailingEmpty(actualLines);
        int expectedLen = trimTrailingEmpty(expectedLines);

        if (actualLen != expectedLen) return false;

        for (int i = 0; i < actualLen; i++) {
            // Trim trailing whitespace trên mỗi dòng
            String a = actualLines[i].stripTrailing();
            String e = expectedLines[i].stripTrailing();
            if (!a.equals(e)) return false;
        }

        return true;
    }

    /** Đếm số dòng thực sự (bỏ qua dòng trống ở cuối) */
    private int trimTrailingEmpty(String[] lines) {
        int len = lines.length;
        while (len > 0 && lines[len - 1].isBlank()) {
            len--;
        }
        return len;
    }
}
