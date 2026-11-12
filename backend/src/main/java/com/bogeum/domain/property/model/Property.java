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
import com.bogeum.domain.property.PropertyException;
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

	// 건물 전체 층 수. 해당 층과 함께 보여 준다 (PROPERTY_010)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer totalFloor;

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

	// 중개사가 매물 정보와 사진까지 모두 업로드 해서 공개(ACTIVE)한 시각. 매물 목록은 이 순서로 보여 준다. 공개 전에는 비어 있다
	// updated_at은 숨김 해제·수정 등으로도 바뀌어 공개 순서로 쓰지 않는다
	private LocalDateTime publishedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	// 중개를 수락한 중개사가 이 매물을 맡는다. 공개(ACTIVE)는 상세 정보를 올린 뒤다 (PROPERTY_008, PROPERTY_010)
	public void assignAgent(User agent) {
		this.agent = agent;
	}

	/**
	 * 중개사가 상세 정보를 저장한다. 임시 저장이라 빈 값도 그대로 저장하고, 공개 상태는 바꾸지 않는다 (PROPERTY_010).
	 * 거래 유형·가격·주소는 임대인이 신청한 값이라 바꾸지 않는다.
	 *
	 * @throws PropertyException 이미 공개 등으로 신청 상태가 아닌 매물이거나, 층이 전체 층보다 큰 경우
	 */
	public void updateDetails(PropertyType propertyType, BigDecimal areaPyeong, Integer roomCount, Integer floor,
			Integer totalFloor, Integer householdCount, Integer parkingCount, String nearbySubwayStation) {
		validateEditable();
		if (floor != null && totalFloor != null && floor > totalFloor) {
			throw PropertyException.invalidFloor();
		}
		this.propertyType = propertyType;
		this.areaPyeong = areaPyeong;
		this.roomCount = roomCount;
		this.floor = floor;
		this.totalFloor = totalFloor;
		this.householdCount = householdCount;
		this.parkingCount = parkingCount;
		this.nearbySubwayStation = nearbySubwayStation;
	}

	/**
	 * 필수 상세 정보와 사진이 갖춰진 매물을 공개한다. 공개 후에는 수정할 수 없다 (PROPERTY_010).
	 * 세대수·주차 대수·지하철역은 없을 수 있는 정보라 필수로 두지 않는다.
	 *
	 * @throws PropertyException 공개 전이 아닌 매물이거나, 필수 정보나 사진이 없는 경우
	 */
	public void publish(boolean hasImage) {
		validateEditable();
		if (!hasImage || propertyType == null || areaPyeong == null || roomCount == null || floor == null
				|| totalFloor == null) {
			throw PropertyException.notPublishable();
		}
		this.propertyStatus = PropertyStatus.ACTIVE;
		this.publishedAt = LocalDateTime.now();
	}

	/**
	 * 중개사가 계약 정보를 등록하면 계약 완료가 된다. 공개 중인 매물만 계약할 수 있어 같은 매물을 두 번 계약하지 않는다 (PROPERTY_011).
	 *
	 * @throws PropertyException 공개 중(ACTIVE)이 아닌 매물인 경우
	 */
	public void contract() {
		if (propertyStatus != PropertyStatus.ACTIVE) {
			throw PropertyException.notContractable();
		}
		this.propertyStatus = PropertyStatus.CONTRACTED;
	}

	/**
	 * 상세 정보와 사진은 공개 전(REQUESTED)에만 바꿀 수 있다. 공개 직전 화면에서 수정할 수 없다고 안내한다 (PROPERTY_010).
	 *
	 * @throws PropertyException 이미 공개 등으로 신청 상태가 아닌 매물인 경우
	 */
	public void validateEditable() {
		if (propertyStatus != PropertyStatus.REQUESTED) {
			throw PropertyException.notEditable();
		}
	}
}
