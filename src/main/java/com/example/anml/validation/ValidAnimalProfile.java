package com.example.anml.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Перевіряє біологічну узгодженість профілю тварини:
 * відповідність віку реалістичній тривалості життя виду
 * та сумісність виду з обраним середовищем проживання.
 */
@Documented
@Constraint(validatedBy = AnimalProfileValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAnimalProfile {

    String message() default "Профіль тварини порушує біологічні правила узгодженості виду, віку та середовища проживання";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
