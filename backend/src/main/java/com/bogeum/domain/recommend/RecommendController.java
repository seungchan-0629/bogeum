package com.bogeum.domain.recommend;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RecommendController {

	private final RecommendService recommendService;
}
