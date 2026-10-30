-- 오픈뱅킹 전환 검토용 MySQL SQL. 이 저장소에서는 실행하지 않았다.
-- 기존 PG/CMS 스키마를 대상으로 한 1회 전환안이며 자동 마이그레이션이 아니다.
-- 백업 후 기존 PG/CMS 작업과 오픈뱅킹 스케줄을 모두 중지하고 적용 여부를 검토한다.
-- DDL은 암묵적으로 커밋된다. 실패 시 전체 ROLLBACK으로 복구할 수 없다.
-- 새 빈 DB 또는 Hibernate update가 이미 실행된 DB에 그대로 실행하지 않는다.
-- 적용 전 SHOW CREATE TABLE로 컬럼·인덱스·CHECK 제약·FK를 실제 스키마와 비교한다.
-- 과거 PG 미확정/미정산 거래는 원거래를 먼저 대사하며, 아래 SQL로 재전송 가능 상태로 바꾸지 않는다.

-- 1. 과거 카드 타입은 새 Java enum이 읽을 수 있도록 정리한다.
-- 기존 행·결제 시도의 FK·과거 출금동의 시각은 보존하되 모든 수단을 비활성화한다.
-- CMS 동의를 오픈뱅킹 동의로 간주하지 않는다. 사용자는 OAuth로 출금 계좌를 재등록한다.
UPDATE payment_method
SET method_type = 'ACCOUNT',
    payment_method_status = 'REPLACED';

ALTER TABLE payment_method
    ADD COLUMN fintech_use_num VARCHAR(24) NULL,
    DROP COLUMN billing_key,
    DROP COLUMN cms_registration_id;

-- 2. 은행거래고유번호는 새 요청을 보내기 전에만 새로 발급·저장한다.
-- 과거 pg_transaction_id는 PG 대사를 위해 DB에 보존한다. bank_tran_id로 복사하지 않는다.
-- 새 지급 로직은 오픈뱅킹 성공 저널의 은행거래고유번호·핀테크이용번호가 없는 과거 PG 지급을 차단한다.
-- MySQL UNIQUE 인덱스는 여러 과거 행의 NULL을 허용한다.
ALTER TABLE payment_attempt
    ADD COLUMN bank_tran_id VARCHAR(20) NULL,
    ADD CONSTRAINT uk_payment_attempt_bank_tran_id UNIQUE (bank_tran_id);

ALTER TABLE settlement
    ADD COLUMN bank_tran_id VARCHAR(20) NULL,
    ADD CONSTRAINT uk_settlement_bank_tran_id UNIQUE (bank_tran_id);

-- 3. 회원별 오픈뱅킹 연결. 인증 시작 직후에는 토큰과 user_seq_no가 NULL이다.
-- LocalDateTime 필드는 엔티티와 같이 nullable이며 TIMESTAMP(6)으로 생성한다.
-- 이 엔티티에는 created_at/updated_at 필드가 없다.
CREATE TABLE open_banking_connection (
    open_banking_connection_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    state_hash VARCHAR(64) NULL,
    state_expires_at TIMESTAMP(6) NULL,
    user_seq_no VARCHAR(20) NULL,
    access_token_encrypted VARCHAR(2048) NULL,
    refresh_token_encrypted VARCHAR(2048) NULL,
    token_expires_at TIMESTAMP(6) NULL,
    PRIMARY KEY (open_banking_connection_id),
    CONSTRAINT uk_open_banking_connection_user_id UNIQUE (user_id),
    CONSTRAINT fk_open_banking_connection_user
        FOREIGN KEY (user_id) REFERENCES `user` (user_id)
);

-- 기존 상태 컬럼은 엔티티가 VARCHAR(20)으로 선언하므로 PROCESSING/UNKNOWN을 저장할 수 있다.
-- 실제 DB가 ENUM/CHECK로 값들을 제한하고 있다면 적용 전 해당 제약을 새 enum과 맞춰 수정해야 한다.
-- 기존 정산 계좌 암호문은 현재 AES-256-GCM 형식·키와 호환되는지 따로 확인한다.
-- 호환되지 않는 암호문은 이 SQL로 덮어쓰지 말고 재등록 또는 검증된 별도 전환을 수행한다.
