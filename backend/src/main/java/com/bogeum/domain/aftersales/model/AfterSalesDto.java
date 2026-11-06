package com.bogeum.domain.aftersales.model;

import com.bogeum.common.enums.AsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AS 최초 접수의 요청·응답 DTO (AS_001). */
public class AfterSalesDto {

	/** 계약 번호와 수리 요청 내용만 받는다. 임차인과 매물은 서버가 계약에서 확인한다. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CreateRequest {
		@NotNull
		@Positive
		private Long contractId;

		@NotBlank
		@Size(max = 2000)
		private String content;
	}

	/** 저장된 접수 번호와 상태만 반환하며 계약·사용자 엔티티는 노출하지 않는다. */
	@Getter
	@Builder
	public static class CreateResponse {
		private Long asRequestId;
		private AsStatus asStatus;

		public static CreateResponse from(AfterSalesRequest request) {
			return CreateResponse.builder().asRequestId(request.getAsRequestId())
				.asStatus(request.getAsStatus()).build();
		}
	}
}
