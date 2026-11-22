package com.example.anml.quartz;

import com.example.anml.config.QuartzConfig;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(classes = QuartzConfig.class)
class QuartzConfigurationAndLiquibaseTest {

    @Autowired
    private List<JobDetail> jobDetails;

    @Autowired
    private List<Trigger> triggers;

    @Autowired
    private JobDetail animalInventoryReportJobDetail;

    @Autowired
    private CronTrigger dailyInventoryReportTrigger;

    @Autowired
    private CronTrigger weeklySeniorAnimalsReportTrigger;

    @Autowired
    private JobDetail animalDataCleanupJobDetail;

    @Autowired
    private CronTrigger animalDataCleanupTrigger;

    @Test
    void allJobsAndTriggers_haveExplicitNamesGroupsAndValidJobKeys_andOneJobHasTwoTriggers() {
        assertEquals(2, jobDetails.size());
        assertEquals(3, triggers.size());

        Set<JobKey> declaredJobKeys = jobDetails.stream()
                .map(JobDetail::getKey)
                .collect(Collectors.toSet());

        for (JobDetail job : jobDetails) {
            assertNotNull(job.getKey().getName());
            assertFalse(job.getKey().getName().isBlank());
            assertNotNull(job.getKey().getGroup());
            assertNotEquals("DEFAULT", job.getKey().getGroup());
            assertTrue(job.isDurable());
        }

        for (Trigger trigger : triggers) {
            assertNotNull(trigger.getKey().getName());
            assertFalse(trigger.getKey().getName().isBlank());
            assertNotNull(trigger.getKey().getGroup());
            assertNotEquals("DEFAULT", trigger.getKey().getGroup());
            assertTrue(declaredJobKeys.contains(trigger.getJobKey()),
                    "Тригер " + trigger.getKey() + " має вказувати на існуючий JobKey");
        }

        long triggersForInventoryJob = triggers.stream()
                .filter(t -> t.getJobKey().equals(QuartzConfig.INVENTORY_REPORT_JOB_KEY))
                .count();
        assertEquals(2, triggersForInventoryJob, "AnimalInventoryReportJob має мати 2 тригери");
    }

    @Test
    void triggerJobDataMap_overridesJobDefaults_whileKeepingUnspecifiedJobKeys() {
        JobDataMap mergedWeekly = new JobDataMap();
        mergedWeekly.putAll(animalInventoryReportJobDetail.getJobDataMap());
        mergedWeekly.putAll(weeklySeniorAnimalsReportTrigger.getJobDataMap());

        assertEquals("WEEKLY_SENIOR", mergedWeekly.getString(AnimalInventoryReportJob.KEY_REPORT_TYPE));
        assertEquals(5, mergedWeekly.getInt(AnimalInventoryReportJob.KEY_MIN_AGE));
        assertTrue(mergedWeekly.getBoolean(AnimalInventoryReportJob.KEY_INCLUDE_HABITAT_BREAKDOWN));

        JobDataMap mergedCleanup = new JobDataMap();
        mergedCleanup.putAll(animalDataCleanupJobDetail.getJobDataMap());
        mergedCleanup.putAll(animalDataCleanupTrigger.getJobDataMap());

        assertEquals(50, mergedCleanup.getInt(AnimalDataCleanupJob.KEY_MAX_RECORDS));
        assertEquals("", mergedCleanup.getString(AnimalDataCleanupJob.KEY_HABITAT_FILTER));
    }

    @Test
    void everyTrigger_hasExplicitMisfireInstruction() {
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW,
                dailyInventoryReportTrigger.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
                weeklySeniorAnimalsReportTrigger.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING,
                animalDataCleanupTrigger.getMisfireInstruction());

        for (Trigger trigger : triggers) {
            assertNotEquals(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY, trigger.getMisfireInstruction(),
                    "Для тригера " + trigger.getKey() + " має бути явно задана misfire instruction");
        }
    }

    @Test
    void allCronTriggers_haveExplicitValidTimeZone_notFallbackGmt() {
        List<CronTrigger> cronTriggers = List.of(
                dailyInventoryReportTrigger,
                weeklySeniorAnimalsReportTrigger,
                animalDataCleanupTrigger
        );

        for (CronTrigger cronTrigger : cronTriggers) {
            TimeZone tz = cronTrigger.getTimeZone();
            assertNotNull(tz);
            assertEquals(QuartzConfig.SCHEDULER_TIME_ZONE_ID, tz.getID());
            assertNotEquals("GMT", tz.getID(), "Невідомий ID часового поясу замінюється на GMT");
        }

        assertEquals("GMT", TimeZone.getTimeZone("Invalid/Timezone_ID").getID());
    }

    @Test
    void quartzJdbcStoreAndLiquibaseMigration_areConfiguredWithInitializeSchemaNever() throws Exception {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertNotNull(props);
        assertEquals("jdbc", props.getProperty("spring.quartz.job-store-type"));
        assertEquals("never", props.getProperty("spring.quartz.jdbc.initialize-schema"));
        assertEquals("org.quartz.impl.jdbcjobstore.PostgreSQLDelegate",
                props.getProperty("spring.quartz.properties.org.quartz.jobStore.driverDelegateClass"));
        assertEquals("true", props.getProperty("spring.liquibase.enabled"));

        String masterChangelog = new ClassPathResource("db/changelog/db.changelog-master.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(masterChangelog.contains("db/changelog/002-create-quartz-tables.sql"));

        String quartzSql = new ClassPathResource("db/changelog/002-create-quartz-tables.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> expectedTables = List.of(
                "QRTZ_JOB_DETAILS",
                "QRTZ_TRIGGERS",
                "QRTZ_SIMPLE_TRIGGERS",
                "QRTZ_CRON_TRIGGERS",
                "QRTZ_SIMPROP_TRIGGERS",
                "QRTZ_BLOB_TRIGGERS",
                "QRTZ_CALENDARS",
                "QRTZ_PAUSED_TRIGGER_GRPS",
                "QRTZ_FIRED_TRIGGERS",
                "QRTZ_SCHEDULER_STATE",
                "QRTZ_LOCKS"
        );
        for (String table : expectedTables) {
            assertTrue(quartzSql.contains("CREATE TABLE " + table),
                    "Liquibase changeset має створювати таблицю " + table);
        }
    }
}
