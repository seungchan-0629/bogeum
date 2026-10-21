package com.bogeum.domain.aftersales;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AfterSalesController {

	private final AfterSalesService afterSalesService;
}
