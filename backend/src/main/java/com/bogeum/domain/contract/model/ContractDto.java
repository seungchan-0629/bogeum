package com.bogeum.domain.contract.model;

import java.math.BigDecimal;
import java.util.List;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import lombok.Builder;
import lombok.Getter;

public class ContractDto {

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
