package com.bogeum.domain.claim;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ClaimController {

	private final ClaimService claimService;
}
