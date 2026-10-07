package com.grading.server.judge.model;

public class TestCase {
    private final int id;
    private final String input;
    private final String expectedOutput;

    public TestCase(int id, String input, String expectedOutput) {
        this.id = id;
        this.input = input;
        this.expectedOutput = expectedOutput;
    }

    public int getId() {
        return id;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }
}
