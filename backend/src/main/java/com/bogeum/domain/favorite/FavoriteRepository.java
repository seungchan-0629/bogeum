package com.bogeum.domain.favorite;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.recommend.model.PropertyCount;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	// 이미 담은 매물인지 본다. 같은 매물은 다시 저장하지 않는다 (FAVORITE_001)
	boolean existsByUser_UserIdAndProperty_PropertyId(Long userId, Long propertyId);

	// 내 관심 매물 목록. 카드에 매물 정보를 보여 줘서 매물을 함께 가져온다 (FAVORITE_003)
	@EntityGraph(attributePaths = "property")
	Page<Favorite> findAllByUser_UserId(Long userId, Pageable pageable);

	// 내가 담은 매물 번호 전부. 화면이 처음부터 하트를 채워 보여 줄 때 쓴다 (FAVORITE_003)
	@Query("SELECT f.property.propertyId FROM Favorite f WHERE f.user.userId = :userId")
	List<Long> findPropertyIdsByUserId(@Param("userId") Long userId);

	// 사용자가 최근 담은 매물. 매물의 주소·지하철역으로 추천할 관심 지역을 정한다 (RECOMMEND_001)
	@EntityGraph(attributePaths = "property")
	List<Favorite> findAllByUser_UserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

	// 공개 중인 매물별 관심 수. 추천의 인기 점수에 쓴다 (RECOMMEND_001)
	@Query("""
		SELECT f.property.propertyId AS propertyId, COUNT(f) AS count FROM Favorite f
		WHERE f.property.propertyStatus = :status
		GROUP BY f.property.propertyId
		""")
	List<PropertyCount> countFavoritesByProperty(@Param("status") PropertyStatus status);

	// 사용자 본인의 관심 매물만 한 번에 지운다. 담지 않았으면 0줄이 지워지고 그대로 끝난다 (FAVORITE_002)
	// 메서드 이름 방식(deleteBy…)은 먼저 조회한 뒤 하나씩 지워 쿼리가 두 번 나가서 직접 쓴다
	@Modifying
	@Query("DELETE FROM Favorite f WHERE f.user.userId = :userId AND f.property.propertyId = :propertyId")
	int deleteByUserAndProperty(@Param("userId") Long userId, @Param("propertyId") Long propertyId);
}
