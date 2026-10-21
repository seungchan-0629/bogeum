package com.bogeum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 백엔드의 실행 진입점. com.bogeum 아래의 컨트롤러·서비스·설정을 스캔해 Spring 빈으로 등록한다.
 */
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
