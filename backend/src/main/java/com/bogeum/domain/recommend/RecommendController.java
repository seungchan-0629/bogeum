package com.bogeum.domain.recommend;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.recommend.model.SimilarBasis;
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

	// 매물 상세에서 보고 있는 매물과 비슷한 매물을 본다 (RECOMMEND_002)
	// 상세처럼 로그인한 사용자·중개사가 보고, 공개 중이 아닌 매물이면 404다
	@GetMapping("/properties/{propertyId}/similar")
	public RecommendDto.SimilarListResponse readSimilarProperties(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId,
			@RequestParam(value = "size", defaultValue = "3") int size) {
		return recommendService.readSimilarProperties(loginUser.getUserId(), propertyId, size);
	}

	// 사용자가 최근 본 매물(VIEW)이나 담은 관심 매물(FAVORITE)과 비슷한 매물을 본다. 메인·관심 매물 화면에서 쓴다 (RECOMMEND_002)
	@GetMapping("/similar")
	public RecommendDto.SimilarListResponse readSimilarByBasis(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("basis") SimilarBasis basis,
			@RequestParam(value = "size", defaultValue = "3") int size) {
		return recommendService.readSimilarByBasis(loginUser.getUserId(), basis, size);
	}
}
