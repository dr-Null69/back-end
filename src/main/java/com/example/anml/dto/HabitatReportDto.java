package com.example.anml.dto;

public record HabitatReportDto(
        String habitat,
        String conditionSummary,
        String threadName,
        boolean fallback
) {
}