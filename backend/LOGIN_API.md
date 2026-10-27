# 일반 로그인 API 계약

기존 `UserDto.LoginReq`와 `UserDto.LoginRes`를 기준으로 프론트와 백엔드가 사용할 계약이다.
프론트 로그인 폼 → 컨트롤러 → 서비스 → DB 사용자 조회가 연결되어 있다.
현재 API는 이메일·비밀번호 검증과 사용자 정보 반환까지 처리한다. 세션 발급·유지·로그아웃은 아직 구현하지 않았으므로 새로고침 시 화면의 로그인 상태는 초기화된다.

## 요청

- 경로: `POST /api/v1/auth/login`
- Content-Type: `application/json`
- DTO: `UserDto.LoginReq`

```json
{
  "email": "user@example.com",
  "password": "example-password"
}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `email` | 문자열 | 필수, 이메일 형식. `User.email`에 저장된 로그인 아이디 |
| `password` | 문자열 | 필수, 빈 값·공백만 있는 값은 허용하지 않음 |

로그인 폼은 이메일 앞뒤 공백을 제거한다. 비밀번호는 공백만 있는지 검증하되, 입력값 자체는 변경하지 않는다.
요청에는 `loginId`를 사용하지 않는다.

## 성공 응답

- HTTP 상태: `200 OK`
- DTO: `UserDto.LoginRes`
- 아래 객체를 응답 본문으로 반환한다.

```json
{
  "userId": 1,
  "name": "홍길동",
  "role": "USER",
  "lastLoginProvider": "LOCAL"
}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `userId` | 숫자 | 사용자 식별자 |
| `name` | 문자열 | 사용자 이름 |
| `role` | 문자열 | 신규 일반 가입은 `USER`. `ADMIN`, `AGENT`는 별도 계정 유형이며 기존 `LANDLORD`, `TENANT` 값은 호환을 위해 유지 |
| `lastLoginProvider` | 문자열 | 일반 로그인 성공 응답은 `LOCAL` |

`LoginProvider` 전체 값은 `LOCAL`, `NAVER`, `KAKAO`, `GOOGLE`이다. 소셜 로그인 API는 이번 계약 범위에 포함하지 않는다.
응답에는 `loginType`, 비밀번호, 비밀번호 해시를 포함하지 않는다.
서비스에서 인증 성공 시 `lastLoginProvider`를 `LOCAL`로 갱신한 다음 컨트롤러에서 `LoginRes.from(user)`로 변환한다. DTO 변환에는 인증·갱신 로직을 넣지 않는다.

## 프론트 연결 기준

- 입력 필드 이름은 `email`, `password`로 맞춘다.
- 로그인 성공 응답 전체를 `authStore.login()`에 전달한다.
- `lastLoginProvider`는 기존 `LAST_LOGIN_TYPE_KEY`(`bogeum.lastLoginType`)에 저장하여 최근 로그인 수단 안내에 사용한다.
- 일반 로그인 상수와 최근 사용 배지는 `LOCAL`을 기준으로 비교한다.
- 후속 단계에서 서버 세션으로 로그인 유지를 구현한다. 최근 로그인 수단 저장값을 인증 근거로 사용하지 않는다.

## 오류 응답

| HTTP 상태 | code | 발생 조건 |
|---|---|---|
| `400` | `INVALID_REQUEST` | 필수 값 누락, 이메일 형식 오류, 잘못된 JSON |
| `401` | `LOGIN_FAILED` | 계정 없음, 비밀번호 불일치, 탈퇴 계정, 비밀번호 해시가 없는 소셜 전용 계정 |

오류 본문은 `{ "code": "LOGIN_FAILED", "message": "이메일 또는 비밀번호를 확인해 주세요." }` 형식이다.

## 브라우저에서 확인

1. MySQL 접속 환경 변수를 설정하고 백엔드(8080)와 프론트(5173)를 실행한다.
2. DB에 `ACTIVE` 사용자와 BCrypt 형식의 `password_hash`가 있어야 성공 응답을 받을 수 있다. 원문 비밀번호를 DB에 저장하지 않는다.
3. `/login`에서 F12 → Network → Fetch/XHR를 열고 이메일·비밀번호를 입력하여 로그인한다.
4. `login` 요청의 Payload에서 `email`, `password`, Response에서 성공 응답 또는 오류를 확인한다. 프론트의 이메일 검증에 걸리면 요청 자체가 전송되지 않는다.

테스트 계정 자동 생성은 포함하지 않는다. 세션 생성·복원·로그아웃은 후속 단계에서 구현한다.

BCrypt 한도인 UTF-8 72바이트를 넘는 로그인 비밀번호는 기존 `401 LOGIN_FAILED`로 응답한다.

## 일반 회원가입 API

- 경로: `POST /api/v1/auth/signup/user`
- Content-Type: `application/json`
- 요청 DTO: `UserDto.SignupRequest`
- 성공: `201 Created`, `UserDto.SignupResponse` 형식의 `{ "userId": 1 }`

```json
{
  "email": "user@example.com",
  "password": "example-password",
  "name": "홍길동",
  "phone": "01012345678",
  "agreements": {
    "privacyCollect": true,
    "thirdPartyProvide": true
  }
}
```

모든 필드는 필수다. 이메일은 255자, 이름은 50자, 전화번호는 20자 이하로 받는다. 이메일을 로그인 아이디로 사용하며 별도의 `loginId`는 사용하지 않는다. 비밀번호 확인은 화면에서 처리하고 입력 비밀번호의 공백은 변경하지 않는다. 두 동의 항목은 모두 `true`여야 한다.

- `UserService.signup()`은 HTTP DTO에 의존하지 않고 이메일, 비밀번호, 이름, 전화번호, 두 필수 동의 여부를 받는다.
- 가입 요청에는 `role`이 없다. 서버가 계정 유형을 `USER`로 지정하며 임대인·임차인 구분은 계약 단위로 관리한다. 이메일 형식, DB 필수값·길이, 두 필수 동의를 검증한다. 비밀번호는 공백만으로 구성할 수 없으며 UTF-8 72바이트 한도를 적용한다.
- 기존 `UserRepository`와 사용자→동의 관계의 `PERSIST` 전파를 통해 `user`와 `user_consent` 두 건을 하나의 트랜잭션에서 저장한다. 테이블·컬럼 추가는 없다. 동의 저장 실패 시 사용자도 롤백한다.
- 비밀번호는 BCrypt 해시만 저장한다. 기존 이메일은 `EMAIL_ALREADY_EXISTS`, 사전 조회 뒤 발생한 DB 제약 위반은 원인을 보존한 `SIGNUP_CONFLICT`로 거절한다.
- 사용자 상태는 기존 기본값인 `ACTIVE`이며 본인 인증 시각은 비워 둔다. 이름과 전화번호는 사용자 입력값이며 검증된 신원 정보가 아니다. `verificationToken`으로 본인 인증을 처리하는 기능은 없다.

| HTTP 상태 | code | 발생 조건 |
|---|---|---|
| `400` | `INVALID_REQUEST` | 필수값 누락, 형식·길이 오류, 동의 누락·거절, 잘못된 JSON |
| `400` | `INVALID_SIGNUP` | 필수 동의 조건 위반, 비밀번호 UTF-8 72바이트 초과 |
| `409` | `EMAIL_ALREADY_EXISTS` | 이미 등록된 이메일 |
| `409` | `SIGNUP_CONFLICT` | 동시 가입 등으로 DB 저장 제약 위반 |

오류는 기존 `ErrorResponse`의 `code`, `message` 형식으로 반환한다. 가입 응답에는 비밀번호·해시를 포함하지 않으며 자동 로그인이나 세션을 발급하지 않는다.

### 프론트 연결 시 남은 사항

일반 가입 폼의 화면 구성은 유지하고 제출 함수를 연결했다. `IdentityVerificationField`의 전화번호를 폼 값으로 전달하며 이름·이메일·비밀번호·전화번호·동의를 전송한다. 성공 시 안내 후 `/login`으로 이동하고 실패 시 오류를 안내한다. `loginId`, 비밀번호 확인, 임시 인증 토큰은 요청에 포함하지 않는다.

| 연결을 보류한 부분 | 확정되지 않은 내용 |
|---|---|
| 일반 가입 아이디 | 화면의 `loginId`와 `email`을 API의 `email` 하나로 정리해야 함 |
| 본인 인증 | 인증 업체·토큰 검증·검증된 이름과 전화번호 전달 방식 미정 |
| 중개사 가입 | 사용자 이름이 요청에 없음. 가입·승인 응답 계약 확정 필요 |
| 약관 | 화면의 본문이 임시 문구. 실제 동의 내용과 약관 조회 계약 필요 |
| 화면 제출 | 중개사 폼은 별도 API 연결 필요 |

이름·전화번호·본인 인증 결과를 임의로 만들어 저장하지 않는다. 중개사 가입, 소셜 로그인, 관리자 로그인 화면 연결, 로그인 유지·로그아웃은 이번 구현에 포함하지 않는다.

검증은 `./gradlew test`로 실행한다. 기존 테스트 파일만 확장했으며 H2를 사용한다. 회원가입 API→DB 저장→기존 로그인 API, 중복 및 DB 고유 제약, 필수값·동의·역할·비밀번호 한도, 동의 저장 실패 시 전체 롤백을 확인한다. 브라우저의 회원가입 완료 흐름을 검증한 것은 아니다.
