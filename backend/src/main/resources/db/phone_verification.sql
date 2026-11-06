-- 기존 DB에 추가하는 휴대폰 인증 테이블. 개발 환경에서는 JPA update로도 생성된다.
CREATE TABLE IF NOT EXISTS phone_verification (
	phone_verification_id BIGINT NOT NULL AUTO_INCREMENT,
	phone VARCHAR(20) NOT NULL,
	verification_id VARCHAR(36),
	code_hash VARCHAR(100),
	sent_at TIMESTAMP(6) NULL,
	expires_at TIMESTAMP(6) NULL,
	failed_attempts INT NOT NULL,
	verified_at TIMESTAMP(6) NULL,
	verification_token VARCHAR(36),
	token_expires_at TIMESTAMP(6) NULL,
	consumed_at TIMESTAMP(6) NULL,
	send_window_started_at TIMESTAMP(6) NULL,
	send_count INT NOT NULL,
	PRIMARY KEY (phone_verification_id),
	UNIQUE KEY uk_phone_verification_phone (phone),
	UNIQUE KEY uk_phone_verification_id (verification_id),
	UNIQUE KEY uk_phone_verification_token (verification_token)
);
