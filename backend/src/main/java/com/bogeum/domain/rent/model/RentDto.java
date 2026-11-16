package com.bogeum.domain.rent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.bogeum.common.enums.RentPaymentStatus;

import lombok.Builder;
import lombok.Getter;

/** 관리자 월세 납부 조회의 응답 DTO를 모은다. */
public class RentDto {

	/** 계약의 납부 회차만 제공하며 결제수단·거래 식별자·재시도 정보는 노출하지 않는다. */
	@Getter
	@Builder
	public static class PaymentItemRes {
		private Long rentPaymentId;
		private Long contractId;
		private Long tenantId;
		private String tenantName;
		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private Integer seq;
		private BigDecimal amount;
		private LocalDateTime dueAt;
		private LocalDateTime paidAt;
		private RentPaymentStatus rentPaymentStatus;

		/** 납부 회차와 계약의 임차인·매물을 목록 항목으로 변환한다. */
		public static PaymentItemRes from(RentPayment payment) {
			var contract = payment.getContract();
			var tenant = contract.getTenant();
			var property = contract.getProperty();
			return PaymentItemRes.builder()
				.rentPaymentId(payment.getRentPaymentId())
				.contractId(contract.getContractId())
				.tenantId(tenant.getUserId())
				.tenantName(tenant.getName())
				.propertyId(property.getPropertyId())
				.roadAddress(property.getRoadAddress())
				.detailAddress(property.getDetailAddress())
				.seq(payment.getSeq())
				.amount(payment.getAmount())
				.dueAt(payment.getDueAt())
				.paidAt(payment.getPaidAt())
				.rentPaymentStatus(payment.getRentPaymentStatus())
				.build();
		}
	}

	/** 납부 목록과 1부터 시작하는 페이지 정보를 제공한다. */
	@Getter
	@Builder
	public static class PaymentListRes {
		private List<PaymentItemRes> payments;
		private int page;
		private int totalPages;
		private long totalElements;

		/** 조회된 납부 페이지를 목록 항목과 페이지 메타데이터로 변환한다. */
		public static PaymentListRes from(Page<RentPayment> payments) {
			return PaymentListRes.builder()
				.payments(payments.getContent().stream().map(PaymentItemRes::from).toList())
				.page(payments.getNumber() + 1)
				.totalPages(payments.getTotalPages())
				.totalElements(payments.getTotalElements())
				.build();
		}
	}
}
