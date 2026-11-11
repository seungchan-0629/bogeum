package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBanking;
import com.bogeum.domain.openbanking.model.OpenBankingDTO.*;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/openbanking")
@RequiredArgsConstructor
public class OpenBankingController {

	private final OpenBankingService openBankingService;
	private final OpenBankingRepository openBankingRepository;

	@GetMapping("/callback")
	public Map<String, Long> registerAccount(@RequestParam String code, @RequestParam String state) {
		OpenBanking account = openBankingService.registerAccount(code, state);
		return Map.of("openBankingId", account.getOpenBankingId());
	}

	@PostMapping("/accounts/{openBankingId}/withdrawals")
	public WithdrawRes withdraw(@PathVariable Long openBankingId, @Valid @RequestBody WithdrawReq request,
			@AuthenticationPrincipal LoginUser loginUser,
			@RequestHeader("X-Openbanking-Client-Id") String clientId,
			@RequestHeader("X-Openbanking-Client-Secret") String clientSecret,
			@RequestHeader("X-Openbanking-Scope") String scope) {
		return openBankingService.withdraw(loginUser.getUserId(), openBankingId, request,
			new TokenRefreshReq(clientId, clientSecret, null, scope, "refresh_token"));
	}

	@PostMapping("/remittances")
	public RemittanceRes remit(@RequestHeader("X-Openbanking-Institution-Token") String institutionAccessToken,
			@RequestParam String reqClientFintechUseNum, @Valid @RequestBody RemittanceReq request) {
		return openBankingService.remit(institutionAccessToken, request, reqClientFintechUseNum);
	}

	@PostMapping("/tokens/institution")
	public InstitutionTokenRes issueInstitutionToken(@Valid @RequestBody InstitutionTokenReq request) {
		return openBankingService.issueInstitutionToken(request);
	}

	/** 로그인한 사용자의 등록 계좌를 반환하고, 미등록이면 204를 반환한다. */
	@GetMapping("/accounts/me")
	public ResponseEntity<AccountResponse> getMyAccount(@AuthenticationPrincipal LoginUser loginUser) {
		return openBankingService.findAccountByUserId(loginUser.getUserId())
			.map(AccountResponse::from)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** 내 집 관리: 본인이 임차인인 계약의 출금 내역. */
	@GetMapping("/transfers/withdrawals/me")
	public TransferListResponse getMyWithdrawals(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) Long contractId, @RequestParam(required = false) Long propertyId,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int size) {
		return TransferListResponse.from(openBankingService.findWithdrawals(loginUser.getUserId(),
			contractId, propertyId, year, month, cursor, size));
	}

	/** 내 매물의 월세 관리: 본인이 임대인인 계약의 송금 내역. */
	@GetMapping("/transfers/remittances/me")
	public TransferListResponse getMyRemittances(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) Long contractId, @RequestParam(required = false) Long propertyId,
			@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
			@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int size) {
		return TransferListResponse.from(openBankingService.findRemittances(loginUser.getUserId(),
			contractId, propertyId, year, month, cursor, size));
	}

	@GetMapping("/accounts/{openBankingId}")
	public RegisteredAccountRes getRegisteredAccounts(@PathVariable Long openBankingId,
			@RequestParam(defaultValue = "N") String includeCancelYn,
			@RequestParam(defaultValue = "D") String sortOrder,
			@RequestHeader("X-Openbanking-Client-Id") String clientId,
			@RequestHeader("X-Openbanking-Client-Secret") String clientSecret,
			@RequestHeader("X-Openbanking-Scope") String scope) {
		OpenBanking account = findAccount(openBankingId);
		return openBankingService.getRegisteredAccounts(account,
			new RegisteredAccountReq(account.getUserSeqNo(), includeCancelYn, sortOrder),
			new TokenRefreshReq(clientId, clientSecret, null, scope, "refresh_token"));
	}

	@PostMapping("/transfers/results")
	public TransferResultRes getTransferResult(
			@RequestHeader("X-Openbanking-Institution-Token") String institutionAccessToken,
			@Valid @RequestBody TransferResultReq request) {
		return openBankingService.getTransferResult(institutionAccessToken, request);
	}

	@PostMapping("/accounts/{openBankingId}/tokens/refresh")
	public UserTokenRes refreshToken(@PathVariable Long openBankingId,
			@Valid @RequestBody TokenRefreshReq request) {
		return openBankingService.refreshToken(findAccount(openBankingId), request);
	}

	private OpenBanking findAccount(Long openBankingId) {
		return openBankingRepository.findById(openBankingId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 계좌를 찾을 수 없습니다."));
	}

}
