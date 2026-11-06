package com.bogeum.domain.property;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.property.model.Property;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}
