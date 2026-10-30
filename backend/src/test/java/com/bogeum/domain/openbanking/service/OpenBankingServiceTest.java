package com.bogeum.domain.openbanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bogeum.common.enums.PaymentMethodStatus;
import com.bogeum.common.enums.PaymentMethodType;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.config.OpenBankingProperties;
import com.bogeum.domain.openbanking.OpenBankingException;
import com.bogeum.domain.openbanking.model.OpenBankingConnection;
import com.bogeum.domain.openbanking.repository.OpenBankingConnectionRepository;
import com.bogeum.domain.openbanking.support.OpenBankingClient;
import com.bogeum.domain.openbanking.support.OpenBankingClient.Account;
import com.bogeum.domain.openbanking.support.OpenBankingClient.Token;
import com.bogeum.domain.rent.model.PaymentMethod;
import com.bogeum.domain.rent.repository.PaymentMethodRepository;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.SecretCipher;

/** 외부 응답만 대체하고 OAuth 회원 귀속·출금 동의·토큰 암호화를 검증한다. */
@ExtendWith(MockitoExtension.class)
class OpenBankingServiceTest {

	private static final Long USER_ID = 1L;
	private static final String FINTECH_USE_NUM = "199999999900000000000001";
	private static final String STATE = "owner-specific-one-time-state";
	private static final String USER_SEQ_NO = "1234567890";

	@Mock
	private OpenBankingClient client;
	@Mock
	private OpenBankingConnectionRepository connections;
	@Mock
	private PaymentMethodRepository paymentMethods;
	@Mock
	private UserRepository users;

	private final User user = User.builder().userId(USER_ID).name("임차인").build();
	private SecretCipher cipher;
	private OpenBankingService service;

	@BeforeEach
	void setUp() {
		var properties = new OpenBankingProperties(false, null, null, null, null, null, null, null,
			Base64.getEncoder().encodeToString(new byte[32]), null);
		cipher = new SecretCipher(properties);
		service = new OpenBankingService(client, connections, paymentMethods, users, cipher);
	}

	@Test
	@DisplayName("인증 state는 로그인 회원에게 해시로 저장되고 10분 뒤 만료된다")
	void createsUserBoundAuthorizationState() throws Exception {
		when(users.findLockedById(USER_ID)).thenReturn(Optional.of(user));
		when(connections.findByUserUserId(USER_ID)).thenReturn(Optional.empty());
		when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(client.authorizationUrl(anyString())).thenReturn("https://bank.example/authorize");
		LocalDateTime before = LocalDateTime.now();

		assertEquals("https://bank.example/authorize", service.beginAuthorization(USER_ID));

		var state = ArgumentCaptor.forClass(String.class);
		verify(client).authorizationUrl(state.capture());
		var saved = ArgumentCaptor.forClass(OpenBankingConnection.class);
		verify(connections).save(saved.capture());
		assertSame(user, saved.getValue().getUser());
		assertTrue(state.getValue().matches("[A-Za-z0-9_-]{43}"));
		assertEquals(hash(state.getValue()), saved.getValue().getStateHash());
		assertNotEquals(state.getValue(), saved.getValue().getStateHash());
		assertFalse(saved.getValue().getStateExpiresAt().isBefore(before.plusMinutes(10)));
		assertFalse(saved.getValue().getStateExpiresAt().isAfter(LocalDateTime.now().plusMinutes(10)));
	}

	@Test
	@DisplayName("정상 인증은 토큰을 암호화하고 state를 폐기하며 기존 출금 계좌를 교체 상태로 바꾼다")
	void authorizesAndInvalidatesPreviousAccount() throws Exception {
		var connection = pendingConnection(USER_ID, STATE, LocalDateTime.now().plusMinutes(5));
		var previous = PaymentMethod.builder().tenant(user).fintechUseNum(FINTECH_USE_NUM).build();
		when(client.exchangeCode("authorization-code")).thenReturn(token("access-secret", "refresh-secret", USER_SEQ_NO));
		when(paymentMethods.findByTenantUserIdAndPaymentMethodStatus(USER_ID, PaymentMethodStatus.ACTIVE))
			.thenReturn(List.of(previous));

		service.completeAuthorization(USER_ID, "authorization-code", STATE);

		assertEquals(USER_SEQ_NO, connection.getUserSeqNo());
		assertNotEquals("access-secret", connection.getAccessTokenEncrypted());
		assertNotEquals("refresh-secret", connection.getRefreshTokenEncrypted());
		assertEquals("access-secret", cipher.decrypt(connection.getAccessTokenEncrypted()));
		assertEquals("refresh-secret", cipher.decrypt(connection.getRefreshTokenEncrypted()));
		assertTrue(connection.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(50)));
		assertNull(connection.getStateHash());
		assertNull(connection.getStateExpiresAt());
		assertEquals(PaymentMethodStatus.REPLACED, previous.getPaymentMethodStatus());
	}

	@Test
	@DisplayName("다른 회원에게 발급된 state로는 인증 코드를 교환하지 않는다")
	void rejectsAnotherUsersState() throws Exception {
		pendingConnection(2L, "second-user-state", LocalDateTime.now().plusMinutes(5));

		assertError(ErrorCode.OPEN_BANKING_AUTH_INVALID,
			() -> service.completeAuthorization(2L, "authorization-code", STATE));

		verifyNoInteractions(client, paymentMethods);
	}

	@Test
	@DisplayName("만료된 state는 은행에 인증 코드를 보내기 전에 거부한다")
	void rejectsExpiredState() throws Exception {
		pendingConnection(USER_ID, STATE, LocalDateTime.now().minusSeconds(1));

		assertError(ErrorCode.OPEN_BANKING_AUTH_INVALID,
			() -> service.completeAuthorization(USER_ID, "authorization-code", STATE));

		verifyNoInteractions(client, paymentMethods);
	}

	@Test
	@DisplayName("이미 사용한 state로 인증을 다시 완료할 수 없다")
	void rejectsReusedState() throws Exception {
		pendingConnection(USER_ID, STATE, LocalDateTime.now().plusMinutes(5));
		when(client.exchangeCode("authorization-code")).thenReturn(token("access", "refresh", USER_SEQ_NO));
		when(paymentMethods.findByTenantUserIdAndPaymentMethodStatus(USER_ID, PaymentMethodStatus.ACTIVE))
			.thenReturn(List.of());
		service.completeAuthorization(USER_ID, "authorization-code", STATE);

		assertError(ErrorCode.OPEN_BANKING_AUTH_INVALID,
			() -> service.completeAuthorization(USER_ID, "another-code", STATE));

		verify(client, times(1)).exchangeCode(anyString());
	}

	@ParameterizedTest
	@MethodSource("invalidTokens")
	@DisplayName("필수 토큰·회원번호·출금 권한이 누락되거나 유효기간이 짧은 인증 응답은 저장하지 않는다")
	void rejectsIncompleteAuthorizationTokens(Token invalidToken) throws Exception {
		var connection = pendingConnection(USER_ID, STATE, LocalDateTime.now().plusMinutes(5));
		when(client.exchangeCode("authorization-code")).thenReturn(invalidToken);

		assertError(ErrorCode.OPEN_BANKING_UNAVAILABLE,
			() -> service.completeAuthorization(USER_ID, "authorization-code", STATE));

		assertNull(connection.getAccessTokenEncrypted());
		assertNull(connection.getRefreshTokenEncrypted());
		verifyNoInteractions(paymentMethods);
	}

	@Test
	@DisplayName("클라이언트가 보낸 식별자가 은행의 회원 계좌 목록에 없으면 등록하지 않는다")
	void rejectsAccountOutsideBankReturnedList() {
		connectedUser(false);
		when(client.accounts("access-secret", USER_SEQ_NO)).thenReturn(List.of(account(true, "임차인")));

		assertError(ErrorCode.PAYMENT_ACCOUNT_REQUIRED,
			() -> service.registerAccount(USER_ID, "199999999900000000000999"));

		verifyNoInteractions(paymentMethods);
	}

	@ParameterizedTest
	@MethodSource("unauthorizedAccounts")
	@DisplayName("출금 미동의 계좌와 회원 이름이 다른 계좌는 등록하지 않는다")
	void rejectsAccountWithoutConsentOrOwnership(Account bankAccount) {
		connectedUser(false);
		when(client.accounts("access-secret", USER_SEQ_NO)).thenReturn(List.of(bankAccount));

		assertError(ErrorCode.PAYMENT_ACCOUNT_REQUIRED,
			() -> service.registerAccount(USER_ID, FINTECH_USE_NUM));

		verifyNoInteractions(paymentMethods);
	}

	@Test
	@DisplayName("회원 소유·출금 동의 계좌만 은행이 반환한 정보로 등록하고 이전 계좌를 교체한다")
	void registersOnlyVerifiedBankAccount() {
		connectedUser(false);
		when(client.accounts("access-secret", USER_SEQ_NO)).thenReturn(List.of(account(true, "임차인")));
		var previous = PaymentMethod.builder().tenant(user).fintechUseNum("199999999900000000000002").build();
		when(paymentMethods.findByTenantUserIdAndPaymentMethodStatus(USER_ID, PaymentMethodStatus.ACTIVE))
			.thenReturn(List.of(previous));
		when(paymentMethods.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentMethod registered = service.registerAccount(USER_ID, FINTECH_USE_NUM);

		assertSame(user, registered.getTenant());
		assertEquals(FINTECH_USE_NUM, registered.getFintechUseNum());
		assertEquals("004", registered.getBankCode());
		assertEquals("123-****-4567", registered.getAccountNumberMasked());
		assertEquals(PaymentMethodType.ACCOUNT, registered.getMethodType());
		assertEquals(PaymentMethodStatus.ACTIVE, registered.getPaymentMethodStatus());
		assertNotNull(registered.getWithdrawalConsentedAt());
		assertEquals(PaymentMethodStatus.REPLACED, previous.getPaymentMethodStatus());
	}

	@Test
	@DisplayName("유효한 토큰은 복호화해서 사용하고 불필요하게 갱신하지 않는다")
	void reusesUnexpiredAccessToken() {
		connected(false);

		assertEquals("access-secret", service.accessToken(USER_ID));

		verifyNoInteractions(client);
	}

	@Test
	@DisplayName("만료된 토큰은 암호화하여 갱신하고 진행 중인 계좌 재인증 state를 유지한다")
	void refreshesExpiredTokenAndEncryptsReplacement() throws Exception {
		var connection = connected(true);
		connection.beginAuthorization(hash(STATE));
		LocalDateTime stateExpiresAt = connection.getStateExpiresAt();
		String previousEncrypted = connection.getAccessTokenEncrypted();
		when(client.refreshToken("refresh-secret")).thenReturn(token("new-access", "new-refresh", USER_SEQ_NO));

		assertEquals("new-access", service.accessToken(USER_ID));

		assertNotEquals(previousEncrypted, connection.getAccessTokenEncrypted());
		assertEquals("new-access", cipher.decrypt(connection.getAccessTokenEncrypted()));
		assertEquals("new-refresh", cipher.decrypt(connection.getRefreshTokenEncrypted()));
		assertEquals(hash(STATE), connection.getStateHash());
		assertEquals(stateExpiresAt, connection.getStateExpiresAt());
		verify(client).refreshToken("refresh-secret");
	}

	@Test
	@DisplayName("갱신 응답의 회원번호가 다르면 기존 인증을 새 회원의 토큰으로 덮어쓰지 않는다")
	void rejectsRefreshForDifferentBankUser() {
		var connection = connected(true);
		String previousEncrypted = connection.getAccessTokenEncrypted();
		when(client.refreshToken("refresh-secret")).thenReturn(token("other-access", "other-refresh", "9999999999"));

		assertError(ErrorCode.OPEN_BANKING_UNAVAILABLE, () -> service.accessToken(USER_ID));

		assertEquals(USER_SEQ_NO, connection.getUserSeqNo());
		assertEquals(previousEncrypted, connection.getAccessTokenEncrypted());
		verify(paymentMethods, never()).save(any());
	}

	@Test
	@DisplayName("연결된 오픈뱅킹 인증이 없으면 은행 API를 호출하지 않는다")
	void rejectsMissingConnection() {
		when(connections.findByUserUserId(USER_ID)).thenReturn(Optional.empty());

		assertError(ErrorCode.OPEN_BANKING_NOT_CONNECTED, () -> service.accounts(USER_ID));

		verifyNoInteractions(client);
	}

	private OpenBankingConnection pendingConnection(Long userId, String state, LocalDateTime expiresAt)
			throws Exception {
		var connection = OpenBankingConnection.builder().user(User.builder().userId(userId).name("임차인").build()).stateHash(hash(state))
			.stateExpiresAt(expiresAt).build();
		when(connections.findByUserUserId(userId)).thenReturn(Optional.of(connection));
		return connection;
	}

	private OpenBankingConnection connected(boolean isExpired) {
		var connection = OpenBankingConnection.builder().user(user).userSeqNo(USER_SEQ_NO)
			.accessTokenEncrypted(cipher.encrypt("access-secret"))
			.refreshTokenEncrypted(cipher.encrypt("refresh-secret"))
			.tokenExpiresAt(isExpired ? LocalDateTime.now().minusSeconds(1) : LocalDateTime.now().plusHours(1))
			.build();
		when(connections.findByUserUserId(USER_ID)).thenReturn(Optional.of(connection));
		return connection;
	}

	private void connectedUser(boolean isExpired) {
		when(users.findLockedById(USER_ID)).thenReturn(Optional.of(user));
		connected(isExpired);
	}

	private static Account account(boolean hasConsent, String holderName) {
		return new Account(FINTECH_USE_NUM, "004", "123-****-4567", holderName, hasConsent);
	}

	private static Token token(String accessToken, String refreshToken, String userSeqNo) {
		return new Token(accessToken, refreshToken, userSeqNo, "login inquiry transfer", 3600);
	}

	private static Stream<Token> invalidTokens() {
		return Stream.of(
			token(null, "refresh", USER_SEQ_NO),
			token("", "refresh", USER_SEQ_NO),
			token("access", "", USER_SEQ_NO),
			token("access", "refresh", ""),
			new Token("access", "refresh", USER_SEQ_NO, "login inquiry", 3600),
			new Token("access", "refresh", USER_SEQ_NO, "login inquiry transfer", 60));
	}

	private static Stream<Account> unauthorizedAccounts() {
		return Stream.of(account(false, "임차인"), account(true, "다른예금주"));
	}

	private static String hash(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private static void assertError(ErrorCode expected, Runnable action) {
		assertEquals(expected, assertThrows(OpenBankingException.class, action::run).getErrorCode());
	}
}
