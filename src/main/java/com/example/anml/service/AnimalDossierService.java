package com.example.anml.service;

import com.example.anml.dto.AnimalDossierDto;
import com.example.anml.dto.AnimalDto;
import com.example.anml.dto.HabitatReportDto;
import com.example.anml.dto.VeterinaryCheckDto;
import com.example.anml.exception.AsyncTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AnimalDossierService {

    private static final Logger log = LoggerFactory.getLogger(AnimalDossierService.class);

    private final AnimalService animalService;
    private final AnimalAsyncOperationsService asyncOperationsService;
    private final long defaultTimeoutMs;

    public AnimalDossierService(
            AnimalService animalService,
            AnimalAsyncOperationsService asyncOperationsService,
            @Value("${app.async.future-timeout-ms:3000}") long defaultTimeoutMs) {
        this.animalService = animalService;
        this.asyncOperationsService = asyncOperationsService;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public AnimalDto notifyKeepersAboutAnimal(Long animalId) {
        return notifyKeepersAboutAnimal(
                animalId,
                asyncOperationsService.getDefaultDelayMs(),
                false,
                null
        );
    }

    public AnimalDto notifyKeepersAboutAnimal(Long animalId,
                                              long delayMs,
                                              boolean simulateFailure,
                                              CountDownLatch completionLatch) {
        AnimalDto animal = animalService.findById(animalId);
        asyncOperationsService.sendKeeperNotification(animal, delayMs, simulateFailure, completionLatch);
        return animal;
    }

    public AnimalDossierDto buildAnimalDossier(Long animalId) {
        long delay = asyncOperationsService.getDefaultDelayMs();
        return buildAnimalDossier(animalId, delay, delay, false, false, defaultTimeoutMs);
    }

    public AnimalDossierDto buildAnimalDossier(Long animalId,
                                               long vetDelayMs,
                                               long habitatDelayMs,
                                               boolean simulateVetFailure,
                                               boolean simulateHabitatFailure,
                                               long timeoutMs) {
        AnimalDto animal = animalService.findById(animalId);

        CompletableFuture<VeterinaryCheckDto> vetFuture = asyncOperationsService
                .fetchVeterinaryStatus(animal.id(), vetDelayMs, simulateVetFailure)
                .exceptionally(ex -> {
                    log.warn("Помилка ветеринарного сервісу для id={}, використано fallback: {}",
                            animal.id(), rootMessage(ex));
                    return new VeterinaryCheckDto(
                            animal.id(),
                            "Ветеринарний статус тимчасово недоступний (fallback)",
                            Thread.currentThread().getName(),
                            true
                    );
                });

        CompletableFuture<HabitatReportDto> habitatFuture;
        try {
            habitatFuture = asyncOperationsService
                    .fetchHabitatReport(animal.habitat(), habitatDelayMs, simulateHabitatFailure)
                    .handle((report, ex) -> {
                        if (ex != null) {
                            log.warn("Помилка сервісу середовища '{}', використано fallback: {}",
                                    animal.habitat(), rootMessage(ex));
                            return new HabitatReportDto(
                                    animal.habitat(),
                                    "Звіт середовища тимчасово недоступний (fallback)",
                                    Thread.currentThread().getName(),
                                    true
                            );
                        }
                        return report;
                    });
        } catch (TaskRejectedException ex) {
            vetFuture.cancel(true);
            throw ex;
        }

        CompletableFuture<AnimalDossierDto> combinedFuture = vetFuture.thenCombine(
                habitatFuture,
                (vetCheck, habitatReport) -> new AnimalDossierDto(animal, vetCheck, habitatReport)
        );

        try {
            return combinedFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            vetFuture.cancel(true);
            habitatFuture.cancel(true);
            combinedFuture.cancel(true);
            throw new AsyncTimeoutException(
                    "Перевищено тайм-аут очікування асинхронних операцій (" + timeoutMs + " мс)", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Очікування асинхронного результату було перервано", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Помилка виконання асинхронної операції", ex.getCause());
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}