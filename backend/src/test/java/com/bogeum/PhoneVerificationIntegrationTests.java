package com.bogeum;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.config.SolapiProperties;
import com.bogeum.domain.user.PhoneVerificationRepository;
import com.bogeum.domain.user.UserException;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.PhoneVerification;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** SOLAPI만 대체하고 실제 인증 API·트랜잭션·H2 저장을 검증한다. 실제 문자는 발송하지 않는다. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-phone-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
	"spring.sql.init.mode=never", "auth.cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhoneVerificationIntegrationTests {

	private static final String PHONE = "01012345678";
	private static final String REQUEST_PATH = "/auth/identity-verification";
	private static final String CONFIRM_PATH = REQUEST_PATH + "/confirm";
	@Autowired private MockMvc mvc;
	@Autowired private JsonMapper mapper;
	@Autowired private UserService service;
	@Autowired private UserRepository users;
	@Autowired private PhoneVerificationRepository verifications;
	@Autowired private PasswordEncoder encoder;
	@MockitoBean private DefaultMessageService sms;
	@MockitoBean private SolapiProperties properties;

	@BeforeEach
	void prepare() throws Exception {
		verifications.deleteAll();
		when(properties.isConfigured()).thenReturn(true);
		when(properties.senderNumber()).thenReturn("01099999999");
		when(sms.send(any(Message.class))).thenReturn(new MultipleDetailMessageSentResponse());
	}

	@Test
	@DisplayName("발송은 6자리 번호를 문자에만 포함하고 해시와 3분 만료 시각을 저장한다")
	void sendsCodeWithoutExposingIt() throws Exception {
		SentCode sent = request("010-1234-5678");
		PhoneVerification saved = saved();
		assertThat(sent.code()).matches("[0-9]{6}");
		assertThat(saved.getPhone()).isEqualTo(PHONE);
		assertThat(saved.getCodeHash()).isNotEqualTo(sent.code());
		assertThat(encoder.matches(sent.code(), saved.getCodeHash())).isTrue();
		assertThat(saved.getExpiresAt()).isEqualTo(saved.getSentAt().plusSeconds(180));
		assertThat(sent.response().getResponse().getContentAsString()).doesNotContain("codeHash", "verificationToken", "\"code\"");
	}

	@Test
	@DisplayName("인증·가입 후 인증 시각을 저장하고 같은 가입 토큰은 재사용할 수 없다")
	void verifiesAndConsumesToken() throws Exception {
		SentCode sent = request(PHONE);
		String token = confirm(sent);
		String email = UUID.randomUUID() + "@example.com";
		mvc.perform(csrfPost("/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(signupBody(email, PHONE, token))).andExpect(status().isCreated());
		assertThat(users.findByEmail(email).orElseThrow().getIdentityVerifiedAt()).isNotNull();
		assertThat(saved().getConsumedAt()).isNotNull();
		mvc.perform(csrfPost("/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(signupBody(UUID.randomUUID() + "@example.com", PHONE, token)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
		mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody(sent.verificationId(), sent.code())))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_INVALID"));
	}

	@Test
	@DisplayName("다른 전화번호·위조 토큰·만료된 가입 토큰은 거절한다")
	void rejectsInvalidSignupProof() throws Exception {
		String token = confirm(request(PHONE));
		for (String[] input : new String[][] {{"01087654321", token}, {PHONE, "1111"}}) {
			mvc.perform(csrfPost("/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
				.content(signupBody(UUID.randomUUID() + "@example.com", input[0], input[1])))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
		}
		PhoneVerification verification = saved();
		ReflectionTestUtils.setField(verification, "tokenExpiresAt", Instant.now().minusSeconds(1));
		verifications.saveAndFlush(verification);
		mvc.perform(csrfPost("/auth/signup/user").contentType(MediaType.APPLICATION_JSON)
			.content(signupBody(UUID.randomUUID() + "@example.com", PHONE, token)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));
	}

	@Test
	@DisplayName("오입력 횟수는 실패 응답에도 유지하고 5회 후에는 정답도 거절한다")
	void persistsFailedAttempts() throws Exception {
		SentCode sent = request(PHONE);
		String wrongCode = sent.code().equals("000000") ? "000001" : "000000";
		for (int attempt = 1; attempt <= 5; attempt++) {
			mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
				.content(confirmBody(sent.verificationId(), wrongCode)))
				.andExpect(status().is(attempt == 5 ? 429 : 400))
				.andExpect(jsonPath("$.code").value(attempt == 5 ? "PHONE_ATTEMPTS_EXCEEDED" : "PHONE_CODE_MISMATCH"));
			assertThat(saved().getFailedAttempts()).isEqualTo(attempt);
		}
		mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody(sent.verificationId(), sent.code())))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	@DisplayName("서버 만료 시각을 지난 인증번호는 정답이어도 거절한다")
	void rejectsExpiredCode() throws Exception {
		SentCode sent = request(PHONE);
		PhoneVerification verification = saved();
		ReflectionTestUtils.setField(verification, "expiresAt", Instant.now().minusSeconds(1));
		verifications.saveAndFlush(verification);
		mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody(sent.verificationId(), sent.code())))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_EXPIRED"));
	}

	@Test
	@DisplayName("60초 재발송 제한과 1시간 5회 제한을 적용하고 이전 인증은 무효화한다")
	void limitsResendsAndInvalidatesPreviousRequest() throws Exception {
		SentCode first = request(PHONE);
		String previousToken = confirm(first);
		mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("phone", PHONE))))
			.andExpect(status().isTooManyRequests());
		for (int count = 2; count <= 5; count++) {
			allowResend();
			SentCode next = request(PHONE);
			assertThat(next.verificationId()).isNotEqualTo(first.verificationId());
		}
		assertThat(saved().getVerificationToken()).isNull();
		assertThat(saved().getSendCount()).isEqualTo(5);
		mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody(first.verificationId(), first.code())))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_INVALID"));
		assertThatThrownBy(() -> service.signup(UUID.randomUUID() + "@example.com", "password", "가입자", PHONE, true, true, previousToken))
			.isInstanceOfSatisfying(UserException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED));
		allowResend();
		mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("phone", PHONE))))
			.andExpect(status().isTooManyRequests());
		verify(sms, times(5)).send(any(Message.class));
		PhoneVerification verification = saved();
		ReflectionTestUtils.setField(verification, "sendWindowStartedAt", Instant.now().minusSeconds(3601));
		verifications.saveAndFlush(verification);
		request(PHONE);
		assertThat(saved().getSendCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("발송 실패 시 저장과 제한을 롤백하여 즉시 다시 요청할 수 있다")
	void rollsBackFailedSend() throws Exception {
		when(sms.send(any(Message.class))).thenThrow(new IllegalStateException("provider unavailable"));
		mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("phone", PHONE))))
			.andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("SMS_SEND_FAILED"));
		assertThat(verifications.count()).isZero();
		when(sms.send(any(Message.class))).thenReturn(new MultipleDetailMessageSentResponse());
		request(PHONE);
		assertThat(saved().getSendCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("API 키 미설정·잘못된 전화번호·CSRF 누락은 실제 발송 전에 거절한다")
	void rejectsRequestsBeforeSending() throws Exception {
		mvc.perform(post(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"01012345678\"}"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(post(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"abc\"}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHONE"));
		when(properties.isConfigured()).thenReturn(false);
		mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"01012345678\"}"))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SMS_NOT_CONFIGURED"));
		mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"verificationId\":\"unknown\",\"code\":\"123\"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(sms);
	}

	@Test
	@DisplayName("가입 저장이 실패하면 인증 토큰은 소모되지 않아 다시 가입할 수 있다")
	void failedSignupPreservesToken() throws Exception {
		String token = confirm(request(PHONE));
		String email = UUID.randomUUID() + "@example.com";
		assertThatThrownBy(() -> service.signup(email, "password", "가입자", PHONE, false, true, token))
			.isInstanceOf(UserException.class);
		assertThat(saved().getConsumedAt()).isNull();
		service.signup(email, "password", "가입자", PHONE, true, true, token);
		assertThat(saved().getConsumedAt()).isNotNull();
	}

	@Test
	@DisplayName("동시에 같은 토큰으로 가입해도 한 요청만 성공한다")
	void consumesTokenAtomically() throws Exception {
		String token = confirm(request(PHONE));
		var executor = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Boolean> signup = () -> {
				start.await(5, TimeUnit.SECONDS);
				try {
					service.signup(UUID.randomUUID() + "@example.com", "password", "가입자", PHONE, true, true, token);
					return true;
				} catch (UserException exception) {
					assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED);
					return false;
				}
			};
			var first = executor.submit(signup);
			var second = executor.submit(signup);
			start.countDown();
			assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
		} finally {
			executor.shutdownNow();
		}
	}

	private SentCode request(String phone) throws Exception {
		MvcResult result = mvc.perform(csrfPost(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(mapper.writeValueAsString(Map.of("phone", phone))))
			.andExpect(status().isOk()).andExpect(jsonPath("$.verificationId").isString())
			.andExpect(jsonPath("$.expiresAt").isString()).andReturn();
		ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
		verify(sms, atLeastOnce()).send(messages.capture());
		Message message = messages.getValue();
		assertThat(message.getTo()).isEqualTo(PHONE);
		assertThat(message.getFrom()).isEqualTo("01099999999");
		var matcher = Pattern.compile("\\[([0-9]{6})\\]").matcher(message.getText());
		assertThat(matcher.find()).isTrue();
		return new SentCode(mapper.readTree(result.getResponse().getContentAsString()).get("verificationId").asText(), matcher.group(1), result);
	}

	private String confirm(SentCode sent) throws Exception {
		MvcResult result = mvc.perform(csrfPost(CONFIRM_PATH).contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody(sent.verificationId(), sent.code())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true))
			.andExpect(jsonPath("$.verificationToken").isString()).andReturn();
		return mapper.readTree(result.getResponse().getContentAsString()).get("verificationToken").asText();
	}

	private PhoneVerification saved() {
		return verifications.findAll().get(0);
	}

	private void allowResend() {
		PhoneVerification verification = saved();
		ReflectionTestUtils.setField(verification, "sentAt", Instant.now().minusSeconds(61));
		verifications.saveAndFlush(verification);
	}

	private String signupBody(String email, String phone, String token) {
		return mapper.writeValueAsString(Map.of("email", email, "password", "password", "name", "가입자", "phone", phone,
			"verificationToken", token, "agreements", Map.of("privacyCollect", true, "thirdPartyProvide", true)));
	}

	private String confirmBody(String id, String code) {
		return mapper.writeValueAsString(Map.of("verificationId", id, "code", code));
	}

	private MockHttpServletRequestBuilder csrfPost(String path) throws Exception {
		MvcResult result = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
		var data = mapper.readTree(result.getResponse().getContentAsString());
		return post(path).cookie(result.getResponse().getCookie("BOGEUM_CSRF"))
			.header(data.get("headerName").asText(), data.get("token").asText());
	}

	private record SentCode(String verificationId, String code, MvcResult response) { }
}
