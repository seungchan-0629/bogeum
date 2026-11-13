package com.bogeum.domain.favorite.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관심 매물. 같은 매물은 중복 등록되지 않는다 (FAVORITE_001)
 * 계약 전에 집을 찾는 사용자(USER)가 담는다. 임대인·임차인은 계약에서 정해져서 tenant가 아니라 user로 둔다
 */
@Entity
@Table(name = "favorite_property", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"user_id", "property_id"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long favoritePropertyId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	// 인기 매물 집계에도 쓴다 (RECOMMEND_001)
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
