package com.bogeum.domain.rent;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import com.bogeum.domain.settlement.SettlementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rents")
public class AdminRentController {

	private final RentService rentService;
	private final SettlementService settlementService;

	/** 아직 전송하지 않은 보류 지급만 처리한다. 결과 미확정 건은 서비스가 거절한다. */
	@PostMapping("/{rentPaymentId}/settlement")
	public ResponseEntity<Void> settle(@PathVariable("rentPaymentId") Long rentPaymentId) {
		settlementService.settle(rentPaymentId);
		return ResponseEntity.noContent().build();
	}
}
