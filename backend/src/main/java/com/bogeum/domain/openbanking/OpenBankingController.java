package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBanking;
import com.bogeum.domain.openbanking.model.OpenBankingDTO.*;
import com.bogeum.domain.openbanking.model.OpenBankingTransfer;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
	private final OpenBankingTransferRepository openBankingTransferRepository;

	@PostMapping("/authorize")
	public Map<String, String> authorize(@AuthenticationPrincipal LoginUser loginUser) {
		return Map.of("authorizationUrl", openBankingService.createAuthorizationUrl(loginUser.getUserId()));
	}

	@GetMapping("/callback")
	public Map<String, Long> registerAccount(@RequestParam String code, @RequestParam String state) {
		OpenBanking account = openBankingService.registerAccount(code, state);
		return Map.of("openBankingId", account.getOpenBankingId());
	}

	@PostMapping("/accounts/{openBankingId}/withdrawals")
	public WithdrawRes withdraw(@PathVariable Long openBankingId, @Valid @RequestBody WithdrawReq request,
			@RequestHeader("X-Openbanking-Client-Id") String clientId,
			@RequestHeader("X-Openbanking-Client-Secret") String clientSecret,
			@RequestHeader("X-Openbanking-Scope") String scope) {
		LocalDateTime requestedAt = LocalDateTime.now();
		WithdrawRes response = openBankingService.withdraw(findAccount(openBankingId), request,
			new TokenRefreshReq(clientId, clientSecret, null, scope, "refresh_token"));
		openBankingTransferRepository.save(OpenBankingTransfer.builder()
			.bankTranId(request.getBankTranId())
			.transfer_type(TransferType.WITHDRAW)
			.requestAmount(new BigDecimal(request.getTranAmt()))
			.requestedAt(requestedAt)
			.status(resolveTransferStatus(response.getRspCode(), response.getBankRspCode()))
			.bankTranDate(parseBankTranDate(response.getBankTranDate()))
			.apiTranId(response.getApiTranId())
			.apiResponseCode(response.getRspCode())
			.apiResponseMessage(response.getRspMessage())
			.bankResponseCode(response.getBankRspCode())
			.bankResponseMessage(response.getBankRspMessage()).build());
		return response;
	}

	@PostMapping("/remittances")
	public RemittanceRes remit(@RequestHeader("X-Openbanking-Institution-Token") String institutionAccessToken,
			@RequestParam String reqClientFintechUseNum, @Valid @RequestBody RemittanceReq request) {
		LocalDateTime requestedAt = LocalDateTime.now();
		RemittanceRes response = openBankingService.remit(institutionAccessToken, request, reqClientFintechUseNum);
		RemittanceItemReq item = request.getReqList().get(0);
		RemittanceItemRes result = response.getResList().get(0);
		openBankingTransferRepository.save(OpenBankingTransfer.builder()
			.bankTranId(item.getBankTranId())
			.transfer_type(TransferType.REMITTANCE)
			.requestAmount(new BigDecimal(item.getTranAmt()))
			.requestedAt(requestedAt)
			.status(resolveTransferStatus(response.getRspCode(), result.getBankRspCode()))
			.bankTranDate(parseBankTranDate(result.getBankTranDate()))
			.apiTranId(response.getApiTranId())
			.apiResponseCode(response.getRspCode())
			.apiResponseMessage(response.getRspMessage())
			.bankResponseCode(result.getBankRspCode())
			.bankResponseMessage(result.getBankRspMessage()).build());
		return response;
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

	private TransferStatus resolveTransferStatus(String apiResponseCode, String bankResponseCode) {
		if (bankResponseCode == null || bankResponseCode.isBlank()) {
			return TransferStatus.PENDING_CONFIRMATION;
		}
		return "A0000".equals(apiResponseCode) && "000".equals(bankResponseCode)
			? TransferStatus.SUCCESS : TransferStatus.FAILED;
	}

	private LocalDate parseBankTranDate(String bankTranDate) {
		return bankTranDate == null || bankTranDate.isBlank() ? null
			: LocalDate.parse(bankTranDate, DateTimeFormatter.BASIC_ISO_DATE);
	}
}
