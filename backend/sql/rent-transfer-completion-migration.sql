-- 월세 청구·이체 이력 보완용 MySQL 1회 전환안. 이 작업에서는 실행하지 않았다.
-- 먼저 openbanking-migration.sql 반영 여부와 SHOW CREATE TABLE 결과를 확인한다.
-- 스케줄·구버전 앱을 중지하고 백업한 뒤 필요한 문장만 적용한다.
-- 새 빈 DB/이미 Hibernate update한 DB에 그대로 재실행하지 않는다. DDL은 암묵적 COMMIT이다.
-- 과거 거래의 계좌 스냅샷/은행 검증시각을 추측해 채우거나 상태를 초기화하지 않는다.

ALTER TABLE settlement_account ADD COLUMN verified_at DATETIME(6) NULL;

ALTER TABLE payment_attempt
    ADD COLUMN fintech_use_num_snapshot VARCHAR(24) NULL,
    ADD COLUMN tenant_name_snapshot VARCHAR(50) NULL,
    ADD COLUMN tenant_user_id_snapshot BIGINT NULL,
    ADD COLUMN institution_account_reference VARCHAR(64) NULL;

CREATE TABLE settlement_attempt (
    settlement_attempt_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    payment_attempt_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    bank_tran_id VARCHAR(20) NOT NULL,
    institution_account_reference VARCHAR(64) NOT NULL,
    amount DECIMAL(15,0) NOT NULL,
    tenant_fintech_use_num_snapshot VARCHAR(24) NOT NULL,
    tenant_name_snapshot VARCHAR(50) NOT NULL,
    tenant_user_id_snapshot BIGINT NOT NULL,
    bank_code_snapshot VARCHAR(3) NOT NULL,
    account_number_encrypted_snapshot VARCHAR(255) NOT NULL,
    account_number_masked_snapshot VARCHAR(30) NOT NULL,
    holder_name_snapshot VARCHAR(50) NOT NULL,
    attempt_status VARCHAR(20) NOT NULL,
    response_code VARCHAR(50) NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (settlement_attempt_id),
    CONSTRAINT uk_settlement_attempt_round UNIQUE (settlement_id, attempt_no),
    CONSTRAINT uk_settlement_attempt_bank_tran_id UNIQUE (bank_tran_id),
    CONSTRAINT fk_settlement_attempt_summary FOREIGN KEY (settlement_id) REFERENCES settlement (settlement_id),
    CONSTRAINT fk_settlement_attempt_collection FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempt (payment_attempt_id)
);

CREATE TABLE collection_resolution (
    collection_resolution_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_attempt_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    evidence_reference VARCHAR(200) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    succeeded BOOLEAN NOT NULL,
    resolved_at DATETIME(6) NOT NULL,
    PRIMARY KEY (collection_resolution_id),
    CONSTRAINT uk_collection_resolution_attempt UNIQUE (payment_attempt_id),
    CONSTRAINT fk_collection_resolution_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempt (payment_attempt_id)
);

CREATE TABLE settlement_resolution (
    settlement_resolution_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_attempt_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    evidence_reference VARCHAR(500) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    succeeded BOOLEAN NOT NULL,
    resolved_at DATETIME(6) NOT NULL,
    PRIMARY KEY (settlement_resolution_id),
    CONSTRAINT uk_settlement_resolution_attempt UNIQUE (settlement_attempt_id),
    CONSTRAINT fk_settlement_resolution_attempt FOREIGN KEY (settlement_attempt_id) REFERENCES settlement_attempt (settlement_attempt_id)
);

-- 생성·스케줄 조회 인덱스. 같은 기능의 기존 인덱스가 있으면 생략한다.
CREATE INDEX ix_rent_payment_due ON rent_payment (rent_payment_status, due_at);
CREATE INDEX ix_settlement_pending ON settlement (settlement_status, settlement_id);

-- 전환 후 필수 확인:
-- 1) 과거 settlement_account.verified_at은 NULL -> 계좌 API를 통해 은행 검증 후 재등록한다.
-- 2) 과거 payment_attempt snapshot은 NULL -> 자동 지급/수동확정 대상으로 추정해 사용하지 않는다.
-- 3) 과거 UNKNOWN/PROCESSING 거래는 별도 은행 대사 후 전환 절차를 정한다.
-- 4) 기존 상태 CHECK/ENUM은 RentPaymentStatus/SettlementStatus/PaymentAttemptStatus와 맞춘다.
-- 5) 구버전 앱과 새 앱을 동시에 실행하지 않는다.
