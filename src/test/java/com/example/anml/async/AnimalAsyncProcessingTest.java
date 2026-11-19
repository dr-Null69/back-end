package com.example.anml.async;

import com.example.anml.config.AnimalAsyncExceptionHandler;
import com.example.anml.config.AsyncConfig;
import com.example.anml.dto.AnimalDossierDto;
import com.example.anml.dto.AnimalDto;
import com.example.anml.exception.AsyncTimeoutException;
import com.example.anml.service.AnimalAsyncOperationsService;
import com.example.anml.service.AnimalDossierService;
import com.example.anml.service.AnimalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = AnimalAsyncProcessingTest.TestAsyncConfig.class)
class AnimalAsyncProcessingTest {

    @Configuration
    @Import({
            AsyncConfig.class,
            AnimalAsyncExceptionHandler.class,
            AnimalAsyncOperationsService.class,
            AnimalDossierService.class
    })
    static class TestAsyncConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Autowired
    @Qualifier(AsyncConfig.ANIMAL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor animalTaskExecutor;

    @Autowired
    private AnimalAsyncExceptionHandler asyncExceptionHandler;

    @Autowired
    private AnimalAsyncOperationsService asyncOperationsService;

    @Autowired
    private AnimalDossierService animalDossierService;

    @MockitoBean
    private AnimalService animalService;

    private final AnimalDto sampleLion = new AnimalDto(1L, "Сімба", "Лев африканський", 5, "Савана");

    @BeforeEach
    void setUp() {
        asyncExceptionHandler.clearRecordedErrors();
        asyncOperationsService.clearSentNotifications();
        when(animalService.findById(1L)).thenReturn(sampleLion);
    }

    // Сценарій 1: Повільний метод більше не блокує викликаючий код
    @Test
    void fireAndForget_returnsImmediatelyBeforeSlowNotificationCompletes_andRunsOnCustomExecutor() throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);
        String callerThread = Thread.currentThread().getName();

        long startNanos = System.nanoTime();
        AnimalDto returned = animalDossierService.notifyKeepersAboutAnimal(1L, 400, false, completionLatch);
        long callDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals("Сімба", returned.name());
        assertEquals(1, completionLatch.getCount(), "Метод мав повернути керування до завершення повільної операції");
        assertTrue(callDurationMs < 350, "Виклик не повинен блокуватися на 400 мс (фактично: " + callDurationMs + " мс)");

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS), "Повільна операція мала завершитися асинхронно");
        List<AnimalAsyncOperationsService.NotificationRecord> sent = asyncOperationsService.getSentNotifications();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).threadName().startsWith(AsyncConfig.THREAD_NAME_PREFIX));
        assertNotEquals(callerThread, sent.get(0).threadName());
    }

    // Сценарій 2: Виконавець є явно визначеним і має обмежені ресурси
    @Test
    void executor_isExplicitlyBounded_andAsyncMethodUsesCustomThreadPrefix() throws Exception {
        assertEquals(2, animalTaskExecutor.getCorePoolSize());
        assertEquals(4, animalTaskExecutor.getMaxPoolSize());
        assertEquals(10, animalTaskExecutor.getQueueCapacity());
        assertEquals(AsyncConfig.THREAD_NAME_PREFIX, animalTaskExecutor.getThreadNamePrefix());

        var vetCheck = asyncOperationsService.fetchVeterinaryStatus(1L, 50, false)
                .get(2, TimeUnit.SECONDS);

        assertTrue(vetCheck.threadName().startsWith(AsyncConfig.THREAD_NAME_PREFIX));
        assertFalse(vetCheck.threadName().startsWith("task-"));
        assertFalse(vetCheck.threadName().contains("SimpleAsyncTaskExecutor"));
    }

    // Сценарій 3: Два незалежні виклики справді виконуються одночасно
    @Test
    void twoIndependentAsyncCalls_executeConcurrently_andCombineResults() {
        long startNanos = System.nanoTime();
        AnimalDossierDto dossier = animalDossierService.buildAnimalDossier(
                1L, 450, 450, false, false, 3000);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals("Сімба", dossier.animal().name());
        assertFalse(dossier.veterinaryCheck().fallback());
        assertFalse(dossier.habitatReport().fallback());
        assertTrue(dossier.veterinaryCheck().threadName().startsWith(AsyncConfig.THREAD_NAME_PREFIX));
        assertTrue(dossier.habitatReport().threadName().startsWith(AsyncConfig.THREAD_NAME_PREFIX));
        assertNotEquals(
                dossier.veterinaryCheck().threadName(),
                dossier.habitatReport().threadName(),
                "Обидва виклики мають виконуватися паралельно в різних потоках пулу"
        );
        assertTrue(elapsedMs < 800,
                "Паралельне виконання двох задач по 450 мс має займати < 800 мс (фактично: " + elapsedMs + " мс)");
    }

    // Сценарій 4 (частина 1): Помилка в CompletableFuture обробляється через exceptionally / handle
    @Test
    void failedCompletableFuture_isHandledWithFallback_withoutFailingCaller() {
        AnimalDossierDto vetFallbackDossier = animalDossierService.buildAnimalDossier(
                1L, 50, 50, true, false, 2000);

        assertTrue(vetFallbackDossier.veterinaryCheck().fallback());
        assertTrue(vetFallbackDossier.veterinaryCheck().healthStatus().contains("fallback"));
        assertFalse(vetFallbackDossier.habitatReport().fallback());

        AnimalDossierDto habitatFallbackDossier = animalDossierService.buildAnimalDossier(
                1L, 50, 50, false, true, 2000);

        assertFalse(habitatFallbackDossier.veterinaryCheck().fallback());
        assertTrue(habitatFallbackDossier.habitatReport().fallback());
        assertTrue(habitatFallbackDossier.habitatReport().conditionSummary().contains("fallback"));
    }

    // Сценарій 4 (частина 2): Помилка у void @Async фіксується через AsyncUncaughtExceptionHandler
    @Test
    void failedVoidAsyncMethod_isCapturedByAsyncUncaughtExceptionHandler() throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);

        animalDossierService.notifyKeepersAboutAnimal(1L, 50, true, completionLatch);

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS));
        waitUntilRecordedErrorAppears();

        List<AnimalAsyncExceptionHandler.RecordedAsyncError> errors = asyncExceptionHandler.getRecordedErrors();
        assertEquals(1, errors.size());
        assertEquals("sendKeeperNotification", errors.get(0).methodName());
        assertInstanceOf(IllegalStateException.class, errors.get(0).exception());
        assertTrue(errors.get(0).threadName().startsWith(AsyncConfig.THREAD_NAME_PREFIX));
    }

    // Сценарій 5: Очікування завжди обмежене в часі (тайм-аут)
    @Test
    void slowFutureExceedingTimeout_stopsWaitingWithAsyncTimeoutException() {
        long startNanos = System.nanoTime();

        AsyncTimeoutException ex = assertThrows(
                AsyncTimeoutException.class,
                () -> animalDossierService.buildAnimalDossier(1L, 2000, 2000, false, false, 200)
        );

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(ex.getMessage().contains("200"));
        assertTrue(elapsedMs < 1500,
                "Очікування мало перерватися за тайм-аутом ~200 мс (фактично: " + elapsedMs + " мс)");
    }

    private void waitUntilRecordedErrorAppears() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (asyncExceptionHandler.getRecordedErrors().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }
}