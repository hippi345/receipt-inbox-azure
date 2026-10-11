package com.interview.receipt.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.nio.file.Files;

public class ActivityLogAttachment implements DisposableBean {
    private final FileAppender<?> appender;

    public ActivityLogAttachment(String applicationName) throws IOException {
        Files.createDirectories(ActivityLog.file().getParent());
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger receipt = context.getLogger("com.interview");
        if (receipt.getAppender(ActivityLog.APPENDER_NAME) != null) {
            this.appender = null;
            return;
        }
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} " + applicationName + " %-5level %logger{24} - %msg%n");
        encoder.start();
        FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName(ActivityLog.APPENDER_NAME);
        fileAppender.setFile(ActivityLog.file().toString());
        fileAppender.setAppend(true);
        fileAppender.setPrudent(true);
        fileAppender.setEncoder(encoder);
        fileAppender.start();
        receipt.addAppender(fileAppender);
        this.appender = fileAppender;
    }

    @Override
    public void destroy() {
        if (appender == null) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("com.interview").detachAppender(ActivityLog.APPENDER_NAME);
        appender.stop();
    }
}
