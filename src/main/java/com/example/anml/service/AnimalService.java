package com.example.anml.service;

import com.example.anml.dto.AnimalDto;
import com.example.anml.entity.Animal;
import com.example.anml.mapper.AnimalMapper;
import com.example.anml.repository.AnimalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class AnimalService {

    private final AnimalRepository animalRepository;
    private final AnimalMapper animalMapper;

    public AnimalService(AnimalRepository animalRepository, AnimalMapper animalMapper) {
        this.animalRepository = animalRepository;
        this.animalMapper = animalMapper;
    }

    @Transactional(readOnly = true)
    public List<AnimalDto> findAll() {
        List<Animal> animals = animalRepository.findAll();
        if (animals == null || animals.isEmpty()) {
            return Collections.emptyList();
        }
        return animals.stream()
                .map(animalMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnimalDto findById(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID тварини має бути додатним числом");
        }
        Animal animal = animalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тварину з id=" + id + " не знайдено"));
        return animalMapper.toDto(animal);
    }

    public AnimalDto create(AnimalDto dto) {
        validateAnimalDto(dto);
        Animal animal = animalMapper.toEntity(dto);
        animal.setId(null);
        Animal saved = animalRepository.save(animal);
        return animalMapper.toDto(saved);
    }

    public AnimalDto update(Long id, AnimalDto dto) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID тварини має бути додатним числом");
        }
        validateAnimalDto(dto);
        Animal existing = animalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тварину з id=" + id + " не знайдено"));
        animalMapper.updateEntityFromDto(dto, existing);
        Animal saved = animalRepository.save(existing);
        return animalMapper.toDto(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID тварини має бути додатним числом");
        }
        if (!animalRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Тварину з id=" + id + " не знайдено");
        }
        animalRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long count() {
        long total = animalRepository.count();
        return Math.max(0L, total);
    }

    private void validateAnimalDto(AnimalDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дані тварини не можуть бути порожніми");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Кличка тварини не може бути порожньою");
        }
        if (dto.species() == null || dto.species().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вид тварини не може бути порожнім");
        }
        if (dto.age() == null || dto.age() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Вік тварини не може бути від'ємним");
        }
        if (dto.habitat() == null || dto.habitat().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Середовище проживання не може бути порожнім");
        }
    }
}