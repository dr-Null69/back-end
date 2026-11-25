package com.example.anml.aop;

import com.example.anml.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    void whenWithinLimit_callsProceed_andWhenExceeded_blocksWithoutCallingProceed() throws Throwable {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        RateLimitAspect aspect = new RateLimitAspect(clock);
        RateLimit annotation = getAnnotation("limitedMethod");
        when(joinPoint.proceed()).thenReturn(42L);

        assertEquals(42L, aspect.enforceRateLimit(joinPoint, annotation));
        assertEquals(42L, aspect.enforceRateLimit(joinPoint, annotation));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> aspect.enforceRateLimit(joinPoint, annotation)
        );

        assertTrue(ex.getMessage().contains("test-limit"));
        assertTrue(ex.getMessage().contains("2"));
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void whenWindowExpires_allowsNewCallsAndInvokesProceedAgain() throws Throwable {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        RateLimitAspect aspect = new RateLimitAspect(clock);
        RateLimit annotation = getAnnotation("limitedMethod");
        when(joinPoint.proceed()).thenReturn(1L);

        aspect.enforceRateLimit(joinPoint, annotation);
        aspect.enforceRateLimit(joinPoint, annotation);

        clock.advanceSeconds(11);

        Object result = aspect.enforceRateLimit(joinPoint, annotation);

        assertEquals(1L, result);
        verify(joinPoint, times(3)).proceed();
    }

    private RateLimit getAnnotation(String methodName) throws NoSuchMethodException {
        return AnnotatedTargets.class.getDeclaredMethod(methodName).getAnnotation(RateLimit.class);
    }

    @SuppressWarnings("unused")
    private static final class AnnotatedTargets {
        @RateLimit(maxCalls = 2, windowSeconds = 10, key = "test-limit")
        void limitedMethod() {
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}