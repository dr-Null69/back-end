package com.example.anml.aop;

import com.example.anml.dto.AnimalDto;
import com.example.anml.entity.Animal;
import com.example.anml.exception.RateLimitExceededException;
import com.example.anml.mapper.AnimalMapper;
import com.example.anml.repository.AnimalRepository;
import com.example.anml.service.AnimalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = AnimalServiceAopProxyTest.AopTestConfig.class)
class AnimalServiceAopProxyTest {

    @Configuration
    @EnableAspectJAutoProxy
    @Import({
            AnimalService.class,
            RetryOnFailureAspect.class,
            RateLimitAspect.class,
            NormalizeAnimalOutputAspect.class
    })
    static class AopTestConfig {
    }

    @Autowired
    private AnimalService animalService;

    @Autowired
    private RateLimitAspect rateLimitAspect;

    @MockitoBean
    private AnimalRepository animalRepository;

    @MockitoBean
    private AnimalMapper animalMapper;

    @BeforeEach
    void resetRateLimiter() {
        rateLimitAspect.reset();
    }

    @Test
    void externalCall_activatesNormalizeAnimalOutputAspect_throughSpringProxy() {
        assertTrue(AopUtils.isAopProxy(animalService), "AnimalService має бути обгорнутий у Spring AOP проксі");

        Animal entity = new Animal(1L, "  сімба   король  ", "  лев   африканський ", 5, "  савана  ");
        AnimalDto rawDto = new AnimalDto(1L, "  сімба   король  ", "  лев   африканський ", 5, "  савана  ");
        when(animalRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(rawDto);

        AnimalDto result = animalService.findById(1L);

        assertEquals("Сімба король", result.name());
        assertEquals("Лев африканський", result.species());
        assertEquals("Савана", result.habitat());
    }

    @Test
    void selfInvocation_bypassesNormalizeAnimalOutputAspect_withoutErrors() {
        Animal entity = new Animal(1L, "  сімба   король  ", "  лев   африканський ", 5, "  савана  ");
        AnimalDto rawDto = new AnimalDto(1L, "  сімба   король  ", "  лев   африканський ", 5, "  савана  ");
        when(animalRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(rawDto);

        AnimalDto result = animalService.findByIdViaSelfInvocation(1L);

        assertEquals("  сімба   король  ", result.name());
        assertEquals("  лев   африканський ", result.species());
        assertEquals("  савана  ", result.habitat());
    }

    @Test
    void externalCall_activatesRetryOnFailureAspect_onTransientError() {
        Animal entity = new Animal(2L, "Балу", "Ведмідь бурий", 8, "Карпатський ліс");
        AnimalDto dto = new AnimalDto(2L, "Балу", "Ведмідь бурий", 8, "Карпатський ліс");
        when(animalRepository.findAll())
                .thenThrow(new IllegalStateException("Тимчасовий збій з'єднання"))
                .thenReturn(List.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(dto);

        List<AnimalDto> result = animalService.findAll();

        assertEquals(1, result.size());
        assertEquals("Балу", result.get(0).name());
        verify(animalRepository, times(2)).findAll();
    }

    @Test
    void externalCall_activatesRateLimitAspect_andBlocksFourthCall() {
        when(animalRepository.count()).thenReturn(7L);

        assertEquals(7L, animalService.count());
        assertEquals(7L, animalService.count());
        assertEquals(7L, animalService.count());

        assertThrows(RateLimitExceededException.class, () -> animalService.count());
        verify(animalRepository, times(3)).count();
    }
}