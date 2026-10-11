package com.interview.receipt.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityLogTailTest {

    @TempDir
    Path dir;

    @Test
    void waitsForAFullLine() throws Exception {
        Path log = dir.resolve("activity.log");
        Files.writeString(log, "one\r\ntwo", StandardCharsets.UTF_8);

        ActivityLogTail.Read first = ActivityLogTail.readCompleteLines(log, 0);

        assertEquals(java.util.List.of("one"), first.lines());
        Files.writeString(log, "one\r\ntwo\r\n", StandardCharsets.UTF_8);
        ActivityLogTail.Read second = ActivityLogTail.readCompleteLines(log, first.position());
        assertEquals(java.util.List.of("two"), second.lines());
    }

    @Test
    void recentStartKeepsTheLastLines() throws Exception {
        Path log = dir.resolve("activity.log");
        Files.writeString(log, "a\r\nb\r\nc\r\n", StandardCharsets.UTF_8);

        long start = ActivityLogTail.recentStart(log, 2);
        ActivityLogTail.Read read = ActivityLogTail.readCompleteLines(log, start);

        assertEquals(java.util.List.of("b", "c"), read.lines());
    }
}
