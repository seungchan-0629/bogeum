package com.bogeum.domain.property;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminPropertyController {

	private final PropertyService propertyService;
}
