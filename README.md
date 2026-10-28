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


github에 pr 생기면 slack 으로 알림 오는지 테스트.v3
