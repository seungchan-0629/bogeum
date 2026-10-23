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

개발 중에는 Vite가 `/api` 요청을 `http://localhost:8080`으로 프록시합니다.

최근 로그인 수단 안내 API와 프런트 연동 방법은 [최근 로그인 안내](docs/recent-login.md)를 참고하세요.
