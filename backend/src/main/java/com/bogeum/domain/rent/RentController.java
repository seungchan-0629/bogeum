package com.bogeum.domain.rent;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import java.util.List;
import com.bogeum.domain.openbanking.service.OpenBankingService;
import com.bogeum.domain.rent.model.RentDto.PaymentAccountRequest;
import com.bogeum.domain.rent.model.RentDto.PaymentAccountResponse;
import com.bogeum.domain.rent.model.RentDto.PaymentResponse;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants/me")
public class RentController {

	private final RentService rentService;
	private final OpenBankingService openBanking;

	@PostMapping("/payment-account")
	public PaymentAccountResponse registerAccount(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestBody PaymentAccountRequest request) {
		return PaymentAccountResponse.from(openBanking.registerAccount(user.getUserId(), request.getFintechUseNum()));
	}

	@PostMapping("/rent-payments/{rentPaymentId}/pay")
	public PaymentResponse pay(@AuthenticationPrincipal LoginUser user, @PathVariable("rentPaymentId") Long rentPaymentId) {
		return PaymentResponse.from(rentService.pay(rentPaymentId, user.getUserId()));
	}

	@GetMapping("/payment-account")
	public ResponseEntity<PaymentAccountResponse> account(@AuthenticationPrincipal LoginUser user) {
		var account = openBanking.paymentAccount(user.getUserId());
		return account == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(PaymentAccountResponse.from(account));
	}

	@GetMapping("/rent-payments")
	public List<PaymentResponse> payments(@AuthenticationPrincipal LoginUser user) {
		return rentService.findPayments(user.getUserId()).stream().map(PaymentResponse::from).toList();
	}
}
