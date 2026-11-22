Лабораторна робота №5 — Асинхронна обробка

1. Пул потоків і обробка помилок void @Async
   - AsyncConfig — обмежує ресурси через ThreadPoolTaskExecutor (animalTaskExecutor: core=2, max=4, queue=10, префікс animal-async-, AbortPolicy) для контролю навантаження.
   - AnimalAsyncExceptionHandler — логує та фіксує помилки з void @Async.

2. 3 асинхронні операції (AnimalAsyncOperationsService)
   - sendKeeperNotification (void, fire-and-forget) — неблокуюче сповіщення доглядачів.
   - fetchVeterinaryStatus (CompletableFuture) — повільний запит до ветеринарного сервісу.
   - fetchHabitatReport (CompletableFuture) — повільна генерація звіту по середовищу.

3. Оркестрація, стійкість і API
   - AnimalDossierService — запускає обидва CompletableFuture паралельно, страхує їх через exceptionally/handle (fallback), об'єднує через thenCombine і обмежує очікування тайм-аутом (AsyncTimeoutException).
   - AnimalAsyncController — POST /api/animals/{id}/notify (202 Accepted) і GET /api/animals/{id}/dossier.
   - AsyncExceptionHandler — перетворює TaskRejectedException на HTTP 503 («спробуйте пізніше») та AsyncTimeoutException на HTTP 504 замість 500.

4. Тести
   - AnimalAsyncProcessingTest — перевіряє fire-and-forget, пул і префікс animal-async-, паралельне виконання, fallback, AsyncUncaughtExceptionHandler і тайм-аут.
   - AnimalAsyncRejectionWebMvcTest — на малому пулі (1/1/1) перевіряє TaskRejectedException → HTTP 503 та 202 Accepted.


Лабораторна робота №6 — Quartz Scheduler

1. Зберігання розкладу в PostgreSQL через Liquibase
   - pom.xml — додано spring-boot-starter-quartz (Quartz 2.5.2).
   - 002-create-quartz-tables.sql + db.changelog-master.yaml — створення всіх 11 таблиць QRTZ_* через Liquibase.
   - application.yml — налаштовано JDBC JobStore (PostgreSQLDelegate) та initialize-schema: never.

2. Конфігурація завдань і тригерів (QuartzConfig)
   - Оголошено 2 JobDetail (з дефолтним JobDataMap) і 3 CronTrigger в одному @Configuration з явними name, group і прив'язкою через JobKey.
   - AnimalInventoryReportJob має 2 тригери (щоденний і щотижневий, який перевизначає reportType та minAge); AnimalDataCleanupJob має нічний тригер (перевизначає maxRecords).
   - Для кожного тригера задано явну misfire instruction (FireAndProceed / DoNothing) і часовий пояс Europe/Kyiv.

3. «Легкі» Jobs та ідемпотентний сервіс
   - AnimalMaintenanceService — транзакційна (@Transactional) та ідемпотентна логіка: generateInventoryReport і cleanupAnimalRecords (повторний запуск нічого не змінює).
   - AnimalInventoryReportJob — читає JobDataMap (явна помилка при відсутньому reportType), викликає сервіс і робить обмежені повтори при збої (MAX_RETRIES=2, без нескінченного циклу).
   - AnimalDataCleanupJob — позначений @DisallowConcurrentExecution, викликає сервіс і при збої логує помилку без негайного повтору.

4. Тести
   - QuartzConfigurationAndLiquibaseTest — перевіряє JobDetail/Trigger, JobKey, пріоритет JobDataMap тригера, misfire instruction, TimeZone і Liquibase QRTZ_* + initialize-schema: never.
   - QuartzJobsTest — перевіряє делегування в сервіс і @Transactional, помилкові/відсутні ключі JobDataMap, @DisallowConcurrentExecution і обробку збоїв.
   - AnimalMaintenanceServiceIdempotencyTest — перевіряє ідемпотентність обох методів сервісу.
