package com.example.anml.dto;

import com.example.anml.validation.ValidAnimalProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ValidAnimalProfile
public record AnimalDto(
        Long id,
        @NotBlank(message = "Кличка тварини не може бути порожньою")
        String name,
        @NotBlank(message = "Вид тварини не може бути порожнім")
        String species,
        @NotNull(message = "Вік тварини є обов'язковим")
        Integer age,
        @NotBlank(message = "Середовище проживання не може бути порожнім")
        String habitat
) {
}
