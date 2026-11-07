package com.bogeum.domain.property.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.PropertyType;
import com.bogeum.domain.agent.model.Agent;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 기능의 요청·응답 DTO.
 */
public class PropertyDto {

	/** 임대인의 매물 중개 신청. agentId가 없으면 서버가 중개사를 배정한다 (PROPERTY_002) */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BrokerageRequest {

		@NotNull(message = "임대 형식을 선택해 주세요.")
		private LeaseType leaseType;

		// 보증금 또는 전세금(원)
		@NotNull(message = "보증금을 입력해 주세요.")
		@PositiveOrZero(message = "보증금은 0원 이상으로 입력해 주세요.")
		private BigDecimal deposit;

		// 전세는 비워 두거나 0
		@PositiveOrZero(message = "월세는 0원 이상으로 입력해 주세요.")
		private BigDecimal monthlyRent;

		@NotBlank(message = "주소를 입력해 주세요.")
		@Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
		private String roadAddress;

		@Size(max = 100, message = "상세 주소는 100자 이하로 입력해 주세요.")
		private String detailAddress;

		// 희망 중개사의 프로필 ID. 로그인 응답의 agentId와 같은 값이다
		private Long agentId;
	}

	/** 저장된 중개 신청과 배정된 중개사 정보. */
	@Getter
	@Builder
	public static class BrokerageResponse {

		private Long brokerageRequestId;
		private Long propertyId;
		private Long agentId;
		private String officeName;
		private Boolean isDesignated;
		private BrokerageStatus brokerageStatus;
		private LocalDateTime createdAt;

		// 같은 이름의 요청 DTO와 구분하려고 엔티티는 전체 경로로 쓴다
		public static BrokerageResponse from(com.bogeum.domain.property.model.BrokerageRequest request, Agent agent) {
			return BrokerageResponse.builder()
				.brokerageRequestId(request.getBrokerageRequestId())
				.propertyId(request.getProperty().getPropertyId())
				.agentId(agent.getAgentProfileId())
				.officeName(agent.getOfficeName())
				.isDesignated(request.getIsDesignated())
				.brokerageStatus(request.getBrokerageStatus())
				.createdAt(request.getCreatedAt())
				.build();
		}
	}

	/** 중개사에게 온 중개 신청 목록의 한 줄 (PROPERTY_012) */
	@Getter
	@Builder
	public static class BrokerageItemResponse {

		private Long brokerageRequestId;
		private Long propertyId;
		private LeaseType leaseType;
		private BigDecimal deposit;
		private BigDecimal monthlyRent;
		private String roadAddress;
		private String detailAddress;
		private String landlordName;
		private Boolean isDesignated;
		private BrokerageStatus brokerageStatus;
		// 내 계약 관리 화면이 진행 단계를 계산하는 기준이다
		private PropertyStatus propertyStatus;
		private LocalDateTime createdAt;
		private LocalDateTime acceptedAt;

		public static BrokerageItemResponse from(com.bogeum.domain.property.model.BrokerageRequest request) {
			Property property = request.getProperty();
			return BrokerageItemResponse.builder()
				.brokerageRequestId(request.getBrokerageRequestId())
				.propertyId(property.getPropertyId())
				.leaseType(property.getLeaseType())
				.deposit(property.getDeposit())
				.monthlyRent(property.getMonthlyRent())
				.roadAddress(property.getRoadAddress())
				.detailAddress(property.getDetailAddress())
				.landlordName(request.getLandlord().getName())
				.isDesignated(request.getIsDesignated())
				.brokerageStatus(request.getBrokerageStatus())
				.propertyStatus(property.getPropertyStatus())
				.createdAt(request.getCreatedAt())
				.acceptedAt(request.getAcceptedAt())
				.build();
		}
	}

	/** 중개 신청 목록과 페이지 정보. page는 화면의 페이지 번호와 같이 1부터 센다 (PROPERTY_012) */
	@Getter
	@Builder
	public static class BrokerageListResponse {

		private List<BrokerageItemResponse> requests;
		private int page;
		private int totalPages;
		private long totalElements;

		public static BrokerageListResponse from(Page<com.bogeum.domain.property.model.BrokerageRequest> requests) {
			return BrokerageListResponse.builder()
				.requests(requests.map(BrokerageItemResponse::from).getContent())
				.page(requests.getNumber() + 1)
				.totalPages(requests.getTotalPages())
				.totalElements(requests.getTotalElements())
				.build();
		}
	}

	/**
	 * 중개사가 저장하는 매물 상세 정보 (PROPERTY_010).
	 * 임시 저장이라 모두 선택값이고, 비워 보내면 비운 채로 저장한다.
	 */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DetailRequest {

		private PropertyType propertyType;

		// 평 수. 컬럼이 소수 둘째 자리까지라 그 이상은 받지 않는다
		@Positive(message = "면적은 0보다 크게 입력해 주세요.")
		@Digits(integer = 5, fraction = 2, message = "면적은 소수 둘째 자리까지 입력해 주세요.")
		private BigDecimal areaPyeong;

		@Positive(message = "방 개수는 1개 이상으로 입력해 주세요.")
		private Integer roomCount;

		// 지하층은 음수로 받는다
		private Integer floor;

		@Positive(message = "전체 층은 1층 이상으로 입력해 주세요.")
		private Integer totalFloor;

		@Positive(message = "세대 수는 1 이상으로 입력해 주세요.")
		private Integer householdCount;

		@PositiveOrZero(message = "주차 대수는 0 이상으로 입력해 주세요.")
		private Integer parkingCount;

		// 여러 역은 쉼표로 구분한다
		@Size(max = 100, message = "주변 지하철역은 100자 이하로 입력해 주세요.")
		private String nearbySubwayStation;
	}

	/** 상세 정보를 저장한 결과. 임시 저장이라 상태는 공개 전(REQUESTED) 그대로다 */
	@Getter
	@Builder
	public static class DetailSaveResponse {

		private Long propertyId;
		private PropertyStatus propertyStatus;

		public static DetailSaveResponse from(Property property) {
			return DetailSaveResponse.builder()
				.propertyId(property.getPropertyId())
				.propertyStatus(property.getPropertyStatus())
				.build();
		}
	}

	/** 매물 사진 한 장. url은 저장소가 없으면 비어 있다 (PROPERTY_010) */
	@Getter
	@Builder
	public static class ImageResponse {

		private Long propertyImageId;
		private Integer sortOrder;
		private String url;

		public static ImageResponse from(PropertyImage image, String url) {
			return ImageResponse.builder()
				.propertyImageId(image.getPropertyImageId())
				.sortOrder(image.getSortOrder())
				.url(url)
				.build();
		}
	}

	/**
	 * 중개사의 매물 정보 올리기 화면 첫 값 (PROPERTY_010).
	 * 거래 유형·가격·주소는 임대인이 신청한 값이라 화면에서 읽기 전용이다.
	 */
	@Getter
	@Builder
	public static class DetailResponse {

		private Long propertyId;
		private String landlordName;
		private LeaseType leaseType;
		private BigDecimal deposit;
		private BigDecimal monthlyRent;
		private String roadAddress;
		private String detailAddress;
		private PropertyType propertyType;
		private BigDecimal areaPyeong;
		private Integer roomCount;
		private Integer floor;
		private Integer totalFloor;
		private Integer householdCount;
		private Integer parkingCount;
		private String nearbySubwayStation;
		private PropertyStatus propertyStatus;
		private List<ImageResponse> images;

		public static DetailResponse from(Property property, List<ImageResponse> images) {
			return DetailResponse.builder()
				.propertyId(property.getPropertyId())
				.landlordName(property.getLandlord().getName())
				.leaseType(property.getLeaseType())
				.deposit(property.getDeposit())
				.monthlyRent(property.getMonthlyRent())
				.roadAddress(property.getRoadAddress())
				.detailAddress(property.getDetailAddress())
				.propertyType(property.getPropertyType())
				.areaPyeong(property.getAreaPyeong())
				.roomCount(property.getRoomCount())
				.floor(property.getFloor())
				.totalFloor(property.getTotalFloor())
				.householdCount(property.getHouseholdCount())
				.parkingCount(property.getParkingCount())
				.nearbySubwayStation(property.getNearbySubwayStation())
				.propertyStatus(property.getPropertyStatus())
				.images(images)
				.build();
		}
	}

	/** 수락된 중개 신청. 채팅방(CHAT_002)이 생기면 chatRoomId를 더한다 (PROPERTY_008) */
	@Getter
	@Builder
	public static class AcceptResponse {

		private Long brokerageRequestId;
		private Long propertyId;
		private BrokerageStatus brokerageStatus;
		private LocalDateTime acceptedAt;

		public static AcceptResponse from(com.bogeum.domain.property.model.BrokerageRequest request) {
			return AcceptResponse.builder()
				.brokerageRequestId(request.getBrokerageRequestId())
				.propertyId(request.getProperty().getPropertyId())
				.brokerageStatus(request.getBrokerageStatus())
				.acceptedAt(request.getAcceptedAt())
				.build();
		}
	}
}
