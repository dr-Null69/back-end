package com.example.anml.controller;

import com.example.anml.dto.AnimalDto;
import com.example.anml.service.AnimalService;
import com.example.anml.web.CurrentUser;
import com.example.anml.web.CurrentUserInfo;
import com.example.anml.web.PostCreatedMapping;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnimalController {

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    @GetMapping("/animals")
    public List<AnimalDto> getAll() {
        return animalService.findAll();
    }

    @GetMapping("/animals/{id}")
    public AnimalDto getById(@PathVariable Long id) {
        return animalService.findById(id);
    }

    @PostCreatedMapping("/animals")
    public AnimalDto create(@Valid @RequestBody AnimalDto dto) {
        return animalService.create(dto);
    }

    @PutMapping("/animals/{id}")
    public AnimalDto update(@PathVariable Long id, @Valid @RequestBody AnimalDto dto) {
        return animalService.update(id, dto);
    }

    @DeleteMapping("/animals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        animalService.delete(id);
    }

    // Ендпоінт, доступ до якого обмежено на рівні SecurityFilterChain (Acceptance Criteria 6)
    @GetMapping("/admin/stats")
    public Map<String, Object> getAdminStats(@CurrentUser CurrentUserInfo currentUser) {
        return Map.of(
                "status", "Доступ дозволено (рівень SecurityFilterChain: тільки ADMIN)",
                "totalAnimals", animalService.count(),
                "requestedBy", currentUser.username(),
                "roles", currentUser.roles()
        );
    }

    // Допоміжний ендпоінт для UI, щоб показати ім'я користувача та його ролі
    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(@CurrentUser CurrentUserInfo currentUser) {
        return Map.of(
                "authenticated", currentUser.authenticated(),
                "username", currentUser.username(),
                "roles", currentUser.roles()
        );
    }
}
