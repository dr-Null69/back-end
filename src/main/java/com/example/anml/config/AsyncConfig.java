package com.example.anml.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфігурація асинхронної обробки з явно обмеженим пулом потоків.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String ANIMAL_TASK_EXECUTOR = "animalTaskExecutor";
    public static final String THREAD_NAME_PREFIX = "animal-async-";

    private final AnimalAsyncExceptionHandler asyncExceptionHandler;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    public AsyncConfig(
            AnimalAsyncExceptionHandler asyncExceptionHandler,
            @Value("${app.async.core-pool-size:2}") int corePoolSize,
            @Value("${app.async.max-pool-size:4}") int maxPoolSize,
            @Value("${app.async.queue-capacity:10}") int queueCapacity) {
        this.asyncExceptionHandler = asyncExceptionHandler;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    @Bean(name = ANIMAL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor animalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return animalTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return asyncExceptionHandler;
    }
}