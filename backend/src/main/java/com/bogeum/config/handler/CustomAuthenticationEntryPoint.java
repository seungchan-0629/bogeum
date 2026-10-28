package com.bogeum.config.handler;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.common.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 인증 누락 및 JWT 검증 실패를 공통 오류 응답으로 반환한다. */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		writeError(response, ErrorCode.AUTH_REQUIRED);
	}

	public void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(jsonMapper.writeValueAsString(new ErrorResponse(code)));
	}
}
