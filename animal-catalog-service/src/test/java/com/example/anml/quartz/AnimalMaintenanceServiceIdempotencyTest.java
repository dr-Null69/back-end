package com.example.anml.quartz;

import com.example.anml.entity.Animal;
import com.example.anml.repository.AnimalRepository;
import com.example.anml.service.AnimalMaintenanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalMaintenanceServiceIdempotencyTest {

    @Mock
    private AnimalRepository animalRepository;

    @InjectMocks
    private AnimalMaintenanceService maintenanceService;

    @Test
    void cleanupAnimalRecords_whenExecutedTwiceConsecutively_secondRunChangesNothing() {
        Animal messyAnimal = new Animal(1L, "  Сімба   Король ", "  Лев   африканський ", 5, "  Савана  ");
        Animal cleanAnimal = new Animal(2L, "Балу", "Ведмідь бурий", 8, "Карпатський ліс");
        List<Animal> animals = List.of(messyAnimal, cleanAnimal);
        when(animalRepository.findAll()).thenReturn(animals);

        int firstRunUpdated = maintenanceService.cleanupAnimalRecords("", 100);
        assertEquals(1, firstRunUpdated);
        assertEquals("Сімба Король", messyAnimal.getName());
        assertEquals("Лев африканський", messyAnimal.getSpecies());
        assertEquals("Савана", messyAnimal.getHabitat());

        int secondRunUpdated = maintenanceService.cleanupAnimalRecords("", 100);
        assertEquals(0, secondRunUpdated);
        verify(animalRepository, times(1)).save(any(Animal.class));
    }

    @Test
    void generateInventoryReport_whenExecutedTwiceConsecutively_secondRunChangesNothing() {
        List<Animal> animals = List.of(
                new Animal(1L, "Сімба", "Лев", 5, "Савана"),
                new Animal(2L, "Шкіпер", "Пінгвін", 3, "Антарктида")
        );
        when(animalRepository.findAll()).thenReturn(animals);

        var firstResult = maintenanceService.generateInventoryReport("DAILY", 0, true);
        assertTrue(firstResult.changed());
        assertEquals(2L, firstResult.report().matchingAnimals());
        assertEquals(1, maintenanceService.getGeneratedReports().size());

        var secondResult = maintenanceService.generateInventoryReport("DAILY", 0, true);
        assertFalse(secondResult.changed());
        assertEquals(firstResult.report(), secondResult.report());
        assertEquals(1, maintenanceService.getGeneratedReports().size());
    }
}
