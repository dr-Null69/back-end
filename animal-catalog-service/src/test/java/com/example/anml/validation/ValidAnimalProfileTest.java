package com.example.anml.validation;

import com.example.anml.dto.AnimalDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidAnimalProfileTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void whenValidAnimalProfiles_thenNoConstraintViolations() {
        AnimalDto lion = new AnimalDto(null, "Сімба", "Лев африканський", 5, "Савана");
        AnimalDto penguin = new AnimalDto(null, "Шкіпер", "Пінгвін імператорський", 3, "Антарктида");
        AnimalDto dolphin = new AnimalDto(null, "Фліппер", "Дельфін афаліна", 12, "Чорне море");

        assertTrue(validator.validate(lion).isEmpty(), "Лев у савані має бути валідним");
        assertTrue(validator.validate(penguin).isEmpty(), "Пінгвін в Антарктиді має бути валідним");
        assertTrue(validator.validate(dolphin).isEmpty(), "Дельфін у морі має бути валідним");
    }

    @Test
    void whenPolarSpeciesPlacedInDesert_thenProducesClearValidAnimalProfileViolation() {
        AnimalDto invalidPenguin = new AnimalDto(null, "Ковальські", "Пінгвін імператорський", 4, "Пустеля Сахара");

        Set<ConstraintViolation<AnimalDto>> violations = validator.validate(invalidPenguin);

        assertEquals(1, violations.size());
        ConstraintViolation<AnimalDto> violation = violations.iterator().next();
        assertEquals(ValidAnimalProfile.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("habitat", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("Пінгвін імператорський"));
        assertTrue(violation.getMessage().contains("Пустеля Сахара"));
        assertTrue(violation.getMessage().contains("полярним видам"));
    }

    @Test
    void whenAquaticSpeciesPlacedInSavanna_thenProducesClearHabitatViolation() {
        AnimalDto invalidDolphin = new AnimalDto(null, "Фліппер", "Дельфін", 8, "Савана");

        Set<ConstraintViolation<AnimalDto>> violations = validator.validate(invalidDolphin);

        assertEquals(1, violations.size());
        ConstraintViolation<AnimalDto> violation = violations.iterator().next();
        assertEquals(ValidAnimalProfile.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("habitat", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("водним видам"));
        assertTrue(violation.getMessage().contains("Дельфін"));
    }

    @Test
    void whenAgeExceedsSpeciesLifespan_thenProducesClearLifespanViolation() {
        AnimalDto oldHamster = new AnimalDto(null, "Хома", "Хом'як джунгарський", 25, "Клітка в зоопарку");

        Set<ConstraintViolation<AnimalDto>> violations = validator.validate(oldHamster);

        assertEquals(1, violations.size());
        ConstraintViolation<AnimalDto> violation = violations.iterator().next();
        assertEquals(ValidAnimalProfile.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("age", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("25"));
        assertTrue(violation.getMessage().contains("Хом'як джунгарський"));
        assertTrue(violation.getMessage().contains("5"));
    }

    @Test
    void whenMultipleBusinessRulesViolatedSimultaneously_thenReportsAllViolations() {
        AnimalDto invalidPenguin = new AnimalDto(null, "Ріко", "Пінгвін", 99, "Савана");

        Set<ConstraintViolation<AnimalDto>> violations = validator.validate(invalidPenguin);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().allMatch(v ->
                v.getConstraintDescriptor().getAnnotation().annotationType().equals(ValidAnimalProfile.class)));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("age")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("habitat")));
    }
}
