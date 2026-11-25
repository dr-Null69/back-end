package com.example.anml.dto;

public record VeterinaryCheckDto(
        Long animalId,
        String healthStatus,
        String threadName,
        boolean fallback
) {
}