package com.bogeum.domain.property.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
 * 매물 조회 이력. 조회 수 집계와 매물 추천에 쓴다 (RECOMMEND_001, RECOMMEND_002)
 */
@Entity
// 24시간 중복 확인은 사람·매물·시각으로, 인기 집계는 매물·시각으로 찾는다
@Table(name = "property_view_log", indexes = {
	@Index(name = "idx_view_log_user_property_viewed", columnList = "user_id, property_id, viewed_at"),
	@Index(name = "idx_view_log_property_viewed", columnList = "property_id, viewed_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyViewLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long propertyViewLogId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime viewedAt;
}
