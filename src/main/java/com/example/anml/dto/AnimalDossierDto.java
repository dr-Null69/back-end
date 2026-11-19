package com.example.anml.dto;

public record AnimalDossierDto(
        AnimalDto animal,
        VeterinaryCheckDto veterinaryCheck,
        HabitatReportDto habitatReport
) {
}