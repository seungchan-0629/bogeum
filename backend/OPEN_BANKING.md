# 오픈뱅킹 월세 수납·지급

임차인의 출금 동의 계좌에서 회사 약정 수납 계좌로 월세를 받은 뒤, 수납이 확인된 금액을 임대인의 정산 계좌로 지급한다. 기존 `RentPayment`, `PaymentMethod`, `PaymentAttempt`, `Settlement`, `SettlementAccount`를 사용하고 지급 시도별 `SettlementAttempt`와 수납·지급 확정 감사 기록을 추가한다. 카드 PG·`billingKey`·CMS 등록번호를 사용하는 결제 방식은 폐기한다.

DB·서버를 준비한 뒤 실행할 상세 순서, JWT·CSRF를 포함한 API 예제, DB 확인 SQL은 [연동 테스트 가이드](OPEN_BANKING_TEST_GUIDE.md)를 따른다. 이번 작업에서는 애플리케이션 서버·MySQL 서버·실제 은행 이체를 실행하지 않았다.

## 코드 구조

다른 도메인과 동일하게 Controller·Service·Repository·Exception은 도메인 루트에, 엔티티·DTO는 `model`에 둔다.

- `openbanking`: `OpenBankingController`, `OpenBankingService`, `OpenBankingRepository`, `OpenBankingClient`, `OpenBankingException`, `model`
- `rent`: 기존 `RentService`가 월별 청구 생성, 수납 준비·은행 호출·결과 반영, 증빙 기반 수납 확정과 이체 상태 조회를 담당한다. 관리자 청구·이체 API는 `AdminRentController`에 모으고 요청·응답은 `model/RentDto`에 둔다. 결제수단·결제시도·수납 확정 기록 저장소는 `RentRepository` 내부 인터페이스로 모은다.
- `settlement`: 기존 `SettlementService`가 계좌 은행 검증, 지급 준비·은행 호출·결과 반영과 증빙 기반 지급 확정을 담당한다. 정산 계좌·지급 시도·지급 확정 기록 저장소는 `SettlementRepository` 내부 인터페이스로 모은다.
- `config`: `OpenBankingConfig`에 HTTP 클라이언트 설정과 `Properties`를 함께 둔다. 환경 변수 이름과 기본값은 유지한다.

`domain/user`처럼 도메인의 서비스·저장소를 중심으로 관련 기능을 모은다. 기존에 분리했던 청구 생성·수납 확정 서비스와 관리자 컨트롤러는 기존 파일에 통합했다. 개별 이체 이력·감사 기록은 별도 DB 엔티티이므로 `model`의 파일로 유지한다. 계약 조회·잠금은 계약 도메인의 `ContractRepository`가 담당하고, 은행 HTTP 통신과 정기 실행은 각각 `OpenBankingClient`와 `RentScheduler`가 담당한다.

```text
rent/
├─ model/                  # RentPayment, PaymentMethod, PaymentAttempt, CollectionResolution, RentDto
├─ AdminRentController
├─ RentController
├─ RentRepository          # PaymentMethodRepository, PaymentAttemptRepository, CollectionResolutionRepository 포함
├─ RentScheduler
└─ RentService

settlement/
├─ model/                  # Settlement, SettlementAccount, SettlementAttempt, SettlementResolution, SettlementDto
├─ SettlementController
├─ SettlementRepository    # SettlementAccountRepository, SettlementAttemptRepository, SettlementResolutionRepository 포함
└─ SettlementService
```

수납·지급은 은행 호출 동안 상위 트랜잭션을 중단하고, 호출 전 준비와 호출 후 결과 반영을 각각 `TransactionTemplate(REQUIRES_NEW)`으로 커밋한다. 같은 서비스 안의 메서드 호출에는 `@Transactional` 프록시가 적용되지 않으므로 준비·완료 메서드의 애너테이션만으로 대체하지 않는다. 중첩 저장소는 기존 `considerNestedRepositories=true` 설정으로 등록된다.

## 실행 전 준비

- 금융결제원의 이용기관 승인·계약 및 회사 약정 계좌 등록이 필요하다. 오픈뱅킹은 회사의 은행 약정 계좌를 통해 이체하며, 별도의 회사 잔액 지갑을 생성하는 구현은 없다.
- 테스트베드에서 발급한 Client ID·Client Secret·이용기관코드와 테스트용 약정 계좌·응답정보를 준비한다. 운영 정보와 구분하여 설정한다.
- 금융결제원에 등록한 Callback URL과 `OPENBANKING_REDIRECT_URI`를 일치시킨다. 기본 주소는 `http://localhost:5173/mypage/payment-method`이다.
- 입금이체용 암호문구를 금융결제원 포털에 설정하고, 그 문구의 SHA-512 해시를 환경 변수로 공급한다.
- 기존 DB는 아래 마이그레이션 항목을 먼저 검토한다. 이 저장소의 SQL은 자동 실행되지 않는다.

오픈뱅킹 호출과 자동 스케줄은 각각 기본값이 `false`다. 필요한 설정과 DB 전환을 마친 뒤 두 설정을 별도로 활성화한다.

## 환경 변수

`backend`에서 실행할 때 `application.yml`이 `./.env`를 선택적으로 읽는다. 비밀값은 환경 변수 또는 로컬 `.env`에만 두고 Git에 올리지 않는다.

| 환경 변수 | 용도 | 기본값 |
| --- | --- | --- |
| `OPENBANKING_ENABLED` | 오픈뱅킹 HTTP 호출 허용 | `false` |
| `OPENBANKING_BASE_URL` | 금융결제원 API 서버 | `https://testapi.openbanking.or.kr` |
| `OPENBANKING_CLIENT_ID` | 이용기관 Client ID | 없음 |
| `OPENBANKING_CLIENT_SECRET` | 이용기관 Client Secret | 없음 |
| `OPENBANKING_REDIRECT_URI` | 프론트엔드 인증 결과 수신 주소 | `http://localhost:5173/mypage/payment-method` |
| `OPENBANKING_FINTECH_ORG_CODE` | 은행거래고유번호에 사용하는 10자리 이용기관코드 | 없음 |
| `OPENBANKING_CONTRACT_ACCOUNT_NUM` | 수납·지급에 사용하는 회사 약정 계좌번호 | 없음 |
| `OPENBANKING_DEPOSIT_PASS_PHRASE` | 입금이체 암호문구의 SHA-512 해시, 128자리 hex | 없음 |
| `OPENBANKING_ENCRYPTION_KEY` | 토큰·정산 계좌번호 암호화용 AES-256 키, 32바이트의 Base64 | 없음 |
| `OPENBANKING_TIMEOUT` | 연결·응답 제한시간 | `10s` |
| `OPENBANKING_SCHEDULING_ENABLED` | 기존 월세 회차 수납·보류 지급 스케줄 허용 | `false` |
| `OPENBANKING_SCHEDULE_DELAY_MS` | 스케줄 실행 종료 후 다음 실행까지 간격, 밀리초 | `60000` |

환경 변수는 모두 `OPENBANKING_` 접두사를 사용한다. 운영 API 서버는 `https://openapi.openbanking.or.kr`이며, 운영 계약과 접근 조건을 확인한 뒤 변경한다.

AES 키는 로컬 PowerShell에서 다음과 같이 생성할 수 있다. 생성한 키는 별도 비밀 저장소에 보관하고 모든 서버 인스턴스에서 동일하게 사용한다. 키를 잃거나 임의 교체하면 기존 토큰과 정산 계좌번호를 복호화할 수 없다.

```powershell
$openBankingKeyBytes = New-Object byte[] 32
$openBankingRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
  $openBankingRng.GetBytes($openBankingKeyBytes)
  [Convert]::ToBase64String($openBankingKeyBytes)
} finally {
  $openBankingRng.Dispose()
}
```

## 계좌 연결

1. 로그인한 사용자가 인증 URL 발급 API를 호출한다. 서버는 회원에게 귀속된 일회용 `state`의 SHA-256 해시를 저장한다. 유효기간은 10분이다.
2. 프론트는 반환된 금융결제원 인증 화면으로 이동한다. 사용자는 금융결제원 화면에서 본인인증·계좌등록·출금 동의를 수행한다. 요청 권한은 `login inquiry transfer`다.
3. 금융결제원은 프론트의 `/mypage/payment-method`로 `code`와 `state`를 돌려준다. 프론트는 주소에서 인증 값을 지운 뒤 기존 JWT·CSRF 인증을 사용해 서버의 callback API로 전달한다.
4. 서버가 회원·`state`·만료시간을 검증하고 인증 코드를 토큰으로 교환한다. 토큰은 AES-256-GCM 암호문으로 저장한다. 인증 완료 후 `state`를 제거한다.
5. 사용자가 연결된 계좌 중 출금 계좌를 선택한다. 서버는 금융결제원에서 다시 조회한 본인 계좌이며 `transfer_agree_yn`이 `Y`이고 예금주가 회원 이름과 일치하는지 확인한다. 기존 출금 계좌는 `REPLACED`로 바뀐다.

출금 계좌는 실제 계좌번호 대신 핀테크이용번호를 저장한다. OAuth 토큰이 만료되기 60초 전부터 refresh token으로 갱신한다. 다른 오픈뱅킹 사용자로 재인증하면 기존 출금 계좌를 다시 등록해야 한다.

임대인의 정산 계좌번호는 서버에서 암호화하고 API에는 마스킹된 번호만 반환한다. 등록 시 회원 이름과 입력한 예금주를 비교한 뒤 금융결제원 `POST /v2.0/inquiry/real_name`으로 계좌·예금주를 검증한다. `birthDate`는 `yyMMdd` 6자리로 받아 검증 요청에만 사용하며 DB·응답·로그에 저장하지 않는다. 성공 시 `verifiedAt`을 기록한다. 실제 지급 시에도 `name_check_option=on`으로 은행 예금주 검증을 요청한다. 임차인 출금은 검증된 임대인 계좌가 있을 때만 시작한다. 과거 미검증 계좌는 다시 등록해야 한다.

## 제공 API

아래 경로는 모두 로그인한 회원의 기존 JWT 인증을 사용한다. `POST`에는 기존 CSRF 토큰도 필요하다. `/api/v1/admin/**`는 `ADMIN` 권한이 필요하다.

| 메서드·경로 | 요청 | 응답·처리 |
| --- | --- | --- |
| `POST /api/v1/tenants/me/open-banking/authorization` | 본문 없음 | `authorizationUrl` |
| `POST /api/v1/tenants/me/open-banking/callback` | `code`, `state` | 인증 완료 시 `204` |
| `GET /api/v1/tenants/me/open-banking/accounts` | 없음 | `fintechUseNum`, `bankCode`, `accountNumberMasked`, `holderName`, `withdrawalAgreed` 목록 |
| `POST /api/v1/tenants/me/payment-account` | `fintechUseNum`: 24자리 숫자 | 등록한 출금 계좌의 표시용 정보 |
| `GET /api/v1/tenants/me/payment-account` | 없음 | 출금 계좌 정보, 미등록이면 `204` |
| `POST /api/v1/tenants/me/rent-payments/{rentPaymentId}/pay` | 본문 없음 | 본인 납부 회차의 수납 결과 |
| `GET /api/v1/tenants/me/rent-payments` | 없음 | 본인 납부 회차 목록, 납부 예정일 내림차순 |
| `POST /api/v1/landlords/me/settlement-account` | `bankCode`: 3자리 숫자, `accountNumber`: 4~16자리 숫자, `holderName`, `birthDate`: 6자리 숫자 | 은행 검증 후 마스킹된 정산 계좌와 `verifiedAt` |
| `GET /api/v1/landlords/me/settlement-account` | 없음 | 정산 계좌 정보, 미등록이면 `204` |
| `POST /api/v1/admin/contracts/{contractId}/rent-payments` | 본문 없음 | 기존 계약의 월세 회차 생성·목록, 이미 생성했으면 기존 회차 유지 |
| `GET /api/v1/admin/contracts/{contractId}/rent-payments` | 없음 | 계약 회차 목록, 회차 순 |
| `POST /api/v1/admin/rents/{rentPaymentId}/settlement` | 본문 없음 | `PENDING` 또는 증빙으로 최종 실패가 확정된 `FAILED` 지급 처리, `204` |
| `POST /api/v1/admin/rents/{rentPaymentId}/collection-resolution` | `bankTranId`, `succeeded`: boolean, `evidenceReference`: 1~200자 | 은행 증빙으로 현재 수납 원거래 확정, `204` |
| `POST /api/v1/admin/rents/{rentPaymentId}/settlement-resolution` | `bankTranId`, `succeeded`: boolean, `evidenceReference`: 1~200자 | 은행 증빙으로 현재 지급 원거래 확정, `204` |
| `GET /api/v1/admin/rents/{rentPaymentId}/transfer-status` | 없음 | 수납·지급 상태, 거래번호, 시도별 이력, 회차 미지급액 |

출금 계좌 응답은 `paymentMethodId`, `bankCode`, `accountNumberMasked`, `paymentMethodStatus`를 포함한다. 납부 응답은 `rentPaymentId`, `seq`, `amount`, `rentPaymentStatus`, `dueAt`, `paidAt`, `settlementStatus`, `settlementPaidAt`를 포함한다. 임차인 화면은 회사 수납 완료와 임대인 지급 완료를 구분한다. 목록 API의 페이지·커서 검색은 구현하지 않았다.

관리자 상태 응답은 `rentPaymentId`, `collectionStatus`, `settlementStatus`, `amount`, `outstandingAmount`, `collectionBankTranId`, `settlementBankTranId`, `collectionAttempts`, `settlementAttempts`다. `outstandingAmount`는 회사 수납이 확정되었으나 지급 완료되지 않은 회차 금액이며, 회사 계좌의 실시간 잔액은 아니다. 계좌 원문·암호문·OAuth 토큰은 응답하지 않는다.

## 계약별 월세 회차 생성

`RentService.generatePayments(contractId)` 또는 관리자 생성 API로 DB에 이미 있는 계약의 월별 회차를 만든다. `RentService.findContractPayments(contractId)`는 생성된 계약 회차를 조회한다. 계약 등록·수정·확정 API 전체는 아직 구현하지 않았다. 향후 계약 확정 흐름에서 회차 생성 메서드를 호출할 수 있다.

- 양의 정수 월세(최대 12자리), 납부일 1~31, 계약기간을 검증한다. 전세의 월세가 0이면 회차를 만들지 않는다.
- 계약 시작·종료 시각 양끝을 포함하는 범위 안에서 매월 납부일 오전 9시(`Asia/Seoul`)를 사용한다. 29~31일이 없는 달은 말일로 보정하며 일할 계산은 하지 않는다.
- 회차 생성 당시 `monthlyRent`를 `RentPayment.amount`에 복사한다. `(contract_id, seq)` 유일 제약과 계약 행 잠금으로 중복 생성을 막는다.
- 이미 회차가 하나라도 있으면 기존 회차 전체를 반환한다. 계약을 수정하고 재호출해도 기존 금액·기한·회차 ID를 바꾸거나 부분 일정을 재생성하지 않는다. 계약 변경에 따른 일정 조정은 별도 업무다.

## 수납·지급과 중복 방지

출금 금액·임차인·임대인은 DB의 납부 회차와 계약에서 가져온다. 클라이언트가 송금액이나 수취인을 지정하지 않는다. 납부일이 도래한 양의 정수 금액만 처리하며, 금융결제원 금액 필드의 12자리 한도를 검사한다.

1. 납부 회차를 잠그고 임차인 출금동의와 검증된 임대인 계좌를 확인한다. `PaymentAttempt`의 은행거래고유번호·금액·출금 식별 스냅샷과 `PROCESSING` 상태를 별도 트랜잭션으로 먼저 커밋한다.
2. 임차인의 `transfer` 토큰과 핀테크이용번호로 `POST /v2.0/transfer/withdraw/fin_num`을 호출하여 회사 약정 계좌로 수납한다.
3. API 응답과 은행 응답, 거래번호·금액·계좌 식별이 일치하는 성공 결과만 납부 `PAID`와 시도 `SUCCEEDED`로 커밋하고 지급건을 만든다.
4. 회사 수납이 확인된 지급건에 한해 검증된 정산 계좌를 읽고 `SettlementAttempt`의 지급 거래번호·금액·당시 수취계좌·원수납 시도와 `PROCESSING`을 먼저 커밋한다. 최근 `SUCCEEDED` 수납 시도의 20자리 은행거래고유번호, 24자리 핀테크이용번호와 임차인 스냅샷을 사용한다. 회사 약정계좌는 SHA-256 식별자로 원수납과 일치하는지 확인하여 설정 변경으로 다른 회사계좌에서 지급하는 것을 차단한다. 기관용 `oob` 토큰으로 `POST /v2.0/transfer/deposit/acnt_num`을 호출한다.
5. API 응답뿐 아니라 `res_list` 안의 은행 응답과 거래번호·금액·수취계좌를 검증하여 지급을 `COMPLETED`로 바꾼다. 수납한 월세 전액을 지급하며 수수료 차감·분할 지급은 구현하지 않았다.

지급 장애가 발생하면 이미 성공한 임차인의 납부는 `PAID`로 유지하고 지급만 대기·확인 대상으로 남긴다. 지급계좌가 변경되어도 기존 지급 시도의 계좌·금액 스냅샷은 바뀌지 않는다. 아직 전송하지 않은 지급은 관리자 API 또는 활성화된 스케줄이 처리한다. 지급 실패가 이미 성공한 수납을 취소하거나 재출금하지 않는다.

| 상태 | 의미·가능한 처리 |
| --- | --- |
| 납부 `SCHEDULED` | 납부일·출금 계좌 조건을 확인하여 수납 가능 |
| 납부·지급 `PROCESSING` | 은행 요청 전 저널이 커밋됨. 중복 전송 금지 |
| 납부·지급 `UNKNOWN` | 은행 결과가 확정되지 않음. 자동·수동 재전송 금지 |
| 납부 `PAID` | 회사 수납 성공. 같은 회차를 다시 출금하지 않음 |
| 지급 `PENDING` | 아직 지급 요청을 전송하지 않은 보류건 |
| 지급 `COMPLETED` | 지급 성공. 같은 지급건을 다시 송금하지 않음 |
| 납부·지급 `FAILED` | 은행 증빙으로 원거래 최종 실패 확정. 사용자·관리자의 명시적 요청으로만 해당 단계 재시도 |

타임아웃·HTTP 오류·응답 불일치·은행 비성공 응답은 현재 모두 `UNKNOWN`으로 보류한다. 은행 코드별로 최종 실패가 확정되는 범위를 검증하지 않았으므로 자동 재시도를 허용하지 않는다. 은행 호출 직후 프로세스가 종료되면 `PROCESSING`이 남을 수 있으며, 이 경우도 재전송하지 않는다.

**원거래 자동조회는 최신 계약 명세와 원거래 결과 코드가 확보되지 않아 구현하지 않았다.** 운영자는 저장한 `bank_tran_id`와 요청시각·금액·계좌로 금융결제원/은행에서 원거래의 최종 결과를 확인하고, 증빙 참조번호를 확보한 뒤 관리자 결과 확정 API로 기록한다. 이 API는 은행 조회 기능이 아니다.

`UNKNOWN` 또는 요청 후 10분 이상 지난 `PROCESSING`의 현재 시도만 확정할 수 있다. 최근 시도의 거래번호와 정확히 일치해야 하며, 이미 확정된 시도나 빈 증빙은 거절한다. 최종 실패를 확인하면 `FAILED`로 바꾸고 명시적 재요청에 새 시도·새 거래번호를 사용한다. 수납 성공 확정은 `PAID`와 지급 대기건을 같은 트랜잭션으로 생성하고, 지급 성공 확정은 `COMPLETED`로 복구한다. 확정자·증빙·이전 상태·결과·확정시각은 `CollectionResolution`/`SettlementResolution`에 보존한다. 확정 이후 지연 응답은 상태를 덮어쓰지 않는다.

단순히 DB 상태를 `SCHEDULED`나 `PENDING`으로 초기화하고 재시도하면 중복 출금·송금이 발생할 수 있다. 10분 경과 자체는 실패의 증거가 아니다. 은행 증빙으로 최종 결과를 확인하기 전에는 실패 확정·재전송하지 않는다. 환불은 자동 처리 범위에 포함하지 않았다.

## 자동 실행 범위

`OPENBANKING_ENABLED`와 `OPENBANKING_SCHEDULING_ENABLED`가 모두 `true`일 때만 스케줄이 등록된다. 매 실행마다 납부일이 지난 `SCHEDULED` 출금 회차와 성공한 오픈뱅킹 수납 저널·검증계좌가 있는 `PENDING` 지급 건을 각각 `rentPaymentId` 기준으로 페이지당 100건씩 전체 순회한다. 앞 100건이 등록 미완료·설정 불일치·처리 실패로 남아도 다음 페이지를 처리한다. 요청 중이거나 결과가 미확정인 건과 증빙으로 확정 실패된 `FAILED`는 자동 재전송하지 않는다.

스케줄은 이미 DB에 생성된 `rent_payment`만 처리한다. 계약 등록 API는 아직 없으며 기존 계약에 대해 관리자 생성 API나 `RentService.generatePayments(contractId)`를 먼저 호출해야 한다. 기존 PG의 미정산·미확정 거래도 오픈뱅킹 거래로 간주하지 않으며 별도 대사가 필요하다.

## 기존 DB 전환

검토용 SQL은 `sql/openbanking-migration.sql`과 후속 `sql/rent-transfer-completion-migration.sql`이다. 이 작업에서는 DB에 실행하지 않았다. 기존 스키마를 대상으로 한 1회 전환안이므로, 백업과 실제 스키마 확인 후 필요한 문장만 순서대로 검토하여 적용한다. MySQL DDL은 암묵적으로 커밋되므로 전체 파일을 트랜잭션으로 되돌릴 수 있다고 가정하면 안 된다.

- 기존 PG·CMS 자동 실행과 새 오픈뱅킹 스케줄을 중지한 상태에서 전환한다. 처리 중인 과거 거래는 먼저 원거래를 확인한다.
- 모든 기존 결제 수단을 `REPLACED`로 바꾸고 `CARD`를 포함한 과거 타입을 `ACCOUNT`로 정리한다. 기존 결제 시도와의 관계는 보존한다. 이전 CMS 동의는 오픈뱅킹 동의로 옮기지 않으며, 사용자는 오픈뱅킹에서 출금 계좌를 새로 등록해야 한다.
- `fintech_use_num`은 과거 행을 보존하기 위해 nullable이다. 실제 새 출금은 24자리 핀테크이용번호와 출금 동의가 있는 활성 계좌만 허용한다.
- `billing_key`, `cms_registration_id`는 제거한다. 코드에서 제거한 `pg_transaction_id`는 과거 PG 대사 식별자를 보존하기 위해 DB에 남긴다. 새 `bank_tran_id`로 복사하지 않는다.
- `payment_attempt`와 `settlement`에 각각 nullable·unique인 `bank_tran_id`를 추가하고 `open_banking_connection`을 만든다. 과거 PG 성공 시도에는 오픈뱅킹 거래번호가 없으므로 새 자동·수동 지급 대상이 되지 않는다.
- 기존 정산 계좌 암호문이 이번 AES-256-GCM 형식·키와 다르면 재등록 또는 별도 검증된 암호화 전환이 필요하다. SQL로 암호문을 임의 변환하지 않는다.
- 후속 SQL은 `settlement_account.verified_at`, 수납 시도의 계좌·임차인·회사계좌 식별 스냅샷, `settlement_attempt`, `collection_resolution`, `settlement_resolution`과 조회 인덱스를 추가한다.
- 과거 계좌의 검증시각이나 과거 수납의 스냅샷을 추측해서 채우지 않는다. 은행 검증·원거래 대사 없이 새 자동 지급·수동확정 대상으로 옮기지 않는다.

`ddl-auto=update`는 폐기 컬럼 삭제나 데이터 의미 변환을 대신하지 않는다. 이미 새 앱이 컬럼을 만들었다면 SQL을 그대로 재실행하지 말고 현재 스키마와 비교한다. 새 빈 DB는 JPA 스키마 생성으로 준비할 수 있으며, 과거 스키마 전환용 SQL을 적용하지 않는다. 운영은 팀 규칙대로 `ddl-auto=validate`를 사용한다.

## 검증

```powershell
# backend에서 실행
.\gradlew.bat test

# frontend에서 실행
npm run lint
npm run build
```

백엔드 테스트는 H2와 가짜 HTTP 응답 또는 mock을 사용한다. 실제 은행 호출·이체는 테스트에 포함하지 않는다. 테스트베드 인증과 수납·지급의 실제 왕복 검증은 계약 정보와 응답정보를 설정한 환경에서 별도로 수행해야 한다.

## 참고한 금융결제원 문서

- [OAuth 인증](https://developers.kftc.or.kr/dev/openapi/open-banking/oauth)
- [사용자인증 URL](https://openapi.kftc.or.kr/openapi/openbanking/userAuth)
- [출금이체](https://developers.kftc.or.kr/dev/openapi/open-banking/withdraw)
- [입금이체](https://developers.kftc.or.kr/dev/openapi/open-banking/deposit)

실제 이용기관에 제공된 최신 명세와 계약 조건을 우선하여 인증·계좌조회·결과확인과 오류코드 처리 범위를 최종 확인해야 한다.
