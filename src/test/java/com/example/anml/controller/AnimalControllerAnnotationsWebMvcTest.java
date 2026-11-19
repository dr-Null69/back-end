package com.example.anml.controller;

import com.example.anml.dto.AnimalDto;
import com.example.anml.service.AnimalService;
import com.example.anml.web.PostCreatedMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AnimalController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        }
)
class AnimalControllerAnnotationsWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void postCreatedMapping_preservesMergedPostMappingAndCreatedResponseStatus() throws NoSuchMethodException {
        Method createMethod = AnimalController.class.getMethod("create", AnimalDto.class);

        assertTrue(createMethod.isAnnotationPresent(PostCreatedMapping.class));
        assertFalse(createMethod.isAnnotationPresent(RequestMapping.class));
        assertFalse(createMethod.isAnnotationPresent(PostMapping.class));
        assertFalse(createMethod.isAnnotationPresent(ResponseStatus.class));

        RequestMapping mergedMapping = AnnotatedElementUtils.findMergedAnnotation(createMethod, RequestMapping.class);
        ResponseStatus mergedStatus = AnnotatedElementUtils.findMergedAnnotation(createMethod, ResponseStatus.class);

        assertNotNull(mergedMapping);
        assertArrayEquals(new RequestMethod[]{RequestMethod.POST}, mergedMapping.method());
        assertArrayEquals(new String[]{"/animals"}, mergedMapping.path());
        assertNotNull(mergedStatus);
        assertEquals(HttpStatus.CREATED, mergedStatus.code());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void createEndpoint_withPostCreatedMappingAndValidDto_returns201Created() throws Exception {
        AnimalDto saved = new AnimalDto(10L, "Сімба", "Лев африканський", 5, "Савана");
        when(animalService.create(any(AnimalDto.class))).thenReturn(saved);
        when(animalService.findAll()).thenReturn(List.of(saved));

        mockMvc.perform(post("/api/animals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Сімба",
                                  "species": "Лев африканський",
                                  "age": 5,
                                  "habitat": "Савана"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Сімба"))
                .andExpect(jsonPath("$.species").value("Лев африканський"));

        verify(animalService).create(any(AnimalDto.class));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void createEndpoint_whenPolarSpeciesInDesert_returns400WithClearValidationMessage() throws Exception {
        mockMvc.perform(post("/api/animals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Шкіпер",
                                  "species": "Пінгвін імператорський",
                                  "age": 3,
                                  "habitat": "Пустеля"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages[0]", containsString("полярним видам")));

        verify(animalService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void createEndpoint_whenAgeExceedsLifespan_returns400WithClearValidationMessage() throws Exception {
        mockMvc.perform(post("/api/animals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Хома",
                                  "species": "Хом'як",
                                  "age": 25,
                                  "habitat": "Вольєр"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages[0]", containsString("максимум 5 років")));

        verify(animalService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void updateEndpoint_whenAquaticSpeciesInSavanna_returns400AndSkipsService() throws Exception {
        mockMvc.perform(put("/api/animals/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Фліппер",
                                  "species": "Дельфін",
                                  "age": 6,
                                  "habitat": "Савана"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]", containsString("водним видам")));

        verify(animalService, never()).update(any(), any());
    }

    @Test
    @WithMockUser(username = "keeper_anna", roles = {"USER", "ADMIN"})
    void meEndpoint_usesResolvedCurrentUserInResponseBody() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("keeper_anna"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_ADMIN", "ROLE_USER")));
    }

    @Test
    @WithMockUser(username = "chief_admin", roles = "ADMIN")
    void adminStatsEndpoint_usesResolvedCurrentUserInResponseBody() throws Exception {
        when(animalService.count()).thenReturn(3L);

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnimals").value(3))
                .andExpect(jsonPath("$.requestedBy").value("chief_admin"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }
}
