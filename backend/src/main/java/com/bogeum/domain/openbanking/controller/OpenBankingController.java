package com.bogeum.domain.openbanking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bogeum.domain.openbanking.model.OpenBankingDto.AccountResponse;
import com.bogeum.domain.openbanking.model.OpenBankingDto.AuthorizationResponse;
import com.bogeum.domain.openbanking.model.OpenBankingDto.CallbackRequest;
import com.bogeum.domain.openbanking.service.OpenBankingService;
import com.bogeum.domain.user.model.UserDto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 은행 콜백은 프론트가 받아 기존 JWT·CSRF 인증으로 이 API에 전달한다. */
@RestController
@RequestMapping("/api/v1/tenants/me/open-banking")
@RequiredArgsConstructor
public class OpenBankingController {
	private final OpenBankingService service;

	@PostMapping("/authorization")
	public AuthorizationResponse authorize(@AuthenticationPrincipal LoginUser user) {
		return AuthorizationResponse.builder().authorizationUrl(service.beginAuthorization(user.getUserId())).build();
	}

	@PostMapping("/callback")
	public ResponseEntity<Void> callback(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestBody CallbackRequest request) {
		service.completeAuthorization(user.getUserId(), request.getCode(), request.getState());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/accounts")
	public List<AccountResponse> accounts(@AuthenticationPrincipal LoginUser user) {
		return service.accounts(user.getUserId()).stream().map(AccountResponse::from).toList();
	}
}
