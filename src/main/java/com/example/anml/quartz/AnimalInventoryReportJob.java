package com.example.anml.quartz;

import com.example.anml.service.AnimalMaintenanceService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AnimalInventoryReportJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AnimalInventoryReportJob.class);

    public static final String KEY_REPORT_TYPE = "reportType";
    public static final String KEY_MIN_AGE = "minAge";
    public static final String KEY_INCLUDE_HABITAT_BREAKDOWN = "includeHabitatBreakdown";

    public static final int DEFAULT_MIN_AGE = 0;
    public static final boolean DEFAULT_INCLUDE_HABITAT_BREAKDOWN = true;
    public static final int MAX_RETRIES = 2;

    private final AnimalMaintenanceService animalMaintenanceService;

    public AnimalInventoryReportJob(AnimalMaintenanceService animalMaintenanceService) {
        this.animalMaintenanceService = animalMaintenanceService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();

        String reportType = readRequiredString(dataMap, KEY_REPORT_TYPE);
        int minAge = readIntWithDefault(dataMap, KEY_MIN_AGE, DEFAULT_MIN_AGE);
        boolean includeHabitatBreakdown = readBooleanWithDefault(
                dataMap, KEY_INCLUDE_HABITAT_BREAKDOWN, DEFAULT_INCLUDE_HABITAT_BREAKDOWN);

        try {
            animalMaintenanceService.generateInventoryReport(reportType, minAge, includeHabitatBreakdown);
        } catch (Exception ex) {
            int refireCount = context.getRefireCount();
            if (refireCount < MAX_RETRIES) {
                log.warn("Помилка виконання AnimalInventoryReportJob (спроба {}/{}): повторний запуск",
                        refireCount + 1, MAX_RETRIES + 1, ex);
                throw new JobExecutionException("Тимчасова помилка генерації звіту, виконується повтор", ex, true);
            }
            log.error("Вичерпано ліміт повторних спроб ({}) для AnimalInventoryReportJob; очікуємо наступного запуску за розкладом",
                    MAX_RETRIES, ex);
            throw new JobExecutionException("Помилка генерації звіту після всіх спроб", ex, false);
        }
    }

    private String readRequiredString(JobDataMap dataMap, String key) throws JobExecutionException {
        if (!dataMap.containsKey(key)) {
            throw new JobExecutionException("Відсутній обов'язковий параметр JobDataMap: '" + key + "'", false);
        }
        String value = dataMap.getString(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Обов'язковий параметр JobDataMap '" + key + "' не може бути порожнім", false);
        }
        return value;
    }

    private int readIntWithDefault(JobDataMap dataMap, String key, int defaultValue) throws JobExecutionException {
        if (!dataMap.containsKey(key) || dataMap.get(key) == null) {
            return defaultValue;
        }
        try {
            return dataMap.getInt(key);
        } catch (RuntimeException ex) {
            throw new JobExecutionException("Некоректне числове значення параметра JobDataMap '" + key + "'", ex, false);
        }
    }

    private boolean readBooleanWithDefault(JobDataMap dataMap, String key, boolean defaultValue) {
        if (!dataMap.containsKey(key) || dataMap.get(key) == null) {
            return defaultValue;
        }
        return dataMap.getBoolean(key);
    }
}