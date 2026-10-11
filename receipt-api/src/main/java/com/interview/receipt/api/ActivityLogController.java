package com.interview.receipt.api;

import com.interview.receipt.logging.ActivityLog;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
public class ActivityLogController {
    private static final int RECENT_LINES = 80;

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        long start = ActivityLogTail.recentStart(ActivityLog.file(), RECENT_LINES);
        Thread tail = new Thread(() -> tail(emitter, start), "activity-log");
        tail.setDaemon(true);
        emitter.onCompletion(tail::interrupt);
        emitter.onTimeout(tail::interrupt);
        emitter.onError(error -> tail.interrupt());
        tail.start();
        return emitter;
    }

    private static void tail(SseEmitter emitter, long start) {
        long position = start;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActivityLogTail.Read read = ActivityLogTail.readCompleteLines(ActivityLog.file(), position);
                position = read.position();
                for (String line : read.lines()) {
                    emitter.send(line);
                }
                Thread.sleep(50);
            }
            emitter.complete();
        } catch (Exception finished) {
            emitter.complete();
        }
    }
}
