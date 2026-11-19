package com.example.anml.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Аспект, який перехоплює методи з {@link RetryOnFailure} і повторює їхнє виконання
 * у разі виникнення дозволених тимчасових винятків.
 */
@Aspect
@Component
public class RetryOnFailureAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryOnFailureAspect.class);

    @Around("@annotation(retryOnFailure)")
    public Object executeWithRetry(ProceedingJoinPoint joinPoint, RetryOnFailure retryOnFailure) throws Throwable {
        int maxAttempts = Math.max(1, retryOnFailure.maxAttempts());
        String methodName = joinPoint.getSignature() != null
                ? joinPoint.getSignature().toShortString()
                : "unknownMethod";

        Throwable lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (Throwable ex) {
                if (!isRetryable(ex, retryOnFailure.retryFor())) {
                    throw ex;
                }
                lastException = ex;
                if (attempt < maxAttempts) {
                    log.warn("Спроба {}/{} для {} завершилась помилкою ({}): повторний виклик...",
                            attempt, maxAttempts, methodName, ex.getClass().getSimpleName());
                } else {
                    log.error("Усі {} спроби для {} завершились помилкою: {}",
                            maxAttempts, methodName, ex.getMessage());
                }
            }
        }
        throw lastException;
    }

    private boolean isRetryable(Throwable ex, Class<? extends Throwable>[] retryableTypes) {
        for (Class<? extends Throwable> type : retryableTypes) {
            if (type.isInstance(ex)) {
                return true;
            }
        }
        return false;
    }
}