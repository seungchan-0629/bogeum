package com.bogeum.domain.recommend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

import com.bogeum.domain.property.model.Property;

/**
 * 두 매물이 얼마나 비슷한지 점수로 매긴다
 * 임대 형식은 후보를 찾을 때 같은 것만 골라서 여기서는 비교하지 않는다.
 *
 * <p>점수표. 숫자를 바꾸면 추천 순서가 바로 바뀐다.
 * <pre>
 * 항목             | 아주 비슷함 | 비슷함
 * 매물 종류 같음    | 20         |
 * 보증금           | 15 (10%)   | 8 (30%)
 * 월세 (월세끼리)   | 10 (10%)   | 5 (30%)
 * 면적             | 10 (10%)   | 5 (30%)
 * 방 개수          | 10 (같음)   | 5 (1개 차이)
 * 같은 시·구        | 10         |
 * 같은 지하철역     | 10         |
 * </pre>
 */
final class PropertySimilarity {

	// 값 차이가 기준 매물 값의 이 비율 안이면 "아주 비슷함", 그다음 비율 안이면 "비슷함"이다
	private static final BigDecimal CLOSE_RATIO = new BigDecimal("0.10");
	private static final BigDecimal NEAR_RATIO = new BigDecimal("0.30");

	private static final int SAME_TYPE = 20;
	private static final int DEPOSIT_CLOSE = 15;
	private static final int DEPOSIT_NEAR = 8;
	private static final int RENT_CLOSE = 10;
	private static final int RENT_NEAR = 5;
	private static final int AREA_CLOSE = 10;
	private static final int AREA_NEAR = 5;
	private static final int SAME_ROOMS = 10;
	private static final int NEAR_ROOMS = 5;
	private static final int SAME_REGION = 10;
	private static final int SAME_STATION = 10;

	private PropertySimilarity() {
	}

	static int score(Property base, Property candidate) {
		int score = 0;
		if (base.getPropertyType() != null && base.getPropertyType() == candidate.getPropertyType()) {
			score += SAME_TYPE;
		}
		score += closeness(base.getDeposit(), candidate.getDeposit(), DEPOSIT_CLOSE, DEPOSIT_NEAR);
		// 전세는 월세가 0이라 비교하지 않는다
		if (base.getMonthlyRent() != null && base.getMonthlyRent().signum() > 0) {
			score += closeness(base.getMonthlyRent(), candidate.getMonthlyRent(), RENT_CLOSE, RENT_NEAR);
		}
		score += closeness(base.getAreaPyeong(), candidate.getAreaPyeong(), AREA_CLOSE, AREA_NEAR);
		if (base.getRoomCount() != null && candidate.getRoomCount() != null) {
			int roomGap = Math.abs(base.getRoomCount() - candidate.getRoomCount());
			score += roomGap == 0 ? SAME_ROOMS : roomGap == 1 ? NEAR_ROOMS : 0;
		}
		String region = regionOf(base);
		if (region != null && region.equals(regionOf(candidate))) {
			score += SAME_REGION;
		}
		if (base.getNearbySubwayStation() != null && !base.getNearbySubwayStation().isBlank()
				&& Objects.equals(base.getNearbySubwayStation().trim(), trim(candidate.getNearbySubwayStation()))) {
			score += SAME_STATION;
		}
		return score;
	}

	// 차이가 기준 값의 10% 안이면 close, 30% 안이면 near 점수. 값이 없거나 기준이 0이면 같을 때만 close 점수다
	private static int closeness(BigDecimal base, BigDecimal candidate, int close, int near) {
		if (base == null || candidate == null) {
			return 0;
		}
		if (base.signum() == 0) {
			return candidate.signum() == 0 ? close : 0;
		}
		BigDecimal ratio = base.subtract(candidate).abs().divide(base, 4, RoundingMode.HALF_UP);
		if (ratio.compareTo(CLOSE_RATIO) <= 0) {
			return close;
		}
		return ratio.compareTo(NEAR_RATIO) <= 0 ? near : 0;
	}

	// 도로명주소 앞 두 낱말 (예: "서울특별시 마포구")
	private static String regionOf(Property property) {
		if (property.getRoadAddress() == null || property.getRoadAddress().isBlank()) {
			return null;
		}
		return String.join(" ", Arrays.stream(property.getRoadAddress().trim().split("\\s+")).limit(2).toList());
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}
}
