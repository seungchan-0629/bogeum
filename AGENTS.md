# bogeum

> 이 파일은 로컬 전용이다 (`.git/info/exclude`로 제외, 팀원은 Claude 미사용).

## 응답 언어
- 모든 응답과 설명은 **한국어**로 한다.
- 응답에 추가적인 더 쉽게 설명해줘라고 할 경우 **코딩을 하나도 모르는 사람에게 설명하듯이 쉬운 예시**를 들어 설명해준다.

# 보금 (Bogeum)

## 서비스 개요

| 항목 | 내용 |
|---|---|
| 서비스명 | 보금 |
| 이름의 뜻 | 보증금 + 보금자리. 보증금과 사는 집을 함께 지킨다는 뜻이다 |
| 한 줄 소개 | 집을 구할 때부터 나갈 때까지, 보증금과 월세를 기록으로 관리하는 부동산 서비스 |
| 목표 | 매물을 쉽게 찾고 전·월세 보증금과 보금자리를 안전하고 편리하게 관리한다 |
| 다루는 거래 | 전세, 월세, 매매 |
| 서비스 형태 | 웹, 앱 서비스 모두 |

## 사용자

| 역할 | 설명 |
|---|---|
| 관리자 | 서비스 운영자. 문의에 답하고, 매물 신고를 처리하고, 등기상 소유자와 등록자가 다른 매물을 검수한다 |
| 집주인 | 매물을 등록하고, 계약 후 월세와 보증금을 관리하려는 사용자 |
| 세입자 | 매물을 찾고, 계약 후 월세 납부와 보증금 반환을 관리하려는 사용자 |
| 중개사 | 매물의 중개를 수락하고 계약을 관리하려는 사용자 |

## 핵심 가치

| 가치 | 설명 |
|---|---|
| 쉽게 찾는다 | 조건으로 매물을 찾고, 관심 매물로 저장하고, 채팅으로 집주인과 바로 연락한다 |
| 안전하게 확인한다 | 모든 매물에 등기부등본을 받아, 확인된 위험 신호를 뜻과 함께 보여준다 |
| 편리하게 관리한다 | 계약서를 올리면 월세 납부, 기한 알림, 입주 기록, 퇴실 정산까지 이어진다 |

## 구조
```
bogeum/
├─ backend/   Spring Boot 4.1.1, Java 17, Gradle (wrapper)
└─ frontend/  React 19 + Vite (JavaScript)
```
- 백엔드 패키지 루트: `com.bogeum`
- 설정 파일: `backend/src/main/resources/application.yml`
- 개발 중에는 Vite가 `/api` 요청을 `http://localhost:8080`으로 프록시한다 (CORS 설정 불필요)

## 명령어
```bash
# 백엔드
cd backend && ./gradlew bootRun     # 실행 (8080)
cd backend && ./gradlew test        # 테스트

# 프론트엔드
cd frontend && npm run dev          # 실행 (5173)
cd frontend && npm run lint         # 린트 (oxlint)
cd frontend && npm run build        # 빌드
```

## Git 규칙
- 커밋은 사용자가 명시적으로 요청할 때만 한다. 기본은 변경 파일 정리 + 커밋 메시지 제안까지만.
- **push,pr 금지** — 항상 사용자가 직접 한다.
- 커밋 메시지에 `Co-Authored-By`를 넣지 않는다.
- 커밋 메시지는 한국어로 쓴다.

## 코드 규칙
- API 경로는 `/api/`로 시작한다. (반드시 지켜야하는건 아님)
- 주변 코드 스타일을 따른다 (백엔드는 탭 들여쓰기, 프론트엔드는 스페이스 2칸).
- 패키지 구조: **기능별**. 기능은 `com.bogeum.domain.{기능}` 아래에 두고 엔티티는 `model/`에 둔다. 공통 코드는 `common`(enums, exception, model), 설정은 `config`, 유틸은 `utils`에 둔다.
- DB / ORM: MySQL + Spring Data JPA (Lombok 사용). 테스트는 H2 인메모리 DB. `ddl-auto`는 dev=`update`, prod=`validate`. 접속 정보는 환경 변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)로 받는다.
<!-- TODO: 인증 방식 -->
<!-- TODO: 프론트 라우팅·상태관리·스타일링 -->
<!-- TODO: 테스트 작성 기준 -->

## 코딩 원칙 (항상 지킨다)
> Karpathy 가이드라인 기반. 아주 사소한 작업은 상식적으로 판단한다.

1. **코딩 전에 생각하기** — 가정은 명시하고, 불확실하면 묻는다. 해석이 여러 개면 혼자 고르지 말고 제시한다. 더 단순한 방법이 있으면 말한다. 헷갈리면 멈추고 무엇이 헷갈리는지 밝힌다.
2. **단순하게** — 요청한 것만 최소한의 코드로 만든다. 한 번만 쓰는 추상화, 요청하지 않은 확장성·설정, 일어날 수 없는 상황의 예외 처리는 넣지 않는다. 200줄을 50줄로 줄일 수 있으면 다시 쓴다.
3. **필요한 곳만 고치기** — 요청과 관계없는 코드·주석·포맷은 건드리지 않는다. 고장 나지 않은 건 리팩토링하지 않는다. 관계없는 죽은 코드는 지우지 말고 알려만 준다. 내 변경으로 안 쓰이게 된 import·변수·함수만 정리한다. 바뀐 모든 줄은 요청과 직접 연결되어야 한다.
4. **확인 가능한 목표** — 작업을 검증 가능한 목표로 바꾼다 (예: "버그 수정" → "재현 테스트 작성 후 통과"). 여러 단계 작업은 `단계 → 확인 방법` 형식으로 짧은 계획을 먼저 세운다.

## 작업 방식
- 기능 단위로 작업하고, 끝나면 테스트·린트·빌드로 확인한 뒤 결과를 보고한다.

## Backend

## 1. Java

### 1.1 명명 규칙 (Naming)

| **구분** | **규칙** | **예시** |
| --- | --- | --- |
| **패키지** | 소문자, 점으로 구분 (관례상 도메인 역순) | `com.ourcompany.servicename.user` |
| **클래스/인터페이스** | PascalCase, 명사형 | `OrderService`, `PaymentGateway` |
| **메서드** | camelCase, 동사·동사구 | `findById`, `cancelOrder` |
| **변수** | camelCase, 의미가 드러나게 | `orderCount`, `isPaid` |
| **상수** | 대문자 + `_` (SNAKE_CASE) | `MAX_PAGE_SIZE`, `DEFAULT_TIMEOUT_MS` |

> **Naming Check**
>
> - `tmp`, `str1` 등 **의미 없는 축약은 지양**합니다.
> - `boolean` 타입은 `is`, `has`, `can` 등으로 시작하여 의도를 명확히 합니다.

### 1.2 포맷 (Formatting)

- **들여쓰기:** 탭
- **중괄호:** K&R 스타일 (여는 `{`는 선언과 같은 줄에 작성)
- `if`, `for`, `while` 뒤 공백 및 연산자 양쪽 공백 유지

    ```jsx
    public void run() {
        if (isReady) {
            doWork();
        }
    }
    ```


### 1.3 주석 (Comments)

- **공개 API:** 다른 모듈/팀에서 사용하는 클래스 및 메서드는 `Javadoc` 권장 (`@param`, `@return`, `@throws`).
- **비즈니스 규칙:** 코드 옆에 짧고 명료하게 작성하며, *"어떻게"* 보다는 **"왜(Why) 이렇게 했는지"** 위주로 작성합니다.

### 1.4 기타 규칙

- **제네릭 사용:** 컬렉션과 반환값은 제네릭으로 타입을 명시합니다.
- **단일 책임:** 메서드가 길어지면 역할별로 분리합니다 (한 메서드 = 한 가지 책임).
- **예외 처리:** 예외를 삼키지(Swallow) 마세요. 로그나 메시지와 함께 처리하거나 상위로 전달합니다.

---

## 2. Spring

### 2.1 패키지 구조 (기본안)

팀 프로젝트는 **기능별**로 패키지를 나누고, 각 기능 안에서 아래 계층을 **기본**으로 맞춥니다. (필요 시 `support` 등 하위 패키지 추가)

```jsx
com.bogeum
├── domain
│   └── {기능}              ← user, property, rent, chat ...
│       ├── controller      ← HTTP 요청/응답 처리
│       ├── service         ← 비즈니스 로직 및 트랜잭션
│       ├── repository      ← DB / 외부 저장소 데이터 접근
│       ├── dto             ← request / response
│       └── model           ← 엔티티, 도메인 모델
├── common                  ← 공통 enums, exception, model
├── config                  ← Bean 등록, 보안(Security), 전역 설정
└── utils                   ← 유틸
```

### 2.2 계층별 역할

| **계층 (Layer)** | **해야 할 일 (Do)** | **하지 말아야 할 일 (Don't)** |
| --- | --- | --- |
| **Controller** | 요청 검증, 응답 매핑, 얇은 로직 유지 | 비즈니스 규칙 적용, 트랜잭션 본문 작성 |
| **Service** | 유스케이스 구현, 도메인 규칙 적용 | HTTP 상태(Status)나 Req/Res 객체 직접 의존 |
| **Repository** | 데이터 조회 및 저장 | 비즈니스 판단 및 로직 |

### 2.3 어노테이션

- **웹:** `@RestController` 사용, 메서드에 `@GetMapping` / `@PostMapping` 등 명시.
- **서비스:** `@Service` 사용, 트랜잭션 경계는 서비스 계층의 `@Transactional`로 모읍니다.
- **저장소:** `@Repository` 또는 `JpaRepository` 상속.
- **엔티티:** `@Entity`, `@Table`로 필드 매핑을 명확히 합니다.

### 2.4 의존성 주입 (DI)

> !!!!!!!!!!! 생성자 주입을 표준으로 합니다!!!!!!!!!!!
>
>
> *새 코드 작성 시 필드 주입(`@Autowired`)은 사용하지 않습니다.*
>

### 2.5 예외 및 API 응답

- **전역 처리:** `@ControllerAdvice` + `@ExceptionHandler` 활용.
- 클라이언트 응답은 **HTTP 상태 코드**와 **팀 표준 에러 응답 형식**(코드, 메시지, 필드 에러 등)을 일관되게 맞춥니다.

    ```jsx
    2) 기본 원칙
    - 모든 컨트롤러는 예외를 직접 `try-catch` 하지 않는다. (특수한 경우 제외)
    - 비즈니스 예외는 서비스 계층에서 `throw` 한다.
    - `@RestControllerAdvice` + `@ExceptionHandler`로 전역 처리한다.
    - 에러 응답은 공통 DTO(`ErrorResponse`)만 사용한다.
    - 상태코드는 `ErrorCode`의 `HttpStatus`를 사용한다.
    ```


---

## 3. Spring Boot

### 3.1 메인 클래스

- `Application` 클래스는 **최상위 패키지**에 위치시켜 하위 컴포넌트(`controller`, `service` 등)가 자동 스캔되도록 합니다.

### 3.2 설정 (Configuration)

- `application.yml`(또는 `.properties`)을 사용하고, **환경별 프로파일**(`dev`, `prod`, `test` 등)을 분리합니다.
- 설정 값이 많아지면 `@ConfigurationProperties`를 사용해 객체로 묶어 관리합니다.
- **보안:** 비밀번호, API 키 등은 **절대 코드에 하드코딩하지 않습니다** (환경 변수, 시크릿 매니저 등 팀 표준 방식 사용).

### 3.3 로깅 (Logging)

- 기본 `Logback`을 사용하며, 패키지별 로그 레벨은 환경별 `application-*.yml`에서 조정합니다.
- 운영 이슈 추적을 위해 **최소한의 컨텍스트**(예: Request ID, User ID)를 포함하는 팀 로깅 정책을 따릅니다.

---

### Entity(엔티티)

1. 엔티티 클래스 생성 시 @Setter 금지
    1. 엔티티 내부에 create메소드 둘 때 기준은 다른 도메인의 로직으로 생성되었는지 아닌지로 하겠습니다.

### Service (서비스)
1. Service는 인터페이스 없이 클래스로 직접 구현한다. (ServiceImpl 만들지 않는다)

2. 메소드 배치시 **반드시** **CRUD 순**으로 놓기 (팀원간의 원활한 코드 리뷰를 위해)
    1. CQRS 패턴 도입시 **반드시** **CUD**순으로 메소드 배치

### Repository (레포지토리)

1. 레포지토리 이름 명시 할 때 Repository와 같이 UserRepository 명시
   이유: Redis같은 NoSql 사용시 헷갈리지 않게 하기 위해

### DTO (Data Transfer Object, 디티오)

1.dto클래스 작명시 명사(기능별로) + Request라고 작명
(e.g. UserRequest)

response dto클래스 작명시 + 명사(기능별로) + response

(e.g. UserResponse)

### Controller (컨트롤러) ⇒ cqrs지만 1개의 컨트롤러로

1. 메소드 배치시 CRUD 순으로 놓기
2. 해당 메소드가 Dto에서 @NotBlank등의 어노테이션 사용시 컨트롤러 메소드에 @Vallid명시
3. service 메서드에서 path variable 변수명 동일하게 쓸 수 있도록 entity명 + id (camel case)

   → PathVariable(”userId”) Long userId


### Support 등 Util

```markdown
domain/booking/support/reservation/ReservationReader.java
```

### 예외처리

1. Exception은 enum값과 정적팩토리메서드를 한 파일에 놓아 직관적으로 볼 수 있다.

---

## Database

1. 필드명 `status` 말고 테이블명 + status 사용(e.g. item_status)
2. 엔티티명은 단수형
3. 시간은 TIMESTAMP와 작명시 `_at` 으로 통일 (_DATE 금지🚫)
4. PK의 작명은 `table_id` 와 같이, 자료형은 BIGINT(20)