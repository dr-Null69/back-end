package com.example.anml.quartz;

import com.example.anml.service.AnimalMaintenanceService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class AnimalDataCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AnimalDataCleanupJob.class);

    public static final String KEY_HABITAT_FILTER = "habitatFilter";
    public static final String KEY_MAX_RECORDS = "maxRecords";

    public static final String DEFAULT_HABITAT_FILTER = "";
    public static final int DEFAULT_MAX_RECORDS = 100;

    private final AnimalMaintenanceService animalMaintenanceService;

    public AnimalDataCleanupJob(AnimalMaintenanceService animalMaintenanceService) {
        this.animalMaintenanceService = animalMaintenanceService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();

        String habitatFilter = dataMap.containsKey(KEY_HABITAT_FILTER) && dataMap.getString(KEY_HABITAT_FILTER) != null
                ? dataMap.getString(KEY_HABITAT_FILTER)
                : DEFAULT_HABITAT_FILTER;
        int maxRecords = readMaxRecords(dataMap);

        try {
            animalMaintenanceService.cleanupAnimalRecords(habitatFilter, maxRecords);
        } catch (Exception ex) {
            log.error("Помилка виконання AnimalDataCleanupJob (filter='{}', maxRecords={}); "
                    + "помилку зафіксовано, очікуємо наступного запланованого запуску", habitatFilter, maxRecords, ex);
            throw new JobExecutionException("Помилка очищення записів тварин", ex, false);
        }
    }

    private int readMaxRecords(JobDataMap dataMap) throws JobExecutionException {
        if (!dataMap.containsKey(KEY_MAX_RECORDS) || dataMap.get(KEY_MAX_RECORDS) == null) {
            return DEFAULT_MAX_RECORDS;
        }
        try {
            int value = dataMap.getInt(KEY_MAX_RECORDS);
            if (value <= 0) {
                throw new JobExecutionException("Параметр JobDataMap '" + KEY_MAX_RECORDS + "' має бути > 0", false);
            }
            return value;
        } catch (JobExecutionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new JobExecutionException("Некоректне значення параметра JobDataMap '" + KEY_MAX_RECORDS + "'", ex, false);
        }
    }
}