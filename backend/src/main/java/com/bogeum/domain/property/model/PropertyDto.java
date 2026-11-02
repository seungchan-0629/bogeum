package com.bogeum.domain.property.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.domain.agent.model.Agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
