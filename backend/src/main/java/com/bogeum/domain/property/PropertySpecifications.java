package com.bogeum.domain.property;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * 매물 목록 검색 조건
 * 받은 조건만 붙이고, 여러 조건은 모두 맞아야 한다(AND). 값이 비어 있는 매물은 그 조건을 고르면 빠진다.
 */
final class PropertySpecifications {

	// LIKE의 %, _를 일반 글자로 찾기 위한 탈출 문자
	private static final char ESCAPE = '\\';

	private PropertySpecifications() {
	}

	/**
	 * 공개 중인 매물 중 검색 조건에 맞고, 커서가 있으면 그 매물 다음 순서인 매물.
	 * 순서는 공개 시각 최신순 → 같으면 매물 번호 큰 순서라, 커서도 같은 두 값으로 비교한다.
	 */
	static Specification<Property> searchPublished(PropertyDto.SearchRequest search, LocalDateTime cursorPublishedAt,
			Long cursorPropertyId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("propertyStatus"), PropertyStatus.ACTIVE));
			if (cursorPropertyId != null) {
				Expression<LocalDateTime> publishedAt = root.get("publishedAt");
				predicates.add(cb.or(cb.lessThan(publishedAt, cursorPublishedAt),
					cb.and(cb.equal(publishedAt, cursorPublishedAt), cb.lessThan(root.get("propertyId"), cursorPropertyId))));
			}
			if (search.getPropertyTypes() != null && !search.getPropertyTypes().isEmpty()) {
				predicates.add(root.get("propertyType").in(search.getPropertyTypes()));
			}
			if (search.getLeaseTypes() != null && !search.getLeaseTypes().isEmpty()) {
				predicates.add(root.get("leaseType").in(search.getLeaseTypes()));
			}
			addRange(predicates, cb, root.get("deposit"), search.getMinDeposit(), search.getMaxDeposit());
			addRange(predicates, cb, root.get("monthlyRent"), search.getMinMonthlyRent(), search.getMaxMonthlyRent());
			addRange(predicates, cb, root.get("areaPyeong"), search.getMinArea(), null);
			addRange(predicates, cb, root.get("householdCount"), search.getMinHouseholdCount(), null);
			addRange(predicates, cb, root.get("parkingCount"), search.getMinParkingCount(), null);
			addRange(predicates, cb, root.get("floor"), search.getMinFloor(), null);
			addRange(predicates, cb, root.get("roomCount"), search.getMinRoomCount(), null);
			// 사람은 "서울 강남구", 주소는 "서울특별시 강남구 …"라서 낱말마다 들어 있는지 본다
			for (String word : words(search.getRegion())) {
				predicates.add(contains(cb, root.get("roadAddress"), word));
			}
			if (!words(search.getSubway()).isEmpty()) {
				predicates.add(contains(cb, root.get("nearbySubwayStation"), search.getSubway().trim()));
			}
			// 메인 검색창: 낱말마다 주소나 지하철역 중 한 곳에 들어 있으면 된다
			for (String word : words(search.getKeyword())) {
				predicates.add(cb.or(contains(cb, root.get("roadAddress"), word),
					contains(cb, root.get("nearbySubwayStation"), word)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	// 최소 이상, 최대 이하. 비교 연산은 NULL이면 거짓이라 값이 비어 있는 매물은 자연히 빠진다
	private static <T extends Comparable<? super T>> void addRange(List<Predicate> predicates, CriteriaBuilder cb,
			Expression<T> path, T min, T max) {
		if (min != null) {
			predicates.add(cb.greaterThanOrEqualTo(path, min));
		}
		if (max != null) {
			predicates.add(cb.lessThanOrEqualTo(path, max));
		}
	}

	private static Predicate contains(CriteriaBuilder cb, Expression<String> path, String word) {
		String escaped = word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return cb.like(path, "%" + escaped + "%", ESCAPE);
	}

	// 앞뒤·사이 공백으로 나눈 낱말. 비어 있으면 조건이 없는 것으로 본다
	private static List<String> words(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.trim().split("\\s+")).distinct().toList();
	}
}
