package com.example.anml.quartz;

import com.example.anml.config.QuartzConfig;
import com.example.anml.service.AnimalMaintenanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuartzJobsTest {

    @Mock
    private AnimalMaintenanceService animalMaintenanceService;

    @Mock
    private JobExecutionContext context;

    @Test
    void jobsAreThin_delegateSingleCallToService_andTransactionIsOnServiceLevel() throws Exception {
        AnimalInventoryReportJob reportJob = new AnimalInventoryReportJob(animalMaintenanceService);
        JobDataMap reportData = new JobDataMap();
        reportData.put(AnimalInventoryReportJob.KEY_REPORT_TYPE, "WEEKLY_SENIOR");
        reportData.put(AnimalInventoryReportJob.KEY_MIN_AGE, 5);
        reportData.put(AnimalInventoryReportJob.KEY_INCLUDE_HABITAT_BREAKDOWN, true);
        when(context.getMergedJobDataMap()).thenReturn(reportData);

        reportJob.execute(context);

        verify(animalMaintenanceService, times(1)).generateInventoryReport("WEEKLY_SENIOR", 5, true);
        verifyNoMoreInteractions(animalMaintenanceService);

        AnimalDataCleanupJob cleanupJob = new AnimalDataCleanupJob(animalMaintenanceService);
        JobDataMap cleanupData = new JobDataMap();
        cleanupData.put(AnimalDataCleanupJob.KEY_HABITAT_FILTER, "Савана");
        cleanupData.put(AnimalDataCleanupJob.KEY_MAX_RECORDS, 50);
        when(context.getMergedJobDataMap()).thenReturn(cleanupData);

        cleanupJob.execute(context);

        verify(animalMaintenanceService, times(1)).cleanupAnimalRecords("Савана", 50);
        verifyNoMoreInteractions(animalMaintenanceService);

        assertFalse(AnimalInventoryReportJob.class.isAnnotationPresent(Transactional.class));
        assertFalse(AnimalDataCleanupJob.class.isAnnotationPresent(Transactional.class));
        Method reportMethod = AnimalMaintenanceService.class
                .getMethod("generateInventoryReport", String.class, int.class, boolean.class);
        Method cleanupMethod = AnimalMaintenanceService.class
                .getMethod("cleanupAnimalRecords", String.class, int.class);
        assertTrue(reportMethod.isAnnotationPresent(Transactional.class));
        assertTrue(cleanupMethod.isAnnotationPresent(Transactional.class));
    }

    @Test
    void missingOrMisspelledRequiredKey_throwsExplicitException_andMissingOptionalKeysUseDocumentedDefaults()
            throws Exception {
        AnimalInventoryReportJob reportJob = new AnimalInventoryReportJob(animalMaintenanceService);

        JobDataMap typoData = new JobDataMap();
        typoData.put("reportTyp", "DAILY");
        when(context.getMergedJobDataMap()).thenReturn(typoData);

        JobExecutionException missingKeyEx = assertThrows(
                JobExecutionException.class,
                () -> reportJob.execute(context)
        );
        assertTrue(missingKeyEx.getMessage().contains("reportType"));
        assertFalse(missingKeyEx.refireImmediately());
        verifyNoInteractions(animalMaintenanceService);

        JobDataMap minimalReportData = new JobDataMap();
        minimalReportData.put(AnimalInventoryReportJob.KEY_REPORT_TYPE, "DAILY");
        when(context.getMergedJobDataMap()).thenReturn(minimalReportData);

        reportJob.execute(context);
        verify(animalMaintenanceService).generateInventoryReport(
                "DAILY",
                AnimalInventoryReportJob.DEFAULT_MIN_AGE,
                AnimalInventoryReportJob.DEFAULT_INCLUDE_HABITAT_BREAKDOWN
        );

        AnimalDataCleanupJob cleanupJob = new AnimalDataCleanupJob(animalMaintenanceService);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        cleanupJob.execute(context);
        verify(animalMaintenanceService).cleanupAnimalRecords(
                AnimalDataCleanupJob.DEFAULT_HABITAT_FILTER,
                AnimalDataCleanupJob.DEFAULT_MAX_RECORDS
        );

        JobDataMap invalidCleanupData = new JobDataMap();
        invalidCleanupData.put(AnimalDataCleanupJob.KEY_MAX_RECORDS, -5);
        when(context.getMergedJobDataMap()).thenReturn(invalidCleanupData);

        JobExecutionException invalidMaxEx = assertThrows(
                JobExecutionException.class,
                () -> cleanupJob.execute(context)
        );
        assertTrue(invalidMaxEx.getMessage().contains("maxRecords"));
        assertFalse(invalidMaxEx.refireImmediately());
    }

    @Test
    void dataCleanupJob_disallowsConcurrentExecution() {
        assertTrue(AnimalDataCleanupJob.class.isAnnotationPresent(DisallowConcurrentExecution.class));

        QuartzConfig config = new QuartzConfig();
        JobDetail cleanupJobDetail = config.animalDataCleanupJobDetail();
        assertTrue(cleanupJobDetail.isConcurrentExecutionDisallowed());
    }

    @Test
    void reportJobFailure_retriesUpToMaxLimit_thenStopsRefiringWithoutInfiniteLoop() {
        AnimalInventoryReportJob reportJob = new AnimalInventoryReportJob(animalMaintenanceService);
        JobDataMap data = new JobDataMap();
        data.put(AnimalInventoryReportJob.KEY_REPORT_TYPE, "DAILY");
        when(context.getMergedJobDataMap()).thenReturn(data);
        when(animalMaintenanceService.generateInventoryReport(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new IllegalStateException("Тимчасова помилка БД"));

        when(context.getRefireCount()).thenReturn(0);
        JobExecutionException firstFailure = assertThrows(JobExecutionException.class, () -> reportJob.execute(context));
        assertTrue(firstFailure.refireImmediately());

        when(context.getRefireCount()).thenReturn(1);
        JobExecutionException secondFailure = assertThrows(JobExecutionException.class, () -> reportJob.execute(context));
        assertTrue(secondFailure.refireImmediately());

        when(context.getRefireCount()).thenReturn(AnimalInventoryReportJob.MAX_RETRIES);
        JobExecutionException finalFailure = assertThrows(JobExecutionException.class, () -> reportJob.execute(context));
        assertFalse(finalFailure.refireImmediately());
    }

    @Test
    void cleanupJobFailure_logsAndWaitsForNextScheduledFireWithoutRefireLoop() {
        AnimalDataCleanupJob cleanupJob = new AnimalDataCleanupJob(animalMaintenanceService);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        when(animalMaintenanceService.cleanupAnimalRecords(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("Збій під час очищення"));

        JobExecutionException ex = assertThrows(JobExecutionException.class, () -> cleanupJob.execute(context));
        assertFalse(ex.refireImmediately());
    }
}
