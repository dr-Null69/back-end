package com.example.anml.client;

import com.example.anml.dto.AnimalDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnimalCatalogClient {

    private final RestClient restClient;

    public AnimalCatalogClient(@Value("${app.catalog.base-url:http://localhost:8080}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public AnimalDto findById(Long id) {
        return restClient.get()
                .uri("/api/animals/{id}", id)
                .retrieve()
                .body(AnimalDto.class);
    }
}
