package com.bogeum.domain.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyType;
import com.bogeum.domain.property.model.Property;

/** 비슷한 매물 점수표대로 점수가 매겨지는지 확인한다 (RECOMMEND_002) */
class PropertySimilarityTest {

	// 기준: 마포구 합정역 오피스텔 월세 1,000만/70만, 10평, 방 1개
	private final Property base = property(PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000, "10.00", 1,
		"서울특별시 마포구 독막로 332", "합정역");

	@Test
	@DisplayName("모든 항목이 같으면 만점(20 + 15 + 10 + 10 + 10 + 10 + 10 = 85점)이다")
	void scoresIdenticalProperty() {
		Property same = property(PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000, "10.00", 1,
			"서울특별시 마포구 와우산로 21", "합정역");

		assertEquals(85, PropertySimilarity.score(base, same));
	}

	@Test
	@DisplayName("가격·면적은 10% 안이면 높은 점수, 30% 안이면 낮은 점수, 그보다 크면 0점이다")
	void scoresByPriceAndAreaGap() {
		// 보증금 10%·월세 10%·면적 10% 차이 → 15 + 10 + 10
		assertEquals(35, PropertySimilarity.score(base, property(null, LeaseType.MONTHLY, 11_000_000, 770_000, "11.00",
			null, "경기도 성남시 분당구 정자일로 196", "정자역")));
		// 보증금 30%·월세 30%·면적 30% 차이 → 8 + 5 + 5
		assertEquals(18, PropertySimilarity.score(base, property(null, LeaseType.MONTHLY, 13_000_000, 910_000, "13.00",
			null, "경기도 성남시 분당구 정자일로 196", "정자역")));
		// 50% 차이 → 0
		assertEquals(0, PropertySimilarity.score(base, property(null, LeaseType.MONTHLY, 15_000_000, 1_050_000, "15.00",
			null, "경기도 성남시 분당구 정자일로 196", "정자역")));
	}

	@Test
	@DisplayName("방은 같으면 10점, 1개 차이면 5점, 시·구와 지하철역이 같으면 각각 10점이다")
	void scoresByRoomsRegionAndStation() {
		Property oneMoreRoom = property(null, LeaseType.MONTHLY, 50_000_000, 2_000_000, null, 2,
			"서울특별시 마포구 월드컵로 61", "망원역");
		Property sameStation = property(null, LeaseType.MONTHLY, 50_000_000, 2_000_000, null, null,
			"서울특별시 서대문구 연희로 129", "합정역");

		assertEquals(5 + 10, PropertySimilarity.score(base, oneMoreRoom));
		assertEquals(10, PropertySimilarity.score(base, sameStation));
	}

	@Test
	@DisplayName("전세끼리는 월세를 비교하지 않고, 비어 있는 값은 점수 없이 넘어간다")
	void skipsRentForJeonseAndEmptyValues() {
		Property jeonse = property(PropertyType.APARTMENT, LeaseType.JEONSE, 300_000_000, 0, null, null,
			"서울특별시 강남구 테헤란로 152", null);
		Property otherJeonse = property(PropertyType.APARTMENT, LeaseType.JEONSE, 300_000_000, 0, "20.00", 3,
			"서울특별시 강남구 역삼로 1", null);

		// 매물 종류 20 + 보증금 15 + 같은 시·구 10
		assertEquals(45, PropertySimilarity.score(jeonse, otherJeonse));
	}

	private Property property(PropertyType type, LeaseType leaseType, long deposit, long monthlyRent, String area,
			Integer rooms, String roadAddress, String station) {
		return Property.builder().propertyType(type).leaseType(leaseType).deposit(BigDecimal.valueOf(deposit))
			.monthlyRent(BigDecimal.valueOf(monthlyRent)).areaPyeong(area == null ? null : new BigDecimal(area))
			.roomCount(rooms).roadAddress(roadAddress).nearbySubwayStation(station).build();
	}
}
