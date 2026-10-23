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

    // =========================================================================
    // 1. Тести для findAll()
    // =========================================================================

    @Test
    void findAll_whenAnimalsExist_returnsMappedAnimalDtoList() {
        // Arrange
        Animal entity = new Animal(1L, "Сімба", "Лев", 5, "Савана");
        AnimalDto dto = new AnimalDto(1L, "Сімба", "Лев", 5, "Савана");
        when(animalRepository.findAll()).thenReturn(List.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(dto);

        // Act
        List<AnimalDto> result = animalService.findAll();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Сімба", result.get(0).name());
        verify(animalRepository, times(1)).findAll();
        verify(animalMapper, times(1)).toDto(entity);
    }

    @Test
    void findAll_whenRepositoryReturnsEmptyList_returnsEmptyListWithoutCallingMapper() {
        // Arrange
        when(animalRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<AnimalDto> result = animalService.findAll();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(animalRepository, times(1)).findAll();
        verifyNoInteractions(animalMapper);
    }

    // =========================================================================
    // 2. Тести для findById(Long id)
    // =========================================================================

    @Test
    void findById_whenAnimalExists_returnsAnimalDto() {
        // Arrange
        Long id = 1L;
        Animal entity = new Animal(id, "Шкіпер", "Пінгвін", 3, "Антарктида");
        AnimalDto expectedDto = new AnimalDto(id, "Шкіпер", "Пінгвін", 3, "Антарктида");
        when(animalRepository.findById(id)).thenReturn(Optional.of(entity));
        when(animalMapper.toDto(entity)).thenReturn(expectedDto);

        // Act
        AnimalDto actualDto = animalService.findById(id);

        // Assert
        assertNotNull(actualDto);
        assertEquals(expectedDto.id(), actualDto.id());
        assertEquals("Шкіпер", actualDto.name());
        verify(animalRepository).findById(id);
    }

    @Test
    void findById_whenAnimalDoesNotExist_throwsNotFoundException() {
        // Arrange
        Long id = 999L;
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
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
        // Arrange
        Long invalidId = 0L;

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.findById(invalidId)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(animalRepository);
    }

    // =========================================================================
    // 3. Тести для create(AnimalDto dto)
    // =========================================================================

    @Test
    void create_whenValidDto_resetsIdSavesEntityAndReturnsDto() {
        // Arrange
        AnimalDto inputDto = new AnimalDto(55L, "Багіра", "Пантера", 4, "Джунглі");
        Animal mappedEntity = new Animal(55L, "Багіра", "Пантера", 4, "Джунглі");
        Animal savedEntity = new Animal(10L, "Багіра", "Пантера", 4, "Джунглі");
        AnimalDto expectedDto = new AnimalDto(10L, "Багіра", "Пантера", 4, "Джунглі");

        when(animalMapper.toEntity(inputDto)).thenReturn(mappedEntity);
        when(animalRepository.save(any(Animal.class))).thenReturn(savedEntity);
        when(animalMapper.toDto(savedEntity)).thenReturn(expectedDto);

        // Act
        AnimalDto result = animalService.create(inputDto);

        // Assert
        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("Багіра", result.name());

        // AC 7: Перевірка побічного ефекту та переданих параметрів через ArgumentCaptor
        ArgumentCaptor<Animal> animalCaptor = ArgumentCaptor.forClass(Animal.class);
        verify(animalRepository, times(1)).save(animalCaptor.capture());
        Animal capturedAnimal = animalCaptor.getValue();
        assertNull(capturedAnimal.getId(), "Перед збереженням ID нової тварини має скидатися в null");
        assertEquals("Багіра", capturedAnimal.getName());
    }

    @Test
    void create_whenAgeIsNegative_throwsBadRequestAndNeverCallsRepository() {
        // Arrange
        AnimalDto invalidDto = new AnimalDto(null, "Багіра", "Пантера", -2, "Джунглі");

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.create(invalidDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(animalRepository, never()).save(any());
    }

    @Test
    void create_whenNameIsBlank_throwsBadRequestException() {
        // Arrange
        AnimalDto invalidDto = new AnimalDto(null, "   ", "Пантера", 3, "Джунглі");

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.create(invalidDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(animalRepository);
    }

    // =========================================================================
    // 4. Тести для update(Long id, AnimalDto dto)
    // =========================================================================

    @Test
    void update_whenAnimalExistsAndValidDto_updatesAndSavesAnimal() {
        // Arrange
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

        // Act
        AnimalDto result = animalService.update(id, updateDto);

        // Assert
        assertNotNull(result);
        assertEquals("Сімба Король", result.name());
        assertEquals(6, result.age());

        // AC 7: Перевірка виклику оновлення та збереження через ArgumentCaptor
        ArgumentCaptor<Animal> captor = ArgumentCaptor.forClass(Animal.class);
        verify(animalMapper).updateEntityFromDto(eq(updateDto), eq(existingEntity));
        verify(animalRepository).save(captor.capture());
        assertEquals("Сімба Король", captor.getValue().getName());
    }

    @Test
    void update_whenAnimalDoesNotExist_throwsNotFoundAndDoesNotSave() {
        // Arrange
        Long id = 404L;
        AnimalDto updateDto = new AnimalDto(id, "Сімба", "Лев", 6, "Савана");
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.update(id, updateDto)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(animalRepository, never()).save(any());
    }

    // =========================================================================
    // 5. Тести для delete(Long id)
    // =========================================================================

    @Test
    void delete_whenAnimalExists_callsRepositoryDeleteById() {
        // Arrange
        Long id = 2L;
        when(animalRepository.existsById(id)).thenReturn(true);

        // Act
        animalService.delete(id);

        // Assert (AC 7: перевірка побічного ефекту через ArgumentCaptor)
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(animalRepository, times(1)).deleteById(idCaptor.capture());
        assertEquals(2L, idCaptor.getValue());
    }

    @Test
    void delete_whenAnimalDoesNotExist_throwsNotFoundAndNeverDeletes() {
        // Arrange
        Long id = 77L;
        when(animalRepository.existsById(id)).thenReturn(false);

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> animalService.delete(id)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(animalRepository, never()).deleteById(any());
    }

    // =========================================================================
    // 6. Тести для count()
    // =========================================================================

    @Test
    void count_whenAnimalsPresent_returnsTotalCount() {
        // Arrange
        when(animalRepository.count()).thenReturn(5L);

        // Act
        long result = animalService.count();

        // Assert
        assertEquals(5L, result);
        verify(animalRepository, times(1)).count();
    }

    @Test
    void count_whenRepositoryReturnsNegativeValue_returnsZeroAsFallback() {
        // Arrange
        when(animalRepository.count()).thenReturn(-1L);

        // Act
        long result = animalService.count();

        // Assert
        assertEquals(0L, result);
        verify(animalRepository, times(1)).count();
    }
}