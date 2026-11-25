package com.example.anml.controller;

import com.example.anml.dto.AnimalDossierDto;
import com.example.anml.dto.AnimalDto;
import com.example.anml.service.AnimalDossierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/animals")
public class AnimalAsyncController {

    private final AnimalDossierService animalDossierService;

    public AnimalAsyncController(AnimalDossierService animalDossierService) {
        this.animalDossierService = animalDossierService;
    }

    @PostMapping("/{id}/notify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> notifyKeepers(@PathVariable Long id) {
        AnimalDto animal = animalDossierService.notifyKeepersAboutAnimal(id);
        return Map.of(
                "status", "accepted",
                "animalId", animal.id(),
                "message", "Сповіщення доглядачам поставлено в асинхронну чергу"
        );
    }

    @GetMapping("/{id}/dossier")
    public AnimalDossierDto getAnimalDossier(@PathVariable Long id) {
        return animalDossierService.buildAnimalDossier(id);
    }
}