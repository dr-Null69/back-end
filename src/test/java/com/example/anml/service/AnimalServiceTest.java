package com.example.anml.service;

import com.example.anml.dto.AnimalDto;
import com.example.anml.entity.Animal;
import com.example.anml.mapper.AnimalMapper;
import com.example.anml.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private AnimalMapper animalMapper;

    @InjectMocks
    private AnimalService animalService;

    @Test
    void findAll_whenAnimalsExist_returnsMappedAnimalDtoList() {
        Animal entity = new Animal(1L, "Сімба", "Лев", 5, "Савана");
        AnimalDto dto = new AnimalDto(1L, "Сімба", "Лев", 5, "Савана");
        when(animalRepository.findAll()).thenReturn(List.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(dto);

        List<AnimalDto> result = animalService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Сімба", result.get(0).name());
        verify(animalRepository, times(1)).findAll();
        verify(animalMapper, times(1)).toDto(entity);
    }

    @Test
    void findAll_whenRepositoryReturnsEmptyList_returnsEmptyListWithoutCallingMapper() {
        when(animalRepository.findAll()).thenReturn(Collections.emptyList());

        List<AnimalDto> result = animalService.findAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(animalRepository, times(1)).findAll();
        verifyNoInteractions(animalMapper);
    }

    @Test
    void findById_whenAnimalExists_returnsAnimalDto() {
        Long id = 1L;
        Animal entity = new Animal(id, "Шкіпер", "Пінгвін", 3, "Антарктида");
        AnimalDto expectedDto = new AnimalDto(id, "Шкіпер", "Пінгвін", 3, "Антарктида");
        when(animalRepository.findById(id)).thenReturn(Optional.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(expectedDto);

        AnimalDto actualDto = animalService.findById(id);

        assertNotNull(actualDto);
        assertEquals(expectedDto.id(), actualDto.id());
        assertEquals("Шкіпер", actualDto.name());
        verify(animalRepository).findById(id);
    }

    @Test
    void findById_whenAnimalDoesNotExist_throwsNotFoundException() {
        Long id = 999L;
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.findById(id)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason().contains("999"));
        verify(animalRepository).findById(id);
        verifyNoInteractions(animalMapper);
    }

    @Test
    void findById_whenIdIsZeroOrNegative_throwsBadRequestException() {
        Long invalidId = 0L;

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.findById(invalidId)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(animalRepository);
    }

    @Test
    void create_whenValidDto_resetsIdSavesEntityAndReturnsDto() {
        AnimalDto inputDto = new AnimalDto(55L, "Багіра", "Пантера", 4, "Джунглі");
        Animal mappedEntity = new Animal(55L, "Багіра", "Пантера", 4, "Джунглі");
        Animal savedEntity = new Animal(10L, "Багіра", "Пантера", 4, "Джунглі");
        AnimalDto expectedDto = new AnimalDto(10L, "Багіра", "Пантера", 4, "Джунглі");

        when(animalMapper.toEntity(inputDto)).thenReturn(mappedEntity);
        when(animalRepository.save(any(Animal.class))).thenReturn(savedEntity);
        when(animalMapper.toDto(savedEntity)).thenReturn(expectedDto);

        AnimalDto result = animalService.create(inputDto);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("Багіра", result.name());

        ArgumentCaptor<Animal> animalCaptor = ArgumentCaptor.forClass(Animal.class);
        verify(animalRepository, times(1)).save(animalCaptor.capture());
        Animal capturedAnimal = animalCaptor.getValue();
        assertNull(capturedAnimal.getId(), "Перед збереженням ID нової тварини має скидатися в null");
        assertEquals("Багіра", capturedAnimal.getName());
    }

    @Test
    void create_whenAgeIsNegative_throwsBadRequestAndNeverCallsRepository() {
        AnimalDto invalidDto = new AnimalDto(null, "Багіра", "Пантера", -2, "Джунглі");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.create(invalidDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(animalRepository, never()).save(any());
    }

    @Test
    void create_whenNameIsBlank_throwsBadRequestException() {
        AnimalDto invalidDto = new AnimalDto(null, "   ", "Пантера", 3, "Джунглі");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.create(invalidDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(animalRepository);
    }

    @Test
    void update_whenAnimalExistsAndValidDto_updatesAndSavesAnimal() {
        Long id = 1L;
        AnimalDto updateDto = new AnimalDto(id, "Сімба Король", "Лев", 6, "Савана Прайду");
        Animal existingEntity = new Animal(id, "Сімба", "Лев", 5, "Савана");
        Animal savedEntity = new Animal(id, "Сімба Король", "Лев", 6, "Савана Прайду");

        when(animalRepository.findById(id)).thenReturn(Optional.of(existingEntity));
        doAnswer(invocation -> {
            AnimalDto source = invocation.getArgument(0);
            Animal target = invocation.getArgument(1);
            target.setName(source.name());
            target.setAge(source.age());
            target.setHabitat(source.habitat());
            return null;
        }).when(animalMapper).updateEntityFromDto(updateDto, existingEntity);
        when(animalRepository.save(existingEntity)).thenReturn(savedEntity);
        when(animalMapper.toDto(savedEntity)).thenReturn(updateDto);

        AnimalDto result = animalService.update(id, updateDto);

        assertNotNull(result);
        assertEquals("Сімба Король", result.name());
        assertEquals(6, result.age());

        ArgumentCaptor<Animal> captor = ArgumentCaptor.forClass(Animal.class);
        verify(animalMapper).updateEntityFromDto(eq(updateDto), eq(existingEntity));
        verify(animalRepository).save(captor.capture());
        assertEquals("Сімба Король", captor.getValue().getName());
    }

    @Test
    void update_whenAnimalDoesNotExist_throwsNotFoundAndDoesNotSave() {
        Long id = 404L;
        AnimalDto updateDto = new AnimalDto(id, "Сімба", "Лев", 6, "Савана");
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.update(id, updateDto)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(animalRepository, never()).save(any());
    }

    @Test
    void delete_whenAnimalExists_callsRepositoryDeleteById() {
        Long id = 2L;
        when(animalRepository.existsById(id)).thenReturn(true);

        animalService.delete(id);

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(animalRepository, times(1)).deleteById(idCaptor.capture());
        assertEquals(2L, idCaptor.getValue());
    }

    @Test
    void delete_whenAnimalDoesNotExist_throwsNotFoundAndNeverDeletes() {
        Long id = 77L;
        when(animalRepository.existsById(id)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.delete(id)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(animalRepository, never()).deleteById(any());
    }

    @Test
    void count_whenAnimalsPresent_returnsTotalCount() {
        when(animalRepository.count()).thenReturn(5L);

        long result = animalService.count();

        assertEquals(5L, result);
        verify(animalRepository, times(1)).count();
    }

    @Test
    void count_whenRepositoryReturnsNegativeValue_returnsZeroAsFallback() {
        when(animalRepository.count()).thenReturn(-1L);

        long result = animalService.count();

        assertEquals(0L, result);
        verify(animalRepository, times(1)).count();
    }
}