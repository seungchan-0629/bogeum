package com.bogeum.domain.property;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PropertyController {

	private final PropertyService propertyService;
}
