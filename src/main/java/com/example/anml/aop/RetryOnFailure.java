package com.example.anml.aop;

import org.springframework.dao.TransientDataAccessException;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Повторює виконання методу при виникненні тимчасових винятків.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryOnFailure {

    int maxAttempts() default 3;

    Class<? extends Throwable>[] retryFor() default {
            TransientDataAccessException.class,
            IllegalStateException.class
    };
}