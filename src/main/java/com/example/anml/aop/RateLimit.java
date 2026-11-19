package com.example.anml.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Обмежує кількість викликів методу в межах заданого вікна часу.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    int maxCalls() default 5;

    long windowSeconds() default 60;

    String key() default "";
}