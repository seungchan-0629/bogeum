package com.bogeum.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 서버의 HTTP 응답 여부를 확인하는 API. DB 등 외부 의존성의 상태를 검사하지는 않는다.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}
