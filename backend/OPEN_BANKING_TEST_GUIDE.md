# 월세 수납·지급 연동 테스트 가이드

이 문서는 **나중에 테스트 DB와 서버를 준비한 뒤** 실행할 절차다. 이번 코드 작업에서 애플리케이션 서버·MySQL 서버·실제 은행 이체를 실행하지 않았다. 자동 테스트의 mock/H2 검증과 금융결제원 테스트베드 왕복 검증은 구분한다.

이 가이드가 회차 생성·계좌 검증·증빙 기반 복구의 현재 테스트 정책이다. 기존 `OPEN_BANKING.md`의 과거 설명과 차이가 있다면 이 가이드와 현재 코드의 API·상태 전이를 함께 확인한다. 원거래 자동조회는 구현하지 않았고, 별도 은행 증빙을 확보한 관리자의 수동 확정만 제공한다. 계약 등록 API는 제공하지 않으며 회차 생성 API는 이미 DB에 있는 계약을 대상으로 한다.

목표는 계약의 `monthlyRent`만큼 임차인 계좌에서 회사 약정계좌로 수납한 다음, 같은 금액을 임대인 계좌로 지급하는 것이다. 은행 수수료는 월세 지급액에서 차감하지 않는다. `PAID`는 회사 수납 완료이고, 임대인 지급 완료는 `Settlement.COMPLETED`다.

청구 생성·수납 확정·상태 조회는 `RentService`, 관리자 API는 `AdminRentController`, 요청·응답은 `model/RentDto`에 통합되어 있다. 이력·감사 기록 저장소는 각 도메인의 기존 `RentRepository`·`SettlementRepository` 내부 인터페이스다. 이 구조 정리는 아래 HTTP 경로와 DB 스키마를 바꾸지 않는다.

## 1. 준비할 것

- Java 17, 저장소의 Gradle Wrapper, MySQL 테스트 DB, 프론트엔드 의존성.
- 서로 다른 테스트용 임차인·임대인 계정, 다른 임차인 계정 하나, `ADMIN` 계정, 계약에 연결할 중개사와 매물.
- 금융결제원 테스트베드의 Client ID/Secret, 10자리 이용기관코드, 회사 약정계좌, 입금이체용 암호문구의 SHA-512 해시.
- 테스트베드에 등록한 임차인 출금계좌와 임대인 지급계좌 및 해당 계좌의 응답정보.
- OAuth Callback URL. 기본값은 `http://localhost:5173/mypage/payment-method`이며 포털 설정과 정확히 일치해야 한다.
- 임대인의 은행코드·계좌번호·예금주·생년월일 6자리(`YYMMDD`). 생년월일은 계좌실명조회 요청에만 사용하며 DB나 브라우저 저장소에 저장하지 않는다.

테스트베드에서는 금융결제원에 설정한 가상 응답이 반환될 수 있다. 성공 응답을 받았다는 사실만으로 실제 은행 계좌에서 자금이 움직였다고 판정하지 않는다. 실제 자금 검증은 운영 승인·계약과 테스트 범위를 정한 별도 환경에서 수행한다.

## 2. DB 전환 및 설정

### 기존 DB

1. 테스트 DB 백업을 만들고 자동 작업을 중지한다.
2. `SHOW CREATE TABLE`로 실제 스키마와 엔티티를 비교한다.
3. 과거 PG/CMS 스키마라면 `sql/openbanking-migration.sql`에서 필요한 문장만 적용한다.
4. 이어서 `sql/rent-transfer-completion-migration.sql`의 추가 컬럼·테이블·인덱스를 검토하여 적용한다.
5. 이미 새 컬럼이 있다면 해당 `ALTER`/`CREATE`를 다시 실행하지 않는다. 두 SQL은 자동 적용 도구나 반복 실행용 스크립트가 아니다.
6. 과거 지급계좌는 은행 검증을 거쳐 다시 등록한다. SQL로 `verified_at`만 채워서 검증된 계좌로 만들지 않는다.

MySQL DDL은 암묵적으로 커밋된다. 전체 파일을 `ROLLBACK` 한 번으로 되돌릴 수 있다고 가정하지 않는다. 과거 미확정 거래를 임의로 재시도 가능 상태로 바꾸거나, 예전 결제 거래번호를 새 은행거래고유번호로 복사하지 않는다.

`src/main/resources/data.sql`은 기존 데이터를 지우는 더미데이터 스크립트다. 이 연동 테스트의 준비 단계로 실행하지 않는다. 새 지급 이력과 FK도 있으므로 과거 초기화 스크립트를 그대로 재사용하지 않는다.

### 새 빈 테스트 DB

빈 전용 DB에서는 개발 프로필의 Hibernate `update`로 현재 엔티티 스키마를 만들 수 있다. 기존 스키마용 마이그레이션을 빈 DB에 그대로 적용하지 않는다. 운영 프로필은 `validate`를 사용한다.

### 환경 변수

`backend/.env` 또는 실행 프로세스의 환경 변수에 테스트 전용 값을 설정한다. 예시의 `<...>`는 실제 테스트 값으로 바꾸며 Git에 올리지 않는다.

```properties
DB_URL=jdbc:mysql://localhost:3306/bogeum_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=<테스트 DB 사용자>
DB_PASSWORD=<테스트 DB 비밀번호>
OPENBANKING_ENABLED=true
OPENBANKING_SCHEDULING_ENABLED=false
OPENBANKING_BASE_URL=https://testapi.openbanking.or.kr
OPENBANKING_CLIENT_ID=<테스트 Client ID>
OPENBANKING_CLIENT_SECRET=<테스트 Client Secret>
OPENBANKING_REDIRECT_URI=http://localhost:5173/mypage/payment-method
OPENBANKING_FINTECH_ORG_CODE=<10자리 이용기관코드>
OPENBANKING_CONTRACT_ACCOUNT_NUM=<회사 약정계좌번호>
OPENBANKING_DEPOSIT_PASS_PHRASE=<SHA-512 해시 128자리>
OPENBANKING_ENCRYPTION_KEY=<32바이트 AES 키를 Base64로 인코딩한 값>
OPENBANKING_TIMEOUT=10s
```

기존 암호화 데이터가 있으면 같은 AES 키를 유지한다. 새 키를 넣으면 기존 토큰·계좌번호를 복호화할 수 없다. 서버 프로세스의 시간대도 `Asia/Seoul`로 맞춘다. 계약과 `dueAt`은 한국 현지 시각으로 저장한다.

## 3. 코드 검증 및 서버 실행 — 나중에 수행

다음은 사용자가 테스트 환경을 준비한 뒤 실행하는 명령이다. 자동 테스트는 외부 MySQL이나 은행 대신 H2와 mock을 사용한다.

```powershell
# backend 폴더
.\gradlew.bat test

# frontend 폴더
npm run lint
npm run build
```

검증 후 별도 터미널에서 서버를 실행한다. `spring.sql.init.mode=never`로 더미데이터 자동 초기화를 차단한다.

```powershell
# backend 폴더
.\gradlew.bat bootRun --args="--spring.profiles.active=dev --spring.sql.init.mode=never"

# frontend 폴더의 다른 터미널
npm run dev
```

수동 테스트가 끝날 때까지 `OPENBANKING_SCHEDULING_ENABLED=false`를 유지한다. 자동 실행을 먼저 켜면 이미 기한이 지난 모든 처리 가능 회차가 대상이 된다.

## 4. 테스트용 계약 준비

기존 테스트 계약이 있다면 해당 ID를 사용한다. 현재 계약 등록 화면/API 전체는 이 작업 범위에 포함하지 않았으므로, 계약이 없다면 테스트 DB에서 준비한다. 송금 관계자는 계약의 `tenant_id`/`landlord_id`로 결정하며 API 요청에서 임의 수취인을 받지 않는다.

먼저 테스트용 사용자와 매물을 확인한다.

```sql
SELECT user_id, email, name, role, user_status
FROM `user`
WHERE user_status = 'ACTIVE'
ORDER BY user_id;

SELECT property_id, landlord_id, agent_id, road_address, lease_type
FROM property
ORDER BY property_id;
```

전용 테스트 데이터에서만 다음 예시의 ID를 실제 확인한 값으로 바꾼다. `monthly_rent=1000`은 예시이며 테스트베드에 등록한 거래금액과 맞춘다. SQL로 OAuth 토큰·핀테크이용번호·출금동의·검증시각을 만들어 넣지 않는다.

```sql
SET time_zone = '+09:00';
SET @test_tenant_id = 101;
SET @test_property_id = 201;

START TRANSACTION;

INSERT INTO contract (
    property_id, landlord_id, tenant_id, agent_id, lease_type,
    deposit, monthly_rent, payment_day, start_at, end_at, created_at, updated_at
)
SELECT p.property_id, p.landlord_id, t.user_id, p.agent_id, 'MONTHLY',
       0, 1000, 1,
       CAST(DATE_FORMAT(DATE_SUB(CURRENT_DATE(), INTERVAL 1 MONTH), '%Y-%m-01') AS DATETIME),
       TIMESTAMP(LAST_DAY(CURRENT_DATE()), '23:59:59'),
       NOW(), NOW()
FROM property p
JOIN `user` t ON t.user_id = @test_tenant_id AND t.user_status = 'ACTIVE'
JOIN `user` l ON l.user_id = p.landlord_id AND l.user_status = 'ACTIVE'
JOIN `user` a ON a.user_id = p.agent_id AND a.role = 'AGENT' AND a.user_status = 'ACTIVE'
WHERE p.property_id = @test_property_id AND t.user_id <> l.user_id;

SELECT ROW_COUNT() AS inserted_contracts;
-- inserted_contracts가 1인지 확인한다. 0이면 ID·사용자·중개사 관계를 고치고 ROLLBACK한다.
SELECT LAST_INSERT_ID() AS contract_id;

COMMIT;
```

계약 시작·종료 시각 양끝을 포함한 범위 안에서 매월 `paymentDay`의 한국 시각 오전 9시를 청구한다. 29~31일이 없는 달은 말일로 보정한다. 일할 계산은 하지 않는다. 위 예시는 지난달 1일 회차를 포함하므로 현재 기한이 지난 회차를 선택할 수 있다.

## 5. JWT·CSRF를 포함한 PowerShell API 준비

브라우저에서는 로그인 및 공통 API 클라이언트가 인증·CSRF 처리를 한다. 아래 명령으로 직접 API를 확인할 때는 사용자마다 별도 WebSession과 Access Token을 사용한다.

```powershell
$testBaseUrl = 'http://localhost:8080'

function Connect-BogeumTest {
    param([string]$BaseUrl, [string]$Message)
    $testWebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $testCsrf = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/auth/csrf" -WebSession $testWebSession
    $testCredential = Get-Credential -Message $Message
    $testLoginBody = @{
        email = $testCredential.UserName
        password = $testCredential.GetNetworkCredential().Password
    } | ConvertTo-Json -Compress
    $testLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
        -WebSession $testWebSession -Headers @{ $testCsrf.headerName = $testCsrf.token } `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($testLoginBody))
    [pscustomobject]@{
        BaseUrl = $BaseUrl
        WebSession = $testWebSession
        AccessToken = $testLogin.accessToken
        UserId = $testLogin.user.userId
    }
}

function Invoke-BogeumTest {
    param($Login, [ValidateSet('Get', 'Post')][string]$Method, [string]$Path, $Body = $null)
    $testHeaders = @{ Authorization = "Bearer $($Login.AccessToken)" }
    if ($Method -eq 'Post') {
        $testCsrf = Invoke-RestMethod -Method Get -Uri "$($Login.BaseUrl)/api/v1/auth/csrf" -WebSession $Login.WebSession
        $testHeaders[$testCsrf.headerName] = $testCsrf.token
    }
    $testRequest = @{
        Method = $Method
        Uri = "$($Login.BaseUrl)$Path"
        WebSession = $Login.WebSession
        Headers = $testHeaders
    }
    if ($null -ne $Body) {
        $testRequest.ContentType = 'application/json; charset=utf-8'
        $testRequest.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
    }
    Invoke-RestMethod @testRequest
}

$testAdmin = Connect-BogeumTest $testBaseUrl '테스트 ADMIN 계정으로 로그인'
$testTenant = Connect-BogeumTest $testBaseUrl '계약의 임차인 계정으로 로그인'
$testLandlord = Connect-BogeumTest $testBaseUrl '계약의 임대인 계정으로 로그인'
$testOtherTenant = Connect-BogeumTest $testBaseUrl '다른 임차인 계정으로 로그인'
$testContractId = 301 # 위에서 확인한 실제 테스트 계약 ID
```

세션 객체나 로그인 응답 전체를 출력·공유하지 않는다. Access Token이 만료되어 401이 나면 해당 사용자만 다시 로그인한다. 이 도우미는 거래 요청을 자동 재전송하지 않는다. 로컬 HTTP 개발은 `auth.cookie-secure=false`인 dev 프로필을 사용한다. HTTPS 운영 환경은 Secure 쿠키 설정을 유지한다.

## 6. 계좌 연결과 검증

### 임차인

1. 프론트엔드에서 계약의 임차인으로 로그인한다.
2. `/mypage/payment-method`에서 **오픈뱅킹 계좌 연결하기**를 누른다.
3. 금융결제원 화면에서 본인 인증·계좌 등록·출금 동의를 마친다.
4. Callback으로 돌아온 뒤 출금동의가 있는 본인 계좌를 선택하고 등록한다.
5. 아래 조회가 등록한 은행코드와 마스킹 계좌번호를 반환하는지 확인한다.

```powershell
Invoke-BogeumTest $testTenant Get '/api/v1/tenants/me/payment-account'
```

`BankCode`는 은행 식별자다. 실제 출금은 OAuth에 연결된 24자리 핀테크이용번호와 사용자 토큰으로 요청한다. API 응답이나 DB에서 임의로 핀테크이용번호를 만들어 등록하지 않는다.

### 임대인

1. 임대인으로 로그인하고 `/mypage/settlement-account`를 연다.
2. 은행코드·계좌번호·예금주·생년월일 6자리를 입력한다.
3. 금융결제원 계좌실명조회가 성공하고 이름이 일치해야 등록된다.
4. **검증 완료** 표시와 응답의 `verifiedAt`을 확인한다. 조회 응답에 생년월일·계좌 원문·암호문이 없어야 한다.

```powershell
Invoke-BogeumTest $testLandlord Get '/api/v1/landlords/me/settlement-account'
```

재현하려는 테스트베드의 생년월일·은행코드·계좌번호·예금주 응답정보가 서로 맞아야 한다. 잘못된 예금주나 실명조회 실패로 등록이 거절되는지도 확인한다. 검증된 임대인 지급계좌가 없으면 임차인 출금이 시작되지 않아야 한다.

## 7. 회차 생성 및 정상 이체

```powershell
$testSchedule = @(Invoke-BogeumTest $testAdmin Post "/api/v1/admin/contracts/$testContractId/rent-payments")
$testSchedule | Format-Table rentPaymentId, seq, amount, dueAt, rentPaymentStatus

# 같은 요청을 한 번 더 보내도 ID·건수·금액·기한이 바뀌지 않아야 한다.
$testScheduleAgain = @(Invoke-BogeumTest $testAdmin Post "/api/v1/admin/contracts/$testContractId/rent-payments")
$testListed = @(Invoke-BogeumTest $testAdmin Get "/api/v1/admin/contracts/$testContractId/rent-payments")

# 기한이 지난 미납 회차 하나만 선택한다. PC와 서버 시간대를 Asia/Seoul로 맞춘다.
$testDue = $testListed | Where-Object {
    $_.rentPaymentStatus -eq 'SCHEDULED' -and [datetime]$_.dueAt -le (Get-Date)
} | Sort-Object seq | Select-Object -First 1
if ($null -eq $testDue) { throw '기한이 지난 SCHEDULED 테스트 회차가 없습니다.' }
$testRentPaymentId = $testDue.rentPaymentId

$testPayment = Invoke-BogeumTest $testTenant Post "/api/v1/tenants/me/rent-payments/$testRentPaymentId/pay"
$testPayment
Invoke-BogeumTest $testTenant Get '/api/v1/tenants/me/rent-payments'
$testTransfer = Invoke-BogeumTest $testAdmin Get "/api/v1/admin/rents/$testRentPaymentId/transfer-status"
$testTransfer | ConvertTo-Json -Depth 8
```

정상 결과:

- `RentPayment.PAID`, 출금 시도 `SUCCEEDED`, 지급 `COMPLETED`, 지급 시도 `SUCCEEDED`.
- `RentPayment.amount = PaymentAttempt.amount = Settlement.amount = 지급 시도 금액`.
- 수납 거래번호와 지급 거래번호는 서로 다르고, 각 시도마다 유일하다.
- 상태 조회의 `outstandingAmount=0`. 처리 전·실패·확인 중인 지급의 미지급액은 완료로 처리되지 않는다.
- 계좌·토큰 원문이 응답에 없어야 한다. 임차인 화면은 **회사 수납 완료 / 임대인 지급 완료**를 구분한다.

DB에서도 해당 회차만 확인한다. 아래 `@test_rent_payment_id`는 위 API에서 확인한 값으로 바꾼다.

```sql
SET @test_rent_payment_id = 401;

SELECT r.rent_payment_id, r.contract_id, r.seq, r.amount, r.due_at,
       r.rent_payment_status, r.paid_at
FROM rent_payment r WHERE r.rent_payment_id = @test_rent_payment_id;

SELECT payment_attempt_id, attempt_no, amount, bank_tran_id, attempt_status,
       requested_at, completed_at
FROM payment_attempt WHERE rent_payment_id = @test_rent_payment_id ORDER BY attempt_no;

SELECT settlement_id, amount, settlement_status, bank_tran_id, paid_at
FROM settlement WHERE rent_payment_id = @test_rent_payment_id;

SELECT a.settlement_attempt_id, a.attempt_no, a.amount, a.bank_tran_id,
       a.attempt_status, a.requested_at, a.completed_at
FROM settlement_attempt a
JOIN settlement s ON s.settlement_id = a.settlement_id
WHERE s.rent_payment_id = @test_rent_payment_id
ORDER BY a.attempt_no;
```

조회 범위는 해당 테스트 회차로 제한한다. 계좌 스냅샷·토큰 원문·암호문을 전체 조회하거나 복사·공유하지 않는다. 금융결제원 테스트베드 거래 내역에서도 두 거래번호·금액·출금/입금 계좌가 일치하는지 대조한다.

## 8. 중복·권한·날짜 검증

| 시나리오 | 실행 및 기대 결과 |
| --- | --- |
| 납부 중복 요청 | 완료 회차에 `pay`를 다시 요청한다. 출금·지급 횟수와 이력 수가 늘지 않는다. |
| 관리자 지급 중복 | 완료 회차에 `POST /api/v1/admin/rents/{id}/settlement`. 재송금하지 않는다. |
| 다른 임차인 | `$testOtherTenant`로 대상 회차 `pay`. 조회 실패/권한 오류가 나며 은행 요청·이력 생성이 없어야 한다. |
| 관리자 API 권한 | 임차인으로 회차 생성·상태 조회·결과 확정 요청. 403이어야 한다. |
| CSRF 누락 | 유효한 JWT만 넣고 POST. 403 `CSRF_INVALID`이며 은행 호출이 없어야 한다. |
| 미래 납부일 | 기한이 지나지 않은 회차를 납부 요청. 거절되고 출금 이력이 없어야 한다. |
| 미검증 지급계좌 | 계좌를 등록하지 않은 별도 테스트 임대인의 계약을 납부. 출금 전 거절한다. |
| 회차 재생성 | 같은 계약에 생성 API를 재호출. 기존 회차와 금액·기한을 유지한다. |
| 월말 | 별도 계약의 paymentDay를 31로 준비. 2월은 28/29일, 4월은 30일 오전 9시로 생성한다. |
| 전세 | 전세·monthlyRent 0인 별도 계약에는 월세 회차가 생성되지 않는다. |
| 잘못된 계약 | 음수/소수 월세, 잘못된 납부일·계약기간은 생성 시 거절된다. |

금액 스냅샷을 검증하려면 전용 계약의 회차 생성 후 계약 `monthlyRent`만 변경하고 생성 API를 다시 호출한다. 이미 생성한 회차는 바뀌지 않아야 한다. 과거 회차를 지우고 재생성해서 검증하지 않는다.

## 9. 응답 유실·서버 중단과 원거래 수동 확정

단위·통합 테스트는 은행 HTTP 응답을 대체하여 타임아웃을 재현한다. 실제 연동 테스트의 장애는 테스트베드 전용 프록시나 승인된 테스트 환경에서만 재현한다. 실제 은행 거래 상태와 무관하게 DB를 `UNKNOWN`으로 고치는 방식으로 이체 결과를 만들어내지 않는다.

**이 구현의 결과 확정 API는 자동 은행 조회가 아니다.** 관리자가 금융결제원/은행에서 해당 원거래의 최종 상태를 확인한 뒤, 그 증빙 참조번호와 함께 결과를 기록하는 수동 대사 기능이다. 미확정 요청이 나중에 성공할 가능성이 남아 있으면 실패로 확정하지 않는다.

1. 관리자 `transfer-status`에서 해당 회차의 현재 출금/지급 거래번호와 상태를 확인한다.
2. `UNKNOWN` 또는 장시간 `PROCESSING`인 거래의 거래번호·요청시각·금액·계좌를 원거래와 대조한다.
3. 은행에서 최종 성공 또는 최종 실패를 확인하고 추적 가능한 증빙 참조번호를 확보한다.
4. 그 **현재 거래번호 그대로** 아래 API로 확정한다. 존재하지 않는 거래번호나 과거 시도 번호는 거절되어야 한다.

`PROCESSING`은 요청 후 10분이 지나야 수동 확정을 허용한다. 10분 경과는 실패의 증거가 아니므로 은행의 최종 결과 확인은 여전히 필요하다. 아래 `TEST-EVIDENCE-...`는 예시이며 실제 확보한 테스트 증빙 참조번호로 바꾼다.

```powershell
# 출금 결과 확정 예시. 원거래 최종 성공을 확인한 경우에만 true를 사용한다.
$testCollectionResolution = @{
    bankTranId = $testTransfer.collectionBankTranId
    succeeded = $true
    evidenceReference = 'TEST-EVIDENCE-COLLECTION-001'
}
Invoke-BogeumTest $testAdmin Post "/api/v1/admin/rents/$testRentPaymentId/collection-resolution" $testCollectionResolution

# 지급 결과 확정 예시. 별도의 미확정 지급 거래에만 수행한다.
$testTransfer = Invoke-BogeumTest $testAdmin Get "/api/v1/admin/rents/$testRentPaymentId/transfer-status"
$testSettlementResolution = @{
    bankTranId = $testTransfer.settlementBankTranId
    succeeded = $true
    evidenceReference = 'TEST-EVIDENCE-SETTLEMENT-001'
}
Invoke-BogeumTest $testAdmin Post "/api/v1/admin/rents/$testRentPaymentId/settlement-resolution" $testSettlementResolution
```

위 두 명령은 순서대로 무조건 실행하는 절차가 아니다. 실제 미확정 상태인 단계 하나를 선택한다. 성공을 확인한 출금은 수납 완료와 지급 대기건으로 복구되며, 지급이 별도로 대기 중이면 관리자 지급 API를 호출한다. 확정된 상태에 같은 결과 확정 요청을 반복해도 추가 이체를 만들지 않아야 한다.

확정 실패를 검증할 때는 **은행이 최종 실패를 확인한 경우에만** `succeeded=$false`로 보낸다.

- 출금 확정 실패 → 납부·출금 시도 `FAILED`. 임차인이 `pay`를 명시적으로 다시 요청하면 새 시도와 새 거래번호로 출금한다.
- 지급 확정 실패 → 지급·지급 시도 `FAILED`, 회사 수납은 `PAID` 유지. 관리자가 아래 지급 API를 명시적으로 다시 요청하면 지급만 재시도한다.
- `UNKNOWN`/`PROCESSING` 상태에서는 두 재요청 모두 중복 이체를 막아야 한다.
- 관리자 ID·증빙 참조번호·확정시각이 이력에 남고, 빈 증빙·잘못된 거래번호·확정 중복 요청은 거절되어야 한다.

```sql
SELECT cr.collection_resolution_id, cr.admin_user_id, cr.evidence_reference,
       cr.previous_status, cr.succeeded, cr.resolved_at
FROM collection_resolution cr
JOIN payment_attempt p ON p.payment_attempt_id = cr.payment_attempt_id
WHERE p.rent_payment_id = @test_rent_payment_id;

SELECT sr.settlement_resolution_id, sr.admin_user_id, sr.evidence_reference,
       sr.previous_status, sr.succeeded, sr.resolved_at
FROM settlement_resolution sr
JOIN settlement_attempt a ON a.settlement_attempt_id = sr.settlement_attempt_id
JOIN settlement s ON s.settlement_id = a.settlement_id
WHERE s.rent_payment_id = @test_rent_payment_id;
```

```powershell
# 아직 전송하지 않은 PENDING 지급 또는 최종 실패가 확인된 FAILED 지급에만 사용한다.
Invoke-BogeumTest $testAdmin Post "/api/v1/admin/rents/$testRentPaymentId/settlement"
```

지급 장애 때문에 이미 성공한 임차인 출금을 취소하거나 같은 임차인에게 다시 출금해서는 안 된다. 결과를 확인하지 않고 SQL로 `SCHEDULED`/`PENDING`으로 초기화하지 않는다. 환불은 이 코드의 자동 처리 범위가 아니며 별도의 원거래 확인·환불 절차가 필요하다.

## 10. 지급계좌 변경과 미지급액 확인

1. 지급 응답 유실로 `UNKNOWN`인 테스트 회차를 준비하고 시도 이력을 확인한다.
2. 임대인이 다른 본인 명의 계좌를 은행 검증 후 등록한다.
3. 과거 지급 시도에 저장된 계좌 스냅샷·금액·거래번호가 바뀌지 않는지 확인한다.
4. 새 계좌 등록만으로 미확정 지급이 다시 전송되지 않아야 한다.
5. 과거 지급이 최종 실패로 확정된 뒤 명시적으로 재시도하면 새 시도는 새 검증계좌를 사용한다. 기존 시도 기록은 남아야 한다.

`outstandingAmount`는 회사가 받은 월세 중 이 회차에서 아직 지급 완료되지 않은 금액이다. 은행 계좌의 실시간 잔액을 의미하지 않는다. 지급 결과가 미확정인 동안에는 미지급액으로 추적하고 은행 원거래와 대조한다.

회사 약정계좌 설정 변경도 별도 테스트베드 시나리오로 확인한다. 원수납과 현재 회사계좌의 식별 해시가 다르면 지급은 거절되어야 한다. 이미 수납한 대기 건을 다른 회사계좌에서 임의로 지급하지 않는다. 회사계좌를 전환해야 한다면 미지급금·원거래 대사를 먼저 진행하고 별도의 전환 절차를 정한다. 과거 해시를 SQL로 새 값에 맞춰 덮어쓰지 않는다.

## 11. 자동 실행은 수동 검증 후 별도로 확인

임차인의 정기 출금 동의 범위와 계약·지급계좌를 확인한 뒤, 자동 테스트 전용 DB에서만 다음 설정으로 재시작한다.

```properties
OPENBANKING_ENABLED=true
OPENBANKING_SCHEDULING_ENABLED=true
OPENBANKING_SCHEDULE_DELAY_MS=60000
```

- 기한이 지난 `SCHEDULED` 회차가 한 번 수납되고, 성공한 수납의 `PENDING` 지급이 처리되는지 확인한다.
- 실행 주기가 반복되어도 출금·지급 횟수가 늘지 않아야 한다.
- 출금과 지급은 각각 `rentPaymentId` 기준으로 페이지당 100건씩 전체 순회한다. 선두 100건이 계좌 미등록·설정 불일치 등으로 실패해 남아 있어도 101번째 정상 회차가 처리되는지 별도 데이터로 확인한다.
- `UNKNOWN`/`PROCESSING`과 확정 실패 `FAILED`는 스케줄러가 자동 재전송하지 않아야 한다.
- 계약 확정 흐름에서 `RentService.generatePayments(contractId)`를 호출하거나 관리자 생성 API로 회차를 먼저 만들어야 한다. 스케줄러는 DB에 없는 계약 회차를 임의 생성하지 않는다.
- 자동 실행 검증이 끝나면 다시 `OPENBANKING_SCHEDULING_ENABLED=false`로 변경하고 재시작한다.

## 이번 코드 작업의 검증 결과

2026-10-31 기준으로 애플리케이션 서버와 외부 MySQL을 실행하지 않고 다음을 확인했다.

- 백엔드 `gradlew.bat --offline --no-daemon test`: **196개 테스트 통과**, 실패·오류·건너뜀 0개. H2 메모리 DB, MockMvc, 가짜 은행 응답을 사용했다.
- 프론트엔드 `npm run lint`, `npm run build`: 통과. 빌드의 500 kB 초과 번들 안내는 남아 있다.
- `git diff --check`: 통과.
- MySQL 마이그레이션 적용, 금융결제원 테스트베드 왕복, 운영계좌 실제 이체는 실행하지 않았다. 위 절차에 따라 별도로 확인해야 한다.

## 완료 판정

정상 두 단계 이체, 회차·송금 중복 방지, 타인 접근 차단, 계좌 실명조회, 결과 불명 거래 보류와 증빙 기반 복구, 계좌 변경 이력 보존, 미지급액 추적을 모두 확인한다. mock/H2 테스트 통과와 테스트베드 통과를 각각 기록하고, 운영계좌 실제 이체를 확인하지 않았다면 실제 이체 완료라고 기록하지 않는다.

참고 명세: [OAuth](https://developers.kftc.or.kr/dev/openapi/open-banking/oauth), [계좌실명조회](https://developers.kftc.or.kr/dev/openapi/open-banking/account), [출금이체](https://developers.kftc.or.kr/dev/openapi/open-banking/withdraw), [입금이체](https://developers.kftc.or.kr/dev/openapi/open-banking/deposit). 실제 이용기관에 제공된 최신 계약 명세와 금융결제원 응답정보를 우선한다.
