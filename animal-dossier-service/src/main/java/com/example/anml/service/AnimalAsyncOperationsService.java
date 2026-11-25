package com.example.anml.service;

import com.example.anml.config.AsyncConfig;
import com.example.anml.dto.AnimalDto;
import com.example.anml.dto.HabitatReportDto;
import com.example.anml.dto.VeterinaryCheckDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

@Service
public class AnimalAsyncOperationsService {

    private static final Logger log = LoggerFactory.getLogger(AnimalAsyncOperationsService.class);

    public record NotificationRecord(Long animalId, String animalName, String threadName, Instant sentAt) {
    }

    private final long defaultDelayMs;
    private final List<NotificationRecord> sentNotifications = new CopyOnWriteArrayList<>();

    public AnimalAsyncOperationsService(
            @Value("${app.async.simulated-delay-ms:800}") long defaultDelayMs) {
        this.defaultDelayMs = defaultDelayMs;
    }

    public long getDefaultDelayMs() {
        return defaultDelayMs;
    }

    @Async(AsyncConfig.ANIMAL_TASK_EXECUTOR)
    public void sendKeeperNotification(AnimalDto animal,
                                       long delayMs,
                                       boolean simulateFailure,
                                       CountDownLatch completionLatch) {
        String threadName = Thread.currentThread().getName();
        try {
            log.info("Початок надсилання сповіщення доглядачам для '{}' у потоці '{}'",
                    animal.name(), threadName);
            simulateSlowIo(delayMs);

            if (simulateFailure) {
                throw new IllegalStateException(
                        "Не вдалося надіслати сповіщення доглядачам для тварини '" + animal.name() + "'");
            }

            sentNotifications.add(new NotificationRecord(animal.id(), animal.name(), threadName, Instant.now()));
            log.info("Сповіщення доглядачам для '{}' успішно надіслано у потоці '{}'",
                    animal.name(), threadName);
        } finally {
            if (completionLatch != null) {
                completionLatch.countDown();
            }
        }
    }

    @Async(AsyncConfig.ANIMAL_TASK_EXECUTOR)
    public CompletableFuture<VeterinaryCheckDto> fetchVeterinaryStatus(Long animalId,
                                                                       long delayMs,
                                                                       boolean simulateFailure) {
        String threadName = Thread.currentThread().getName();
        log.info("Отримання ветеринарного статусу для тварини id={} у потоці '{}'", animalId, threadName);
        simulateSlowIo(delayMs);

        if (simulateFailure) {
            throw new IllegalStateException("Зовнішній ветеринарний сервіс тимчасово недоступний");
        }

        VeterinaryCheckDto dto = new VeterinaryCheckDto(
                animalId,
                "Здорова (плановий ветеринарний огляд пройдено)",
                threadName,
                false
        );
        return CompletableFuture.completedFuture(dto);
    }

    @Async(AsyncConfig.ANIMAL_TASK_EXECUTOR)
    public CompletableFuture<HabitatReportDto> fetchHabitatReport(String habitat,
                                                                  long delayMs,
                                                                  boolean simulateFailure) {
        String threadName = Thread.currentThread().getName();
        log.info("Генерація звіту для середовища '{}' у потоці '{}'", habitat, threadName);
        simulateSlowIo(delayMs);

        if (simulateFailure) {
            throw new IllegalStateException("Сервіс моніторингу середовища тимчасово недоступний");
        }

        HabitatReportDto dto = new HabitatReportDto(
                habitat,
                "Звіт сформовано: середовище '" + habitat + "' відповідає нормам",
                threadName,
                false
        );
        return CompletableFuture.completedFuture(dto);
    }

    public List<NotificationRecord> getSentNotifications() {
        return List.copyOf(sentNotifications);
    }

    public void clearSentNotifications() {
        sentNotifications.clear();
    }

    private void simulateSlowIo(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Асинхронну операцію перервано", e);
        }
    }
}