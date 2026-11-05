# bogeum

## 구조

```
bogeum/
├─ backend/   Spring Boot 4 (Java 17, Gradle)
└─ frontend/  React + Vite
```

## 실행

```bash
# backend (http://localhost:8080)
cd backend
./gradlew bootRun

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

개발 중에는 Vite가 `/api`, `/auth`, `/users`, `/openbanking` 요청을 `http://localhost:8080`으로 프록시합니다. 회원가입·로그인·회원 정보·계좌 등록은 `/auth`, `/users/me`, `/openbanking` 경로를 사용하고, 그 외 API는 기존 경로를 유지합니다.

## 가상 금결원 (계좌 등록 테스트)

백엔드와 프론트엔드 외에 터미널 하나에서 실행합니다. Node.js만 사용하며 별도 패키지 설치는 필요 없습니다.

```bash
cd fake-kftc
npm start
```

- 화면 미리보기: `http://localhost:8090`
- 보금(`http://localhost:5173`) 로그인 → 마이페이지 → **계좌 등록하기** → 은행·계좌번호·예금주 입력 및 동의 → 계좌 연결
- **테스트 정보 채우기**를 누르면 예시가 입력됩니다. 필수 동의는 직접 선택합니다.
- 제출하면 `code`, 요청 때 받은 `state`를 `http://localhost:5173/mypage/openbanking/callback`으로 돌려줍니다. 보금은 코드로 토큰과 가상 계좌 정보를 받아 기존 계좌 등록 로직으로 저장합니다.
- 취소하면 `error=access_denied`와 동일한 `state`를 콜백으로 보냅니다.

이 서버는 계좌 등록에 필요한 인증·토큰 발급·사용자 계좌 조회만 흉내 냅니다. 실제 인증·이체와 토큰 갱신은 지원하지 않습니다. 가상 서버의 코드와 토큰은 메모리에만 보관되며 재시작하면 사라집니다. 실제 계좌 정보 대신 테스트 값을 입력하세요.

```bash
cd fake-kftc
npm test
```
