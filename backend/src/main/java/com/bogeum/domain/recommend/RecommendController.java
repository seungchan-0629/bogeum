package com.bogeum.domain.recommend;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;

import lombok.RequiredArgsConstructor;

@RequestMapping("/recommend")
@RestController
@RequiredArgsConstructor
public class RecommendController {

	private final RecommendService recommendService;

	// 사용자가 최근 검색·조회·관심 매물로 정해진 관심 지역 근처의 인기 매물을 추천받는다 (RECOMMEND_001)
	// 메인 화면은 3개를 본다. 기록이 없으면 전체 인기 매물을 받는다
	@GetMapping("/properties")
	public RecommendDto.RecommendListResponse readRecommendations(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(value = "size", defaultValue = "3") int size) {
		return recommendService.readRecommendations(loginUser.getUserId(), size);
	}
}
