package com.example.anml.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryOnFailureAspectTest {

    private final RetryOnFailureAspect aspect = new RetryOnFailureAspect();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    void whenProceedSucceedsFirstTime_callsProceedOnceAndReturnsResult() throws Throwable {
        RetryOnFailure annotation = getAnnotation("threeAttemptsMethod");
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.executeWithRetry(joinPoint, annotation);

        assertEquals("OK", result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void whenTransientFailureOccursOnce_retriesAndReturnsResult() throws Throwable {
        RetryOnFailure annotation = getAnnotation("threeAttemptsMethod");
        when(joinPoint.proceed())
                .thenThrow(new IllegalStateException("Тимчасовий збій"))
                .thenReturn("RECOVERED");

        Object result = aspect.executeWithRetry(joinPoint, annotation);

        assertEquals("RECOVERED", result);
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void whenAllAttemptsFailWithRetryableException_exhaustsAttemptsAndRethrows() throws Throwable {
        RetryOnFailure annotation = getAnnotation("threeAttemptsMethod");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("Постійний збій"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> aspect.executeWithRetry(joinPoint, annotation)
        );

        assertEquals("Постійний збій", ex.getMessage());
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    void whenNonRetryableExceptionOccurs_doesNotRetryAndRethrowsImmediately() throws Throwable {
        RetryOnFailure annotation = getAnnotation("threeAttemptsMethod");
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Некоректні дані"));

        assertThrows(
                IllegalArgumentException.class,
                () -> aspect.executeWithRetry(joinPoint, annotation)
        );

        verify(joinPoint, times(1)).proceed();
    }

    private RetryOnFailure getAnnotation(String methodName) throws NoSuchMethodException {
        return AnnotatedTargets.class.getDeclaredMethod(methodName).getAnnotation(RetryOnFailure.class);
    }

    @SuppressWarnings("unused")
    private static final class AnnotatedTargets {
        @RetryOnFailure(maxAttempts = 3)
        void threeAttemptsMethod() {
        }
    }
}