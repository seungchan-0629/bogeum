package com.bogeum.domain.favorite;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.user.model.UserDto.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FavoriteController {

	private final FavoriteService favoriteService;

	// 사용자가 공개 중인 매물을 관심 매물로 담는다. 여러 번 보내도 한 번만 담긴다 (FAVORITE_001)
	// 결과로 돌려줄 내용이 없어 본문 없이 204로 답한다
	@PutMapping("/favorites/{propertyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void createFavorite(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("propertyId") Long propertyId) {
		favoriteService.createFavorite(loginUser.getUserId(), propertyId);
	}
}
