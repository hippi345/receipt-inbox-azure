package com.interview.receipt.api;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ActivityLogTail {
    private ActivityLogTail() {
    }

    static long recentStart(Path file, int maxLines) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        byte[] data = Files.readAllBytes(file);
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n' && i + 1 < data.length) {
                starts.add(i + 1);
            }
        }
        if (starts.size() <= maxLines) {
            return 0;
        }
        return starts.get(starts.size() - maxLines);
    }

    static Read readCompleteLines(Path file, long position) throws IOException {
        if (!Files.exists(file)) {
            return new Read(List.of(), position);
        }
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            if (input.length() < position) {
                position = 0;
            }
            int available = (int) (input.length() - position);
            if (available == 0) {
                return new Read(List.of(), position);
            }
            byte[] data = new byte[available];
            input.seek(position);
            input.readFully(data);
            List<String> lines = new ArrayList<>();
            int lineStart = 0;
            for (int i = 0; i < data.length; i++) {
                if (data[i] != '\n') {
                    continue;
                }
                int end = i;
                if (end > lineStart && data[end - 1] == '\r') {
                    end--;
                }
                lines.add(new String(data, lineStart, end - lineStart, StandardCharsets.UTF_8));
                lineStart = i + 1;
            }
            return new Read(lines, position + lineStart);
        }
    }

    record Read(List<String> lines, long position) {
    }
}
