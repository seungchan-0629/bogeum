package com.bogeum.domain.favorite;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FavoriteController {

	private final FavoriteService favoriteService;
}
