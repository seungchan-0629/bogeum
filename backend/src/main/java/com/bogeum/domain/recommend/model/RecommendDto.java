package com.bogeum.domain.recommend.model;

import java.util.List;

import com.bogeum.domain.property.model.PropertyDto;

import lombok.Builder;
import lombok.Getter;

/**
 * 매물 추천의 요청·응답 DTO.
 */
public class RecommendDto {

	/**
	 * 나를 위한 추천 매물 (RECOMMEND_001). 카드는 매물 목록과 같은 모양이다.
	 * regions·stations는 추천에 쓴 관심 지역·지하철역이라 화면이 "마포구·합정역 근처 인기 매물"처럼 제목을 붙일 수 있다.
	 * 검색·조회·관심 기록이 없으면 비어 있고 isPersonalized가 false다(전체 인기 매물).
	 */
	@Getter
	@Builder
	public static class RecommendListResponse {

		private List<PropertyDto.PropertyItemResponse> properties;
		private List<String> regions;
		private List<String> stations;
		private Boolean isPersonalized;
	}

	/**
	 * 비슷한 매물 (RECOMMEND_002). 카드는 매물 목록과 같은 모양이고, 비슷한 순서대로 온다.
	 * 기준 매물(최근 본 매물·관심 매물)이 없으면 비어 있어 화면이 추천 기준을 안내한다.
	 */
	@Getter
	@Builder
	public static class SimilarListResponse {

		private List<PropertyDto.PropertyItemResponse> properties;
	}
}
