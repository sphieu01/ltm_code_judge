package com.grading.server.judge;

public class OutputComparator {

    public static boolean compare(String actual, String expected) {
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }

        String cleanActual = actual.replace("\r\n", "\n").trim();
        String cleanExpected = expected.replace("\r\n", "\n").trim();

        return cleanActual.equals(cleanExpected);
    }
}
