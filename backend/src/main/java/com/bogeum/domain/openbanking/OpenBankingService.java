package com.bogeum.domain.openbanking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.PaymentMethodType;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.openbanking.model.OpenBankingConnection;
import com.bogeum.domain.openbanking.OpenBankingClient.Account;
import com.bogeum.domain.openbanking.OpenBankingClient.Token;
import com.bogeum.domain.rent.model.PaymentMethod;
import com.bogeum.domain.rent.RentRepository.PaymentMethodRepository;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.utils.SecretCipher;

import lombok.RequiredArgsConstructor;

/** 외부 인증 토큰과 핀테크이용번호를 로그인 회원에게 귀속시킨다. */
@Service
@RequiredArgsConstructor
public class OpenBankingService {
	private final OpenBankingClient client;
	private final OpenBankingRepository connections;
	private final PaymentMethodRepository paymentMethods;
	private final UserRepository users;
	private final SecretCipher cipher;

	@Transactional
	public String beginAuthorization(Long userId) {
		var user = users.findLockedById(userId).orElseThrow(() -> OpenBankingException.of(ErrorCode.AUTH_REQUIRED));
		byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		String url = client.authorizationUrl(state);
		var connection = connections.findByUserUserId(userId)
			.orElseGet(() -> connections.save(OpenBankingConnection.builder().user(user).build()));
		connection.beginAuthorization(hash(state));
		return url;
	}

	@Transactional
	public PaymentMethod registerAccount(Long userId, String fintechUseNum) {
		var user = users.findLockedById(userId).orElseThrow(() -> OpenBankingException.of(ErrorCode.AUTH_REQUIRED));
		var connection = connection(userId);
		Account account = client.accounts(validAccessToken(connection), connection.getUserSeqNo()).stream()
			.filter(candidate -> candidate.fintechUseNum().equals(fintechUseNum)
				&& candidate.withdrawalAgreed() && candidate.holderName().equals(user.getName()))
			.findFirst().orElseThrow(() -> OpenBankingException.of(ErrorCode.PAYMENT_ACCOUNT_REQUIRED));
		paymentMethods.findByTenantUserIdAndPaymentMethodStatus(userId, PaymentMethodStatus.ACTIVE)
			.forEach(PaymentMethod::replace);
		return paymentMethods.save(PaymentMethod.builder().tenant(user).methodType(PaymentMethodType.ACCOUNT)
			.fintechUseNum(account.fintechUseNum()).bankCode(account.bankCode())
			.accountNumberMasked(account.accountNumberMasked()).withdrawalConsentedAt(LocalDateTime.now()).build());
	}

	@Transactional
	public List<Account> accounts(Long userId) {
		var connection = connection(userId);
		return client.accounts(validAccessToken(connection), connection.getUserSeqNo());
	}

	@Transactional(readOnly = true)
	public PaymentMethod paymentAccount(Long userId) {
		return paymentMethods.findByTenantUserIdAndPaymentMethodStatus(userId, PaymentMethodStatus.ACTIVE)
			.stream().findFirst().orElse(null);
	}

	@Transactional
	public String accessToken(Long userId) {
		return validAccessToken(connection(userId));
	}

	@Transactional
	public void completeAuthorization(Long userId, String code, String state) {
		var connection = connection(userId);
		if (connection.getStateHash() == null || connection.getStateExpiresAt() == null
				|| !connection.getStateExpiresAt().isAfter(LocalDateTime.now())
				|| !MessageDigest.isEqual(connection.getStateHash().getBytes(StandardCharsets.UTF_8),
					hash(state).getBytes(StandardCharsets.UTF_8))) {
			throw OpenBankingException.of(ErrorCode.OPEN_BANKING_AUTH_INVALID);
		}
		Token token = client.exchangeCode(code);
		validateToken(token);
		connection.authorize(token.userSeqNo(), cipher.encrypt(token.accessToken()),
			cipher.encrypt(token.refreshToken()), token.expiresIn());
		// 다른 오픈뱅킹 사용자로 다시 인증했다면 이전 사용자의 계좌로 출금할 수 없다.
		paymentMethods.findByTenantUserIdAndPaymentMethodStatus(userId, PaymentMethodStatus.ACTIVE)
			.forEach(PaymentMethod::replace);
	}

	private OpenBankingConnection connection(Long userId) {
		return connections.findByUserUserId(userId)
			.orElseThrow(() -> OpenBankingException.of(ErrorCode.OPEN_BANKING_NOT_CONNECTED));
	}

	private String validAccessToken(OpenBankingConnection connection) {
		if (connection.getAccessTokenEncrypted() == null || connection.getTokenExpiresAt() == null) {
			throw OpenBankingException.of(ErrorCode.OPEN_BANKING_NOT_CONNECTED);
		}
		if (connection.getTokenExpiresAt().isBefore(LocalDateTime.now().plusSeconds(60))) {
			Token token = client.refreshToken(cipher.decrypt(connection.getRefreshTokenEncrypted()));
			validateToken(token);
			if (!connection.getUserSeqNo().equals(token.userSeqNo())) {
				throw OpenBankingException.invalidResponse();
			}
			connection.updateTokens(token.userSeqNo(), cipher.encrypt(token.accessToken()),
				cipher.encrypt(token.refreshToken()), token.expiresIn());
		}
		return cipher.decrypt(connection.getAccessTokenEncrypted());
	}

	private void validateToken(Token token) {
		if (token.accessToken() == null || token.accessToken().isBlank()
				|| token.refreshToken() == null || token.refreshToken().isBlank()
				|| token.userSeqNo() == null || token.userSeqNo().isBlank()
				|| token.scope() == null || !Arrays.asList(token.scope().split("\\s+")).containsAll(List.of("inquiry", "transfer"))
				|| token.expiresIn() <= 60) {
			throw OpenBankingException.invalidResponse();
		}
	}

	private String hash(String state) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(state.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
