package com.bogeum.domain.contract.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ContractDto {

	/**
	 * 중개사의 계약 정보 등록 (PROPERTY_011).
	 * 임대인과 거래 유형은 매물에서 정하고, 임차인은 계약 당사자 후보에서 고른다. 날짜는 일 단위로 받는다.
	 */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ContractRequest {

		@NotNull(message = "매물을 선택해 주세요.")
		private Long propertyId;

		@NotNull(message = "임차인을 선택해 주세요.")
		private Long tenantId;

		// 보증금 또는 전세금(원). 협상으로 신청 가격과 달라질 수 있다
		@NotNull(message = "보증금을 입력해 주세요.")
		@PositiveOrZero(message = "보증금은 0원 이상으로 입력해 주세요.")
		@Digits(integer = 15, fraction = 0, message = "보증금은 원 단위로 입력해 주세요.")
		private BigDecimal deposit;

		// 전세는 비워 두거나 0
		@PositiveOrZero(message = "월세는 0원 이상으로 입력해 주세요.")
		@Digits(integer = 15, fraction = 0, message = "월세는 원 단위로 입력해 주세요.")
		private BigDecimal monthlyRent;

		// 월세 자동 결제일(매월 며칠). 전세는 비워 둔다
		@Min(value = 1, message = "월 납부일은 1일부터 31일까지 입력해 주세요.")
		@Max(value = 31, message = "월 납부일은 1일부터 31일까지 입력해 주세요.")
		private Integer paymentDay;

		@NotNull(message = "계약 시작일을 입력해 주세요.")
		private LocalDate startAt;

		@NotNull(message = "계약 종료일을 입력해 주세요.")
		private LocalDate endAt;

		@NotNull(message = "계약일을 입력해 주세요.")
		private LocalDate contractedAt;

		@NotNull(message = "중개보수를 입력해 주세요.")
		@PositiveOrZero(message = "중개보수는 0원 이상으로 입력해 주세요.")
		@Digits(integer = 15, fraction = 0, message = "중개보수는 원 단위로 입력해 주세요.")
		private BigDecimal brokerageFee;
	}

	/** 등록한 계약과 계약 완료로 바뀐 매물 상태 (PROPERTY_011) */
	@Getter
	@Builder
	public static class SaveResponse {

		private Long contractId;
		private Long propertyId;
		private PropertyStatus propertyStatus;

		public static SaveResponse from(Contract contract) {
			return SaveResponse.builder()
				.contractId(contract.getContractId())
				.propertyId(contract.getProperty().getPropertyId())
				.propertyStatus(contract.getProperty().getPropertyStatus())
				.build();
		}
	}

	/**
	 * 중개 이력의 계약 한 건 (AGENT_003).
	 * 거래 조건은 신청 가격이 아니라 실제로 계약한 금액이다. 날짜는 일 단위로 등록받아 날짜만 준다.
	 */
	@Getter
	@Builder
	public static class ContractItemResponse {

		private Long contractId;
		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private LeaseType leaseType;
		private BigDecimal deposit;
		private BigDecimal monthlyRent;
		private BigDecimal brokerageFee;
		private String landlordName;
		private String tenantName;
		private LocalDate startAt;
		private LocalDate endAt;
		private LocalDate contractedAt;

		public static ContractItemResponse from(Contract contract) {
			Property property = contract.getProperty();
			return ContractItemResponse.builder()
				.contractId(contract.getContractId())
				.propertyId(property.getPropertyId())
				.roadAddress(property.getRoadAddress())
				.detailAddress(property.getDetailAddress())
				.leaseType(contract.getLeaseType())
				.deposit(contract.getDeposit())
				.monthlyRent(contract.getMonthlyRent())
				.brokerageFee(contract.getBrokerageFee())
				.landlordName(contract.getLandlord().getName())
				.tenantName(contract.getTenant().getName())
				.startAt(contract.getStartAt().toLocalDate())
				.endAt(contract.getEndAt().toLocalDate())
				.contractedAt(contract.getContractedAt().toLocalDate())
				.build();
		}
	}

	/** 중개 이력 목록과 페이지 정보. page는 화면의 페이지 번호와 같이 1부터 센다 (AGENT_003) */
	@Getter
	@Builder
	public static class ContractListResponse {

		private List<ContractItemResponse> contracts;
		private int page;
		private int totalPages;
		private long totalElements;

		public static ContractListResponse from(Page<Contract> contracts) {
			return ContractListResponse.builder()
				.contracts(contracts.map(ContractItemResponse::from).getContent())
				.page(contracts.getNumber() + 1)
				.totalPages(contracts.getTotalPages())
				.totalElements(contracts.getTotalElements())
				.build();
		}
	}

	/**
	 * 계약 당사자 후보 (PROPERTY_011).
	 * 이름이 같은 사람을 구분할 수 있게 이메일을 주되, 앞 두 글자만 남겨 다른 회원 정보가 드러나지 않게 한다.
	 */
	@Getter
	@Builder
	public static class PartyResponse {

		private Long userId;
		private String name;
		private String maskedEmail;

		public static PartyResponse from(User user) {
			return PartyResponse.builder()
				.userId(user.getUserId())
				.name(user.getName())
				.maskedEmail(maskEmail(user.getEmail()))
				.build();
		}

		// "tenant@example.com" → "te****@example.com"
		private static String maskEmail(String email) {
			int atIndex = email.indexOf('@');
			return email.substring(0, Math.min(2, atIndex)) + "****" + email.substring(atIndex);
		}
	}

	/**
	 * 중개사의 계약 등록 화면 첫 값 (PROPERTY_011).
	 * 임대인은 매물의 임대인으로 정해져 있고, 임차인은 후보 중에서 고른다. 신청 가격은 계약 금액의 첫 값이다.
	 */
	@Getter
	@Builder
	public static class CandidateResponse {

		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private LeaseType leaseType;
		private BigDecimal deposit;
		private BigDecimal monthlyRent;
		private PropertyStatus propertyStatus;
		private PartyResponse landlord;
		private List<PartyResponse> tenants;

		public static CandidateResponse from(Property property, List<User> tenants) {
			return CandidateResponse.builder()
				.propertyId(property.getPropertyId())
				.roadAddress(property.getRoadAddress())
				.detailAddress(property.getDetailAddress())
				.leaseType(property.getLeaseType())
				.deposit(property.getDeposit())
				.monthlyRent(property.getMonthlyRent())
				.propertyStatus(property.getPropertyStatus())
				.landlord(PartyResponse.from(property.getLandlord()))
				.tenants(tenants.stream().map(PartyResponse::from).toList())
				.build();
		}
	}
}
