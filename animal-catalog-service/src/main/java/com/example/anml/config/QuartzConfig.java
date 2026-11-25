package com.example.anml.config;

import com.example.anml.quartz.AnimalDataCleanupJob;
import com.example.anml.quartz.AnimalInventoryReportJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class QuartzConfig {

    public static final String REPORTS_GROUP = "animal-reports";
    public static final String MAINTENANCE_GROUP = "animal-maintenance";
    public static final String SCHEDULER_TIME_ZONE_ID = "Europe/Kyiv";

    public static final JobKey INVENTORY_REPORT_JOB_KEY =
            JobKey.jobKey("animalInventoryReportJob", REPORTS_GROUP);
    public static final JobKey DATA_CLEANUP_JOB_KEY =
            JobKey.jobKey("animalDataCleanupJob", MAINTENANCE_GROUP);

    public static final TriggerKey DAILY_REPORT_TRIGGER_KEY =
            TriggerKey.triggerKey("dailyInventoryReportTrigger", REPORTS_GROUP);
    public static final TriggerKey WEEKLY_SENIOR_REPORT_TRIGGER_KEY =
            TriggerKey.triggerKey("weeklySeniorAnimalsReportTrigger", REPORTS_GROUP);
    public static final TriggerKey DATA_CLEANUP_TRIGGER_KEY =
            TriggerKey.triggerKey("nightlyAnimalDataCleanupTrigger", MAINTENANCE_GROUP);

    @Bean
    public JobDetail animalInventoryReportJobDetail() {
        return JobBuilder.newJob(AnimalInventoryReportJob.class)
                .withIdentity(INVENTORY_REPORT_JOB_KEY)
                .withDescription("Формування ідемпотентного звіту інвентаризації тварин")
                .usingJobData(AnimalInventoryReportJob.KEY_REPORT_TYPE, "DAILY")
                .usingJobData(AnimalInventoryReportJob.KEY_MIN_AGE, 0)
                .usingJobData(AnimalInventoryReportJob.KEY_INCLUDE_HABITAT_BREAKDOWN, true)
                .storeDurably()
                .build();
    }

    @Bean
    public CronTrigger dailyInventoryReportTrigger() {
        CronScheduleBuilder schedule = CronScheduleBuilder
                .cronSchedule("0 0 8 * * ?")
                .inTimeZone(TimeZone.getTimeZone(SCHEDULER_TIME_ZONE_ID))
                .withMisfireHandlingInstructionFireAndProceed();

        return TriggerBuilder.newTrigger()
                .forJob(INVENTORY_REPORT_JOB_KEY)
                .withIdentity(DAILY_REPORT_TRIGGER_KEY)
                .withDescription("Щоденний звіт інвентаризації о 08:00 (використовує дефолтні параметри Job)")
                .withSchedule(schedule)
                .build();
    }

    @Bean
    public CronTrigger weeklySeniorAnimalsReportTrigger() {
        CronScheduleBuilder schedule = CronScheduleBuilder
                .cronSchedule("0 0 9 ? * MON")
                .inTimeZone(TimeZone.getTimeZone(SCHEDULER_TIME_ZONE_ID))
                .withMisfireHandlingInstructionDoNothing();

        return TriggerBuilder.newTrigger()
                .forJob(INVENTORY_REPORT_JOB_KEY)
                .withIdentity(WEEKLY_SENIOR_REPORT_TRIGGER_KEY)
                .withDescription("Щотижневий звіт по старших тваринах (перевизначає reportType та minAge)")
                .usingJobData(AnimalInventoryReportJob.KEY_REPORT_TYPE, "WEEKLY_SENIOR")
                .usingJobData(AnimalInventoryReportJob.KEY_MIN_AGE, 5)
                .withSchedule(schedule)
                .build();
    }

    @Bean
    public JobDetail animalDataCleanupJobDetail() {
        return JobBuilder.newJob(AnimalDataCleanupJob.class)
                .withIdentity(DATA_CLEANUP_JOB_KEY)
                .withDescription("Ідемпотентне очищення зайвих пробілів у таблиці animals")
                .usingJobData(AnimalDataCleanupJob.KEY_HABITAT_FILTER, "")
                .usingJobData(AnimalDataCleanupJob.KEY_MAX_RECORDS, 100)
                .storeDurably()
                .build();
    }

    @Bean
    public CronTrigger animalDataCleanupTrigger() {
        CronScheduleBuilder schedule = CronScheduleBuilder
                .cronSchedule("0 30 2 * * ?")
                .inTimeZone(TimeZone.getTimeZone(SCHEDULER_TIME_ZONE_ID))
                .withMisfireHandlingInstructionDoNothing();

        return TriggerBuilder.newTrigger()
                .forJob(DATA_CLEANUP_JOB_KEY)
                .withIdentity(DATA_CLEANUP_TRIGGER_KEY)
                .withDescription("Нічне очищення записів о 02:30 (перевизначає maxRecords)")
                .usingJobData(AnimalDataCleanupJob.KEY_MAX_RECORDS, 50)
                .withSchedule(schedule)
                .build();
    }
}