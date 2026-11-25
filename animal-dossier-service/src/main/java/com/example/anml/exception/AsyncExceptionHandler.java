package com.example.anml.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AsyncExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    @ExceptionHandler(TaskRejectedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleTaskRejected(TaskRejectedException ex) {
        log.warn("Асинхронний виконавець переповнений, завдання відхилено: {}", ex.getMessage());
        return Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Temporarily Overloaded",
                "message", "Сервіс тимчасово перевантажений, спробуйте пізніше"
        );
    }

    @ExceptionHandler(AsyncTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Map<String, Object> handleAsyncTimeout(AsyncTimeoutException ex) {
        log.warn("Перевищено тайм-аут асинхронної операції: {}", ex.getMessage());
        return Map.of(
                "status", HttpStatus.GATEWAY_TIMEOUT.value(),
                "error", "Async Operation Timeout",
                "message", ex.getMessage()
        );
    }
}