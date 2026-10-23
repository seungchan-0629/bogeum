package com.bogeum.domain.movein;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MoveInController {

	private final MoveInService moveInService;
}
