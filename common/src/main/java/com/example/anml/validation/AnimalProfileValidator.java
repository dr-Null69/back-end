package com.example.anml.validation;

import com.example.anml.dto.AnimalDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.Locale;

public class AnimalProfileValidator implements ConstraintValidator<ValidAnimalProfile, AnimalDto> {

    private static final int DEFAULT_MAX_AGE = 150;

    private record SpeciesLifespanRule(List<String> keywords, int maxAge) {
    }

    private static final List<SpeciesLifespanRule> LIFESPAN_RULES = List.of(
            new SpeciesLifespanRule(List.of("хом'як", "хомяк", "hamster"), 5),
            new SpeciesLifespanRule(List.of("вовк", "wolf"), 20),
            new SpeciesLifespanRule(List.of("пантера", "panther", "собака", "dog"), 25),
            new SpeciesLifespanRule(List.of("лев", "lion", "пінгвін", "penguin", "кіт", "cat"), 30),
            new SpeciesLifespanRule(List.of("ведмідь", "bear"), 45),
            new SpeciesLifespanRule(List.of("дельфін", "dolphin"), 60),
            new SpeciesLifespanRule(List.of("слон", "elephant"), 80),
            new SpeciesLifespanRule(List.of("черепаха", "turtle", "tortoise"), 200)
    );

    private static final List<String> AQUATIC_SPECIES = List.of(
            "дельфін", "кит", "акула", "риба", "dolphin", "whale", "shark", "fish"
    );

    private static final List<String> TERRESTRIAL_OR_ARID_HABITATS = List.of(
            "пустел", "саван", "степ", "ліс", "джунгл", "гор",
            "desert", "savanna", "forest", "jungle", "mountain"
    );

    private static final List<String> POLAR_SPECIES = List.of(
            "пінгвін", "білий ведмідь", "морж", "penguin", "polar bear", "walrus"
    );

    private static final List<String> HOT_OR_TROPICAL_HABITATS = List.of(
            "пустел", "саван", "джунгл", "тропік",
            "desert", "savanna", "jungle", "tropic"
    );

    @Override
    public boolean isValid(AnimalDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        String normalizedSpecies = normalize(dto.species());
        String normalizedHabitat = normalize(dto.habitat());

        boolean valid = validateAge(dto, normalizedSpecies, context);
        if (!validateHabitat(dto, normalizedSpecies, normalizedHabitat, context)) {
            valid = false;
        }
        return valid;
    }

    private boolean validateAge(AnimalDto dto, String normalizedSpecies, ConstraintValidatorContext context) {
        Integer age = dto.age();
        if (age == null) {
            return true;
        }
        if (age < 0) {
            addViolation(context, "age",
                    "Вік тварини не може бути від'ємним (передано: " + age + ")");
            return false;
        }
        if (normalizedSpecies == null || normalizedSpecies.isBlank()) {
            return true;
        }

        int maxAge = resolveMaxLifespan(normalizedSpecies);
        if (age > maxAge) {
            addViolation(context, "age", String.format(
                    "Вік %d років перевищує максимально реалістичну тривалість життя для виду '%s' (максимум %d років)",
                    age, dto.species(), maxAge));
            return false;
        }
        return true;
    }

    private boolean validateHabitat(AnimalDto dto,
                                    String normalizedSpecies,
                                    String normalizedHabitat,
                                    ConstraintValidatorContext context) {
        if (normalizedSpecies == null || normalizedSpecies.isBlank()
                || normalizedHabitat == null || normalizedHabitat.isBlank()) {
            return true;
        }

        if (containsAny(normalizedSpecies, AQUATIC_SPECIES)
                && containsAny(normalizedHabitat, TERRESTRIAL_OR_ARID_HABITATS)) {
            addViolation(context, "habitat", String.format(
                    "Вид '%s' не може проживати в середовищі '%s': водним видам потрібне водне середовище (океан, море, річка, озеро, акваріум)",
                    dto.species(), dto.habitat()));
            return false;
        }

        if (containsAny(normalizedSpecies, POLAR_SPECIES)
                && containsAny(normalizedHabitat, HOT_OR_TROPICAL_HABITATS)) {
            addViolation(context, "habitat", String.format(
                    "Вид '%s' не може проживати в середовищі '%s': полярним видам не підходять спекотні або тропічні середовища",
                    dto.species(), dto.habitat()));
            return false;
        }

        return true;
    }

    private int resolveMaxLifespan(String normalizedSpecies) {
        for (SpeciesLifespanRule rule : LIFESPAN_RULES) {
            if (containsAny(normalizedSpecies, rule.keywords())) {
                return rule.maxAge();
            }
        }
        return DEFAULT_MAX_AGE;
    }

    private boolean containsAny(String value, List<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private void addViolation(ConstraintValidatorContext context, String propertyNode, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(propertyNode)
                .addConstraintViolation();
    }
}
