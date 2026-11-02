package com.bogeum.domain.settlement;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementService settlementService;
}
