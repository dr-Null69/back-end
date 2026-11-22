package com.example.anml.aop;

import com.example.anml.dto.AnimalDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
public class NormalizeAnimalOutputAspect {

    @Around("@annotation(normalizeAnimalOutput)")
    public Object normalizeResult(ProceedingJoinPoint joinPoint,
                                  NormalizeAnimalOutput normalizeAnimalOutput) throws Throwable {
        Object result = joinPoint.proceed();
        boolean capitalizeFirst = normalizeAnimalOutput.capitalizeFirst();

        if (result instanceof AnimalDto dto) {
            return normalizeDto(dto, capitalizeFirst);
        }

        if (result instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof AnimalDto dto ? normalizeDto(dto, capitalizeFirst) : item)
                    .toList();
        }

        return result;
    }

    private AnimalDto normalizeDto(AnimalDto dto, boolean capitalizeFirst) {
        return new AnimalDto(
                dto.id(),
                normalizeText(dto.name(), capitalizeFirst),
                normalizeText(dto.species(), capitalizeFirst),
                dto.age(),
                normalizeText(dto.habitat(), capitalizeFirst)
        );
    }

    private String normalizeText(String value, boolean capitalizeFirst) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty() || !capitalizeFirst) {
            return collapsed;
        }
        return Character.toUpperCase(collapsed.charAt(0)) + collapsed.substring(1);
    }
}