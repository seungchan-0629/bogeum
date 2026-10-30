package com.bogeum.domain.settlement;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import com.bogeum.domain.settlement.model.SettlementDto.AccountRequest;
import com.bogeum.domain.settlement.model.SettlementDto.AccountResponse;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/landlords/me/settlement-account")
public class SettlementController {

	private final SettlementService settlementService;

	@PostMapping
	public AccountResponse register(@AuthenticationPrincipal LoginUser user, @Valid @RequestBody AccountRequest request) {
		return AccountResponse.from(settlementService.registerAccount(user.getUserId(), request.getBankCode(),
			request.getAccountNumber(), request.getHolderName()));
	}

	@GetMapping
	public ResponseEntity<AccountResponse> account(@AuthenticationPrincipal LoginUser user) {
		var account = settlementService.findAccount(user.getUserId());
		return account == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(AccountResponse.from(account));
	}
}
