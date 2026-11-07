-- dev는 ddl-auto=update로 생성한다. validate 환경은 배포 전에 이 스크립트를 적용한다.
CREATE TABLE IF NOT EXISTS institution_token (
    institution_token_id BIGINT NOT NULL PRIMARY KEY,
    access_token VARCHAR(2048) NOT NULL,
    client_use_code VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
