package com.novastream.util;

import org.modelmapper.Conditions;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class GenericMapper {

  private final ModelMapper modelMapper;
  private final ModelMapper nonNullMapper;

  public GenericMapper() {
    this.modelMapper = new ModelMapper();

    this.nonNullMapper = new ModelMapper();
    this.nonNullMapper.getConfiguration()
      .setPropertyCondition(Conditions.isNotNull());
  }

  public <D, E> E toEntity(D dto, Class<E> entityClass) {
    return modelMapper.map(dto, entityClass);
  }

  public <E, D> D toDto(E entity, Class<D> dtoClass) {
    return modelMapper.map(entity, dtoClass);
  }

  public <D, E> E mapTo(D sourceDto, E targetEntity) {
    nonNullMapper.map(sourceDto, targetEntity);
    return targetEntity;
  }
}
