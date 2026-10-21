package com.bogeum.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.bogeum.exception.ErrorCode;
import com.bogeum.exception.ErrorResponseDTO;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 필터와 핸들러에서 HTTP 상태·UTF-8 JSON 본문을 직접 작성하는 공통 도구.
 */
@Component
@RequiredArgsConstructor
public class JsonResponseWriter {

	private final ObjectMapper objectMapper;

	public void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
		write(response, code.getStatus(), new ErrorResponseDTO(code));
	}

	public void write(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// 로그인 결과와 인증 오류 응답이 브라우저·중간 캐시에 저장되지 않도록 한다.
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
