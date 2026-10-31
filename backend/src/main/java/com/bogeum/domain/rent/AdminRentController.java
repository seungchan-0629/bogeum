package com.bogeum.domain.rent;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import com.bogeum.domain.settlement.SettlementService;
import com.bogeum.domain.rent.model.RentDto.PaymentResponse;
import com.bogeum.domain.rent.model.RentDto.ResolutionRequest;
import com.bogeum.domain.rent.model.RentDto.TransferStatus;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminRentController {

	private final RentService rentService;
	private final SettlementService settlementService;

	/** 미전송 지급 또는 증빙으로 실패가 확정된 지급만 처리한다. */
	@PostMapping("/rents/{rentPaymentId}/settlement")
	public ResponseEntity<Void> settle(@PathVariable("rentPaymentId") Long rentPaymentId) {
		settlementService.settle(rentPaymentId);
		return ResponseEntity.noContent().build();
	}

	/** 금액·수취인·납부일은 요청 대신 DB 계약에서 가져온다. */
	@PostMapping("/contracts/{contractId}/rent-payments")
	public List<PaymentResponse> generatePayments(@PathVariable("contractId") Long contractId) {
		return rentService.generatePayments(contractId).stream().map(PaymentResponse::from).toList();
	}

	@GetMapping("/contracts/{contractId}/rent-payments")
	public List<PaymentResponse> contractPayments(@PathVariable("contractId") Long contractId) {
		return rentService.findContractPayments(contractId).stream().map(PaymentResponse::from).toList();
	}

	@PostMapping("/rents/{rentPaymentId}/collection-resolution")
	public ResponseEntity<Void> resolveCollection(@PathVariable("rentPaymentId") Long rentPaymentId,
			@AuthenticationPrincipal LoginUser admin, @Valid @RequestBody ResolutionRequest request) {
		rentService.resolveCollection(rentPaymentId, request.bankTranId(), request.succeeded(), request.evidenceReference(), admin.getUserId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/rents/{rentPaymentId}/settlement-resolution")
	public ResponseEntity<Void> resolveSettlement(@PathVariable("rentPaymentId") Long rentPaymentId,
			@AuthenticationPrincipal LoginUser admin, @Valid @RequestBody ResolutionRequest request) {
		settlementService.resolve(rentPaymentId, request.bankTranId(), request.succeeded(), request.evidenceReference(), admin.getUserId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/rents/{rentPaymentId}/transfer-status")
	public TransferStatus transferStatus(@PathVariable("rentPaymentId") Long rentPaymentId) {
		return rentService.transferStatus(rentPaymentId);
	}
}
