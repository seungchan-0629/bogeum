package com.bogeum.domain.rent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.enums.RentPaymentStatus;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.service.OpenBankingService;
import com.bogeum.domain.openbanking.support.OpenBankingClient;
import com.bogeum.domain.openbanking.support.OpenBankingClient.TransferResult;
import com.bogeum.domain.rent.model.RentPayment;
import com.bogeum.domain.rent.service.RentTransferService;
import com.bogeum.domain.settlement.SettlementService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RentService {
	private final RentRepository rents;
	private final RentTransferService transfers;
	private final OpenBankingService openBanking;
	private final OpenBankingClient client;
	private final SettlementService settlements;

	/** 납부할 금액·수취인은 DB의 계약에서만 가져온다. */
	public RentPayment pay(Long rentPaymentId, Long tenantId) {
		// 외부 토큰 발급 실패는 이체 요청을 만들기 전에 처리한다.
		var existing = findPayment(rentPaymentId, tenantId);
		if (existing.getRentPaymentStatus() == RentPaymentStatus.PAID) {
			return existing;
		}
		if (existing.getRentPaymentStatus() == RentPaymentStatus.PROCESSING
				|| existing.getRentPaymentStatus() == RentPaymentStatus.UNKNOWN) {
			throw OpenBankingException.of(ErrorCode.PAYMENT_IN_PROGRESS);
		}
		String accessToken = openBanking.accessToken(tenantId);
		var withdrawal = transfers.prepare(rentPaymentId, tenantId, client.newBankTranId());
		if (withdrawal == null) {
			return findPayment(rentPaymentId, tenantId);
		}
		TransferResult result;
		try {
			result = client.withdraw(accessToken, withdrawal.bankTranId(), withdrawal.fintechUseNum(),
				withdrawal.amount(), withdrawal.tenantName(), withdrawal.tenantNum());
		} catch (RuntimeException exception) {
			transfers.complete(rentPaymentId, tenantId, withdrawal.attemptId(), false, "RESPONSE_UNKNOWN");
			throw exception;
		}
		transfers.complete(rentPaymentId, tenantId, withdrawal.attemptId(), result.succeeded(), result.code());
		if (result.succeeded()) {
			// 지급 장애가 이미 완료된 임차인 수납을 실패로 바꾸지 않는다.
			settlements.trySettle(rentPaymentId);
		}
		return findPayment(rentPaymentId, tenantId);
	}

	@Transactional(readOnly = true)
	public List<RentPayment> findPayments(Long tenantId) {
		return rents.findByContractTenantUserIdOrderByDueAtDesc(tenantId);
	}

	@Transactional(readOnly = true)
	public RentPayment findPayment(Long rentPaymentId, Long tenantId) {
		return rents.findByRentPaymentIdAndContractTenantUserId(rentPaymentId, tenantId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.RENT_PAYMENT_NOT_FOUND));
	}
}
