package com.bogeum.security.filter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.bogeum.dto.LoginRequestDTO;
import com.bogeum.exception.ErrorCode;
import com.bogeum.exception.ErrorResponseDTO;
import com.bogeum.security.EmailNormalizer;
import com.bogeum.security.PasswordPolicy;
import com.bogeum.security.handler.JsonResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /api/auth/login의 JSON 요청을 컨트롤러 대신 처리한다.
 * 본문 해석 → 입력 검증 → 인증 관리자 호출 순서로 진행하며 결과 처리는 보안 핸들러에 맡긴다.
 */
public class JsonLoginFilter extends AbstractAuthenticationProcessingFilter {

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final JsonResponseWriter responseWriter;

	public JsonLoginFilter(AuthenticationManager authenticationManager, ObjectMapper objectMapper,
			Validator validator, JsonResponseWriter responseWriter) {
		super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
			authenticationManager);
		this.objectMapper = objectMapper;
		this.validator = validator;
		this.responseWriter = responseWriter;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException, IOException {
		// 입력 오류 응답을 작성한 경우 인증 성공·실패 핸들러가 덮어쓰지 않도록 종료한다.
		if (!isJson(request.getContentType())) {
			responseWriter.writeError(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
			return null;
		}

		// 컨트롤러의 요청 바인딩을 거치지 않으므로 본문 파싱과 입력 검증을 필터에서 직접 수행한다.
		LoginRequestDTO login;
		try {
			login = objectMapper.readValue(request.getInputStream(), LoginRequestDTO.class);
		} catch (JacksonException exception) {
			responseWriter.writeError(response, ErrorCode.INVALID_JSON);
			return null;
		}
		if (login == null) {
			responseWriter.writeError(response, ErrorCode.INVALID_JSON);
			return null;
		}

		// 가입 때와 같은 이메일 형식을 적용하고, 문자 수 검증에 더해 BCrypt의 바이트 제한도 확인한다.
		login.setEmail(EmailNormalizer.normalize(login.getEmail()));
		Map<String, String> errors = new LinkedHashMap<>();
		validator.validate(login).forEach(violation ->
			errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
		if (PasswordPolicy.exceedsByteLimit(login.getPassword())) {
			errors.put("password", "비밀번호는 72바이트 이하여야 합니다.");
		}
		if (!errors.isEmpty()) {
			responseWriter.write(response, ErrorCode.VALIDATION_FAILED.getStatus(),
				new ErrorResponseDTO(ErrorCode.VALIDATION_FAILED, errors));
			return null;
		}

		// 아직 인증되지 않은 자격 증명을 전달한다. 실제 계정 조회와 비밀번호 비교는 인증 관리자의 역할이다.
		UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
			.unauthenticated(login.getEmail(), login.getPassword());
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		// 반환되면 부모 필터가 세션 전략·인증 정보 저장·성공 핸들러를 실행하고, 인증 예외는 실패 핸들러로 보낸다.
		return getAuthenticationManager().authenticate(authentication);
	}

	private boolean isJson(String contentType) {
		try {
			return contentType != null
				&& MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
