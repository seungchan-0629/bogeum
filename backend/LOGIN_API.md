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
  "role": "TENANT",
  "lastLoginProvider": "LOCAL"
}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `userId` | 숫자 | 사용자 식별자 |
| `name` | 문자열 | 사용자 이름 |
| `role` | 문자열 | `ADMIN`, `LANDLORD`, `TENANT`, `AGENT` 중 하나 |
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

회원가입과 테스트 계정 자동 생성은 포함하지 않는다. 세션 생성·복원·로그아웃은 후속 단계에서 구현한다.
