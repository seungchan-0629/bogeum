package com.bogeum.domain.recommend;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.PropertyViewLog;
import com.bogeum.domain.recommend.model.PropertyCount;

// 매물 조회 이력은 추천에만 쓰여 추천 패키지에 둔다 (RECOMMEND_001)
public interface PropertyViewLogRepository extends JpaRepository<PropertyViewLog, Long> {

	// 같은 사람이 같은 매물을 24시간 안에 다시 보면 조회 수를 늘리지 않는다
	boolean existsByUser_UserIdAndProperty_PropertyIdAndViewedAtAfter(Long userId, Long propertyId, LocalDateTime since);

	// 사용자가 최근 본 매물. 매물의 주소·지하철역으로 관심 지역을 정해서 매물을 함께 가져온다
	@EntityGraph(attributePaths = "property")
	List<PropertyViewLog> findAllByUser_UserIdAndViewedAtAfter(Long userId, LocalDateTime since);

	// 공개 중인 매물별 최근 조회 수. 인기 점수에 쓴다
	@Query("""
		SELECT v.property.propertyId AS propertyId, COUNT(v) AS count FROM PropertyViewLog v
		WHERE v.viewedAt > :since AND v.property.propertyStatus = :status
		GROUP BY v.property.propertyId
		""")
	List<PropertyCount> countViewsByProperty(@Param("since") LocalDateTime since, @Param("status") PropertyStatus status);
}
