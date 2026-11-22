package com.example.anml.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AnimalAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AnimalAsyncExceptionHandler.class);

    public record RecordedAsyncError(String methodName, Throwable exception, String threadName, Instant timestamp) {
    }

    private final List<RecordedAsyncError> recordedErrors = new CopyOnWriteArrayList<>();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        String threadName = Thread.currentThread().getName();
        log.error("Необроблений виняток у void @Async методі '{}' (потік '{}'): {}",
                method.getName(), threadName, ex.getMessage(), ex);
        recordedErrors.add(new RecordedAsyncError(method.getName(), ex, threadName, Instant.now()));
    }

    public List<RecordedAsyncError> getRecordedErrors() {
        return List.copyOf(recordedErrors);
    }

    public void clearRecordedErrors() {
        recordedErrors.clear();
    }
}