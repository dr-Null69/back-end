package com.example.anml.aop;

import com.example.anml.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> callHistory = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitAspect() {
        this(Clock.systemUTC());
    }

    RateLimitAspect(Clock clock) {
        this.clock = clock;
    }

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String bucketKey = resolveKey(joinPoint, rateLimit);
        int maxCalls = Math.max(1, rateLimit.maxCalls());
        long windowSeconds = Math.max(1L, rateLimit.windowSeconds());
        Instant now = Instant.now(clock);
        Instant windowStart = now.minusSeconds(windowSeconds);

        Deque<Instant> timestamps = callHistory.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxCalls) {
                log.warn("Перевищено ліміт викликів для '{}': максимум {} за {} с",
                        bucketKey, maxCalls, windowSeconds);
                throw new RateLimitExceededException(String.format(
                        "Перевищено ліміт викликів для '%s': дозволено не більше %d викликів за %d с",
                        bucketKey, maxCalls, windowSeconds));
            }
            timestamps.addLast(now);
        }

        return joinPoint.proceed();
    }

    public void reset() {
        callHistory.clear();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (rateLimit.key() != null && !rateLimit.key().isBlank()) {
            return rateLimit.key();
        }
        return joinPoint.getSignature() != null
                ? joinPoint.getSignature().toShortString()
                : "default";
    }
}