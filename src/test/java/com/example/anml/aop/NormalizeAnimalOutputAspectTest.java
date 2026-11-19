package com.example.anml.aop;

import com.example.anml.dto.AnimalDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalizeAnimalOutputAspectTest {

    private final NormalizeAnimalOutputAspect aspect = new NormalizeAnimalOutputAspect();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    void whenSingleAnimalDtoReturnedWithCapitalizeTrue_normalizesSpacesAndCapitalizesFirstLetter() throws Throwable {
        NormalizeAnimalOutput annotation = getAnnotation("capitalizeMethod");
        AnimalDto raw = new AnimalDto(1L, "  сімба   король  ", "  лев   африканський ", 5, "  савана  ");
        when(joinPoint.proceed()).thenReturn(raw);

        AnimalDto result = (AnimalDto) aspect.normalizeResult(joinPoint, annotation);

        assertEquals(new AnimalDto(1L, "Сімба король", "Лев африканський", 5, "Савана"), result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void whenCapitalizeFalse_onlyTrimsAndCollapsesSpaces() throws Throwable {
        NormalizeAnimalOutput annotation = getAnnotation("trimOnlyMethod");
        AnimalDto raw = new AnimalDto(2L, "  балу   ведмідь ", "  ведмідь  ", 8, "  ліс ");
        when(joinPoint.proceed()).thenReturn(raw);

        AnimalDto result = (AnimalDto) aspect.normalizeResult(joinPoint, annotation);

        assertEquals(new AnimalDto(2L, "балу ведмідь", "ведмідь", 8, "ліс"), result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenListOfAnimalDtosReturned_normalizesEachElement() throws Throwable {
        NormalizeAnimalOutput annotation = getAnnotation("capitalizeMethod");
        when(joinPoint.proceed()).thenReturn(List.of(
                new AnimalDto(1L, "  шкіпер ", " пінгвін ", 3, " антарктида ")
        ));

        List<AnimalDto> result = (List<AnimalDto>) aspect.normalizeResult(joinPoint, annotation);

        assertEquals(1, result.size());
        assertEquals("Шкіпер", result.get(0).name());
        assertEquals("Пінгвін", result.get(0).species());
        assertEquals("Антарктида", result.get(0).habitat());
    }

    @Test
    void whenNullReturned_returnsNullSafely() throws Throwable {
        NormalizeAnimalOutput annotation = getAnnotation("capitalizeMethod");
        when(joinPoint.proceed()).thenReturn(null);

        assertNull(aspect.normalizeResult(joinPoint, annotation));
        verify(joinPoint, times(1)).proceed();
    }

    private NormalizeAnimalOutput getAnnotation(String methodName) throws NoSuchMethodException {
        return AnnotatedTargets.class.getDeclaredMethod(methodName).getAnnotation(NormalizeAnimalOutput.class);
    }

    @SuppressWarnings("unused")
    private static final class AnnotatedTargets {
        @NormalizeAnimalOutput
        void capitalizeMethod() {
        }

        @NormalizeAnimalOutput(capitalizeFirst = false)
        void trimOnlyMethod() {
        }
    }
}