# AGENT_001 — 관리자 중개사 목록 (#34)

## API 계약

`GET /admin/agents?keyword=보금&page=1`

- 인증: GET·HEAD 모두 기존 Bearer Access JWT, `ADMIN` 전용. 미인증·잘못된 토큰은 401, USER·AGENT는 승인 여부와 무관하게 403.
- `keyword`: 선택, 기본 빈 문자열. 앞뒤 공백 제거 후 사무소명 또는 사무소 주소의 대소문자 무시 부분 검색. `%`, `_`는 일반 문자다. 대표자명 검색은 포함하지 않는다.
- `page`: 선택, 기본 1. 기존 목록 API처럼 1 미만은 1로 보정하며 정수가 아니거나 int 범위를 넘으면 400 `INVALID_REQUEST`. 보정된 페이지의 조회 오프셋 `(page - 1) * 5`가 JPA의 기술적 상한 `Integer.MAX_VALUE`를 넘어도 같은 400을 반환한다. 현재 429496730까지 허용하고 429496731부터 거절하며, 별도의 업무상 최대 페이지 정책은 없다.
- 페이지 크기: 기존 목록과 같은 고정 5건. `size`, 클라이언트 정렬 옵션은 제공하지 않는다.
- 정렬: PENDING 우선 → 각 그룹에서 Agent.createdAt 내림차순 → agentProfileId 내림차순. APPROVED·REJECTED 사이에는 별도 우선순위가 없다.
- 빈 결과: 200, agents=[], totalPages=0, totalElements=0. 마지막 페이지 이후 조회는 agents=[]와 실제 전체 건수·전체 페이지 수를 반환한다.

```json
{
  "agents": [
    {
      "agentId": 12,
      "representativeName": "김보금",
      "officeName": "보금공인중개사",
      "officeAddress": "서울 송파구",
      "approvalStatus": "PENDING",
      "createdAt": "2026-11-11T10:00:00"
    }
  ],
  "page": 1,
  "totalPages": 1,
  "totalElements": 1
}
```

목록에는 연락처·이메일·비밀번호 해시·심사자·서류 저장 키를 포함하지 않는다. 가입일은 기존 Agent.createdAt이며 기존 날짜 직렬화 방식을 유지한다. 기존 Agent/User/ApprovalStatus를 사용하고 엔티티·DB 스키마·YAML 변경은 없다. User는 EntityGraph로 함께 조회한다.

## 범위 및 #35 연동 주의

- 이슈 #34와 작업 브리프 기준으로 목록·검색·페이징·현재 승인 상태 조회만 구현한다. 자격 서류 열람·유효성 검증·승인/반려는 #36 범위다.
- 상태는 기존 enum `PENDING`, `APPROVED`, `REJECTED`를 그대로 반환한다. 화면의 WAITING/NO_DOCUMENT 샘플 값으로 변환하거나 서류 제출 여부를 추정하지 않는다.
- 프론트엔드 #35는 수정하지 않았다. 기존 샘플의 `/api/v1/admin/agents` 주석과 필드명은 이 계약에 맞춰 연결해야 한다.
- 현재 Vite에는 `/admin` API 프록시가 없고 화면 주소도 `/admin/agents`이므로, #35에서 화면 탐색과 API 요청이 충돌하지 않도록 프록시/요청 주소를 함께 결정해야 한다. 백엔드 API는 8080 포트의 `/admin/agents`에서 제공한다.

## 검증

PowerShell, 작업 폴더 `C:\web\bogeum\backend`:

```powershell
.\gradlew.bat test --tests com.bogeum.AdminAgentReadTests
.\gradlew.bat test
```

AdminAgentReadTests는 실제 JWT 필터·H2 DB를 사용해 검색, 특수문자, 대기 우선·시간·동률 정렬, 필터 후 페이징/전체 건수, 빈 결과, 개인정보 제외, 401/403, 페이지 입력 처리를 검증한다.

### 실제 실행 결과 (2026-11-11)

- 본체 Java 컴파일 성공, `git diff --check` 통과.
- 일반 `test` 및 `test --tests com.bogeum.AdminAgentReadTests`는 기존 OpenBankingAuthorizationTest.java:23의 생성자 인자 불일치로 compileTestJava 단계에서 중단된다. origin/dev에도 같은 코드가 있으며 이번 변경에서는 수정하지 않았다.
- 해당 파일 하나만 컴파일 대상에서 제외한 실행: 352개 중 320개 통과, 32개 실패, 건너뜀 0개. #34 AdminAgentReadTests 8개, AuthIntegrationTests 49개, DemoAgentApprovalTests 3개는 모두 통과했다.
- 실패: 수정하지 않은 KakaoLoginIntegrationTests 20개, NaverLoginIntegrationTests 12개. 카카오는 테스트의 redirect_uri 추출 후 URLDecoder에서 null로 실패하며, 네이버도 소셜 로그인 경로에서 NullPointerException이 발생했다. 전체 테스트 통과 상태는 아니다.
- 제외 설정은 저장소 build.gradle을 변경하지 않고 임시 Gradle init script에만 적용했다.

동일한 제한을 둔 회귀 테스트 재현 (PowerShell, `C:\web\bogeum\backend`):

```powershell
$agentTestInit = Join-Path $env:TEMP 'bogeum-34-test-exclude.init.gradle'
Set-Content -LiteralPath $agentTestInit -Encoding ASCII -Value "allprojects { plugins.withId('java') { sourceSets.test.java.exclude('com/bogeum/domain/openbanking/OpenBankingAuthorizationTest.java') } }"
.\gradlew.bat test --init-script $agentTestInit
```

위 명령에 `--tests com.bogeum.AdminAgentReadTests`를 추가하면 #34 테스트만 선택할 수 있다. 제외된 테스트의 성공을 의미하지 않는다.

### P2 수정 후 검증 (2026-11-11)

- GET 규칙 바로 다음에 `/admin/agents` HEAD 전용 ADMIN 규칙을 추가했다. 다른 경로와 HTTP 메서드의 보안 규칙은 변경하지 않았다.
- 조회 전에 PageRequest의 long 오프셋이 Integer.MAX_VALUE를 넘는지 검사하고, 기존 AgentException·INVALID_REQUEST·전역 처리기를 통해 400을 반환한다. 신규 설정·클래스·스키마 변경은 없다.
- 관리자 GET/HEAD는 200, USER·AGENT GET/HEAD는 403, 미인증·잘못된 JWT의 HEAD는 401임을 회귀 테스트로 확인했다.
- 마지막 허용 페이지 429496730은 200과 빈 목록, 첫 오프셋 초과 페이지 429496731·int 최댓값 2147483647·int 바인딩 초과 2147483648은 400 INVALID_REQUEST를 확인했다. 기존 검색·정렬·정상 페이징 테스트도 통과했다.
- 실행 결과: AdminAgentReadTests 16개 + AuthIntegrationTests 49개 + DemoAgentApprovalTests 3개 = 68개 전부 통과. 실패·오류·건너뜀 0개. git diff --check 통과.
- 기존 OpenBankingAuthorizationTest 컴파일 오류 파일 하나는 앞과 동일한 임시 init script로 제외했다. 해당 파일과 카카오·네이버 테스트는 수정하지 않았으며, 이번 실행은 아래 세 테스트 클래스만 대상으로 했다. 전체 테스트 성공을 의미하지 않는다.

실제 실행 명령 (PowerShell, `C:\web\bogeum\backend`; 위 임시 스크립트 생성 후):

```powershell
.\gradlew.bat test --init-script "$env:TEMP\bogeum-34-test-exclude.init.gradle" --tests com.bogeum.AdminAgentReadTests --tests com.bogeum.AuthIntegrationTests --tests com.bogeum.DemoAgentApprovalTests
```

### 최신 dev 통합 검증 — f7c9630 (2026-11-11)

- 검증 기준: 작업 브랜치와 `origin/dev` 모두 `f7c9630bb2bc0476a588decec4fc0b08ebc5753d`. 해당 커밋에 #34 변경사항을 복원한 상태로 실행했다.
- #34의 `/admin/agents` GET·HEAD ADMIN 검사와 최신 dev의 계약 등록·후보 조회 승인 중개사 규칙이 함께 유지됐음을 확인했다.

| 구분 | 테스트 | 통과 | 실패·오류·건너뜀 |
|---|---|---:|---:|
| #34 | AdminAgentReadTests | 16 | 0 |
| 인증 | AuthIntegrationTests | 49 | 0 |
| 승인 | DemoAgentApprovalTests | 3 | 0 |
| 계약 | ContractControllerTest | 13 | 0 |
| 계약 | ContractServiceTest | 16 | 0 |
| 합계 | #34 16개 + 인증·계약 관련 81개 | **97** | **0** |

- 일반 테스트 명령은 기존 `OpenBankingAuthorizationTest.java:23`의 생성자 인자 불일치로 테스트 컴파일 단계에서 중단됐다. 위와 동일한 임시 Gradle init script로 이 파일 하나만 컴파일 대상에서 제외한 후, 아래 다섯 클래스의 선택 실행이 성공했다. 원본 테스트와 build.gradle은 수정하지 않았다.
- 소셜 로그인 테스트는 이번 통합 검증에서 **미실행**이다. 앞선 검증의 소셜 로그인 실패 32개가 해결됐다는 의미가 아니며, 해당 영역의 코드는 수정하지 않았다.
- **전체 테스트 및 MySQL 실DB 검증은 미완료**다. 위 통과 결과는 선택한 테스트와 H2 기반 검증 범위에 한정된다.
- `git diff --check` 통과, #34 변경 대상 Java 주석과 API 문서의 UTF-8 인코딩 정상 확인.

실제 통합 검증 명령 (PowerShell, `C:\web\bogeum\backend`; 위 임시 스크립트 생성 후):

```powershell
.\gradlew.bat test --init-script "$env:TEMP\bogeum-34-test-exclude.init.gradle" --tests com.bogeum.AdminAgentReadTests --tests com.bogeum.AuthIntegrationTests --tests com.bogeum.DemoAgentApprovalTests --tests com.bogeum.domain.contract.ContractControllerTest --tests com.bogeum.domain.contract.ContractServiceTest
```
