package com.example.anml.async;

import com.example.anml.config.AnimalAsyncExceptionHandler;
import com.example.anml.config.AsyncConfig;
import com.example.anml.controller.AnimalAsyncController;
import com.example.anml.dto.AnimalDto;
import com.example.anml.service.AnimalAsyncOperationsService;
import com.example.anml.service.AnimalDossierService;
import com.example.anml.service.AnimalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AnimalAsyncController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        }
)
@Import({
        AsyncConfig.class,
        AnimalAsyncExceptionHandler.class,
        AnimalAsyncOperationsService.class,
        AnimalDossierService.class
})
@TestPropertySource(properties = {
        "app.async.core-pool-size=1",
        "app.async.max-pool-size=1",
        "app.async.queue-capacity=1",
        "app.async.simulated-delay-ms=400"
})
class AnimalAsyncRejectionWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier(AsyncConfig.ANIMAL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor tinyExecutor;

    @Autowired
    private AnimalDossierService animalDossierService;

    @MockitoBean
    private AnimalService animalService;

    @BeforeEach
    void setUp() throws InterruptedException {
        when(animalService.findById(1L))
                .thenReturn(new AnimalDto(1L, "Сімба", "Лев африканський", 5, "Савана"));
        waitUntilExecutorIdle();
    }

    // Сценарій 6 (через HTTP): переповнення малого пулу повертає HTTP 503 із чітким повідомленням, а не 500
    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void whenTinyExecutorIsSaturated_httpEndpointReturns503WithClearMessageInsteadOf500() throws Exception {
        assertEquals(1, tinyExecutor.getCorePoolSize());
        assertEquals(1, tinyExecutor.getMaxPoolSize());
        assertEquals(1, tinyExecutor.getQueueCapacity());

        CountDownLatch activeAndQueuedLatch = new CountDownLatch(2);
        // Займаємо 1 активний потік і 1 єдине місце в черзі
        animalDossierService.notifyKeepersAboutAnimal(1L, 500, false, activeAndQueuedLatch);
        animalDossierService.notifyKeepersAboutAnimal(1L, 500, false, activeAndQueuedLatch);

        // 3-тє завдання переповнює виконавець -> TaskRejectedException -> AsyncExceptionHandler -> HTTP 503
        mockMvc.perform(post("/api/animals/1/notify").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Temporarily Overloaded"))
                .andExpect(jsonPath("$.message", containsString("спробуйте пізніше")));

        assertTrue(activeAndQueuedLatch.await(5, TimeUnit.SECONDS));
    }

    // Сценарій 6 (прямий виклик): переповнення малого пулу явно кидає TaskRejectedException
    @Test
    void whenTinyExecutorIsSaturated_directSubmissionThrowsTaskRejectedException() throws Exception {
        CountDownLatch activeAndQueuedLatch = new CountDownLatch(2);

        animalDossierService.notifyKeepersAboutAnimal(1L, 500, false, activeAndQueuedLatch);
        animalDossierService.notifyKeepersAboutAnimal(1L, 500, false, activeAndQueuedLatch);

        assertThrows(
                TaskRejectedException.class,
                () -> animalDossierService.notifyKeepersAboutAnimal(1L, 500, false, null)
        );

        assertTrue(activeAndQueuedLatch.await(5, TimeUnit.SECONDS));
    }

    // Перевірка штатної роботи контролера, коли пул не перевантажений
    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void whenExecutorHasCapacity_notifyEndpointReturns202Accepted() throws Exception {
        mockMvc.perform(post("/api/animals/1/notify").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.animalId").value(1));

        waitUntilExecutorIdle();
    }

    private void waitUntilExecutorIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while ((tinyExecutor.getActiveCount() > 0 || !tinyExecutor.getThreadPoolExecutor().getQueue().isEmpty())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }
}