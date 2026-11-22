package com.example.anml.service;

import com.example.anml.entity.Animal;
import com.example.anml.repository.AnimalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AnimalMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(AnimalMaintenanceService.class);

    public record InventoryReport(
            String reportKey,
            LocalDate reportDate,
            String reportType,
            int minAge,
            long matchingAnimals,
            Map<String, Long> habitatBreakdown
    ) {
    }

    public record ReportGenerationResult(InventoryReport report, boolean changed) {
    }

    private final AnimalRepository animalRepository;
    private final ConcurrentHashMap<String, InventoryReport> generatedReports = new ConcurrentHashMap<>();

    public AnimalMaintenanceService(AnimalRepository animalRepository) {
        this.animalRepository = animalRepository;
    }

    @Transactional(readOnly = true)
    public ReportGenerationResult generateInventoryReport(String reportType,
                                                          int minAge,
                                                          boolean includeHabitatBreakdown) {
        if (reportType == null || reportType.isBlank()) {
            throw new IllegalArgumentException("Тип звіту (reportType) не може бути порожнім");
        }
        if (minAge < 0) {
            throw new IllegalArgumentException("Мінімальний вік (minAge) не може бути від'ємним: " + minAge);
        }

        List<Animal> matching = animalRepository.findAll().stream()
                .filter(animal -> animal.getAge() != null && animal.getAge() >= minAge)
                .toList();

        Map<String, Long> breakdown = includeHabitatBreakdown
                ? matching.stream().collect(Collectors.groupingBy(
                animal -> animal.getHabitat() != null ? animal.getHabitat() : "Невідомо",
                TreeMap::new,
                Collectors.counting()))
                : Collections.emptyMap();

        LocalDate today = LocalDate.now();
        String key = today + ":" + reportType + ":" + minAge;
        InventoryReport candidate = new InventoryReport(
                key,
                today,
                reportType,
                minAge,
                matching.size(),
                Map.copyOf(breakdown)
        );

        InventoryReport existing = generatedReports.get(key);
        if (candidate.equals(existing)) {
            log.info("Звіт '{}' уже актуальний (змін немає): знайдено {} тварин", key, candidate.matchingAnimals());
            return new ReportGenerationResult(existing, false);
        }

        generatedReports.put(key, candidate);
        log.info("Сформовано/оновлено звіт '{}': знайдено {} тварин (minAge={})",
                key, candidate.matchingAnimals(), minAge);
        return new ReportGenerationResult(candidate, true);
    }

    @Transactional
    public int cleanupAnimalRecords(String habitatFilter, int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("Ліміт записів (maxRecords) має бути додатним: " + maxRecords);
        }

        String normalizedFilter = habitatFilter == null ? "" : habitatFilter.trim().toLowerCase(Locale.ROOT);
        List<Animal> animals = animalRepository.findAll();

        int processed = 0;
        int updatedCount = 0;
        for (Animal animal : animals) {
            if (processed >= maxRecords) {
                break;
            }
            if (!normalizedFilter.isEmpty()) {
                String habitat = animal.getHabitat() == null ? "" : animal.getHabitat().toLowerCase(Locale.ROOT);
                if (!habitat.contains(normalizedFilter)) {
                    continue;
                }
            }
            processed++;

            String cleanName = cleanSpaces(animal.getName());
            String cleanSpecies = cleanSpaces(animal.getSpecies());
            String cleanHabitat = cleanSpaces(animal.getHabitat());

            boolean modified = !Objects.equals(cleanName, animal.getName())
                    || !Objects.equals(cleanSpecies, animal.getSpecies())
                    || !Objects.equals(cleanHabitat, animal.getHabitat());

            if (modified) {
                animal.setName(cleanName);
                animal.setSpecies(cleanSpecies);
                animal.setHabitat(cleanHabitat);
                animalRepository.save(animal);
                updatedCount++;
            }
        }

        log.info("Очищення записів тварин завершено (filter='{}', maxRecords={}): оновлено {} записів",
                normalizedFilter, maxRecords, updatedCount);
        return updatedCount;
    }

    public Map<String, InventoryReport> getGeneratedReports() {
        return Map.copyOf(generatedReports);
    }

    public void clearGeneratedReports() {
        generatedReports.clear();
    }

    private String cleanSpaces(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}