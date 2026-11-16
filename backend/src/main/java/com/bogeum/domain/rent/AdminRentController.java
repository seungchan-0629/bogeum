package com.bogeum.domain.rent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.rent.model.RentDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminRentController {
	private final RentService rentService;

	/** 관리자 납부 내역: 예정일·회차 ID 내림차순, 페이지당 5건 (#38). */
	@GetMapping("/admin/rents/payments")
	public RentDto.PaymentListRes readPayments(@RequestParam(required = false) Long tenantId,
			@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page) {
		return RentDto.PaymentListRes.from(rentService.readAdminPayments(tenantId, keyword, page));
	}
}
