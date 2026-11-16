package com.bogeum.domain.recommend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.recommend.model.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

	// 이어서 고친 검색인지 보려고 바로 앞 검색 한 건을 찾는다 (RECOMMEND_001)
	Optional<SearchLog> findFirstByUser_UserIdOrderBySearchedAtDescSearchLogIdDesc(Long userId);

	// 사용자가 최근 검색한 지역·지하철역. 관심 지역을 정한다
	List<SearchLog> findAllByUser_UserIdAndSearchedAtAfter(Long userId, LocalDateTime since);
}
