package com.example.anml.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class AsyncTimeoutException extends RuntimeException {

    public AsyncTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}