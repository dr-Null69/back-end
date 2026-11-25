package com.example.anml.mapper;

import com.example.anml.dto.AnimalDto;
import com.example.anml.entity.Animal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AnimalMapper {

    AnimalDto toDto(Animal animal);

    Animal toEntity(AnimalDto dto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(AnimalDto dto, @MappingTarget Animal animal);
}
