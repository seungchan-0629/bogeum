package com.bogeum.domain.favorite;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.favorite.model.FavoriteDto;
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

	// 사용자가 담은 관심 매물을 최근에 담은 순서로 12개씩 본다 (FAVORITE_003)
	// 화면 주소 /favorites(관심 매물 화면)와 겹치지 않게 /favorites/me로 둔다
	@GetMapping("/favorites/me")
	public FavoriteDto.FavoriteListResponse readFavorites(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		return favoriteService.readFavorites(loginUser.getUserId(), page);
	}

	// 사용자가 담은 매물 번호 전부. 매물 찾기·상세에 들어가자마자 담은 매물의 하트를 채울 때 쓴다 (FAVORITE_003)
	@GetMapping("/favorites/me/ids")
	public FavoriteDto.FavoriteIdsResponse readFavoriteIds(@AuthenticationPrincipal LoginUser loginUser) {
		return favoriteService.readFavoriteIds(loginUser.getUserId());
	}

	// 사용자가 담아 둔 매물을 관심 매물에서 뺀다. 담지 않은 매물이어도 오류 없이 204다 (FAVORITE_002)
	@DeleteMapping("/favorites/{propertyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteFavorite(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("propertyId") Long propertyId) {
		favoriteService.deleteFavorite(loginUser.getUserId(), propertyId);
	}
}
