package com.example.anml.dto;

public record AnimalDto(
        Long id,
        String name,
        String species,
        Integer age,
        String habitat
) {
}
