package com.bogeum.domain.risk;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RiskController {

	private final RiskService riskService;
}
