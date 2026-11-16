package com.bogeum.domain.recommend.model;

import java.time.LocalDateTime;

import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 찾기에서 검색한 지역·지하철역. 사용자의 관심 지역을 정해 매물을 추천한다
 * 검색 조건 중 지역·역만 남긴다. 가격·면적은 지역을 정하는 데 쓰지 않는다
 */
@Entity
@Table(name = "search_log", indexes = @Index(name = "idx_search_log_user_searched", columnList = "user_id, searched_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long searchLogId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 검색 조건과 같은 길이다 (PROPERTY_005)
	@Column(length = 50)
	private String region;

	@Column(length = 50)
	private String subwayStation;

	// 입력 중에 이어서 고친 검색은 마지막 값으로 덮어써서 시각도 함께 바뀐다
	@Column(nullable = false)
	private LocalDateTime searchedAt;

	/**
	 * 바로 앞 검색을 이어서 고친 것으로 보고 마지막 검색 값으로 바꾼다.
	 * 매물 찾기는 입력하는 동안에도 검색해서 "서울 마" → "서울 마포구"가 따로 쌓이지 않게 한다.
	 */
	public void overwrite(String region, String subwayStation, LocalDateTime searchedAt) {
		this.region = region;
		this.subwayStation = subwayStation;
		this.searchedAt = searchedAt;
	}
}
