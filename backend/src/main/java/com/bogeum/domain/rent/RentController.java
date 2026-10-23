package com.bogeum.domain.rent;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RentController {

	private final RentService rentService;
}
