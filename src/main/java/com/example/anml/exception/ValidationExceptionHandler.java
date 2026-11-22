package com.example.anml.exception;

import org.springframework.http.HttpStatus;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .toList();
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation Failed",
                "messages", messages
        );
    }
}
