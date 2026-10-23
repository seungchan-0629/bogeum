package com.bogeum.domain.property.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물. 임대인이 중개를 신청하면 만들어지고, 상세 정보는 중개사가 수락 후 채운다 (PROPERTY_002, PROPERTY_010)
 */
@Entity
@Table(name = "property")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long propertyId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;

	// 중개를 수락한 중개사. 수락 전에는 비어 있다 (PROPERTY_008)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agent_id")
	private User agent;

	// 중개사가 상세 정보를 올릴 때 채운다 (PROPERTY_003, PROPERTY_010)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private PropertyType propertyType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private LeaseType leaseType;

	// 보증금 또는 전세금(원)
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal deposit;

	// 전세는 0
	@Builder.Default
	@Column(nullable = false, precision = 15, scale = 0)
	private BigDecimal monthlyRent = BigDecimal.ZERO;

	// 지역 검색의 기준 (PROPERTY_005)
	@Column(nullable = false)
	private String roadAddress;

	@Column(length = 100)
	private String detailAddress;

	// 검색은 평 수 기준이다 (PROPERTY_005)
	@Column(precision = 7, scale = 2)
	private BigDecimal areaPyeong;

	private Integer householdCount;

	private Integer parkingCount;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer floor;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer roomCount;

	// 매물 추천에도 쓴다 (RECOMMEND_001)
	@Column(length = 100)
	private String nearbySubwayStation;

	// 삭제돼도 행과 채팅 내역은 보존한다 (CHAT_009)
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PropertyStatus propertyStatus = PropertyStatus.REQUESTED;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
