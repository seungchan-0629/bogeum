package com.bogeum.domain.recommend.model;

/**
 * 매물별 집계 수. 인기 점수(조회 수, 관심 수)를 매물마다 한 번에 센다 (RECOMMEND_001)
 */
public interface PropertyCount {

	Long getPropertyId();

	Long getCount();
}
