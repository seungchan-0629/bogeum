# 보금(Bogeum) AI 작업 규칙

이 문서는 다른 컴퓨터의 AI에게 보금 프로젝트의 개발 규칙을 전달하기 위한 독립 문서다. 작업 전에 읽고 적용한다. 별도 대화 이력이나 특정 컴퓨터의 경로를 전제로 하지 않는다.

기존 `AGENTS.md`와 `backend/DTO_CONVENTION.md`를 통합했다. DTO의 위치·구조·이름은 별도로 정리된 DTO 규칙을 적용한다. 기존 코드가 규칙과 다르더라도 요청과 관계없는 파일까지 일괄 수정하지 않는다.

## 1. 응답과 작업 원칙

- 모든 응답과 설명은 한국어로 한다.
- 사용자가 쉽게 설명해 달라고 하면 코딩을 전혀 모르는 사람도 이해할 수 있도록 쉬운 예시를 든다.
- 코딩 전에 가정을 명시한다. 해석이 여러 가지이거나 판단에 필요한 정보가 부족하면 무엇이 불확실한지 설명하고 확인한다.
- 더 단순한 해결 방법이 있으면 제시한다.
- 요청한 기능만 최소한의 코드로 구현한다. 한 번만 쓰는 추상화, 요청하지 않은 확장성·설정, 발생할 수 없는 상황을 위한 예외 처리를 추가하지 않는다.
- 요청과 관계없는 코드·주석·포맷을 변경하지 않는다. 정상 동작하는 코드를 임의로 리팩토링하지 않는다.
- 관계없는 죽은 코드는 삭제하지 않고 알려만 준다. 자신의 변경으로 사용하지 않게 된 import·변수·함수는 정리한다.
- 모든 변경은 사용자의 요청과 직접 연결되어야 한다.
- 여러 단계 작업은 `단계 → 확인 방법` 형식으로 짧은 계획을 먼저 제시한다.
- 기능 단위로 작업하고, 관련 테스트·린트·빌드로 확인한 뒤 변경 내용과 검증 결과를 보고한다. 실행하지 못한 검증은 이유를 함께 알린다.

## 2. 서비스 이해

| 항목 | 내용 |
|---|---|
| 서비스명 | 보금(Bogeum) |
| 이름의 뜻 | 보증금 + 보금자리. 보증금과 사는 집을 함께 지킨다는 뜻 |
| 한 줄 소개 | 집을 구할 때부터 나갈 때까지, 보증금과 월세를 기록으로 관리하는 부동산 서비스 |
| 목표 | 매물을 쉽게 찾고 전·월세 보증금과 보금자리를 안전하고 편리하게 관리한다 |
| 거래 종류 | 전세, 월세, 매매 |
| 서비스 형태 | 웹과 앱 |

| 사용자 | 역할 |
|---|---|
| 관리자 | 문의 답변, 매물 신고 처리, 등기상 소유자와 등록자가 다른 매물 검수 |
| 집주인 | 매물 등록, 계약 후 월세와 보증금 관리 |
| 세입자 | 매물 검색, 계약 후 월세 납부와 보증금 반환 관리 |
| 중개사 | 매물 중개 수락, 계약 관리 |

핵심 가치는 다음과 같다.

- **쉽게 찾는다:** 조건 검색, 관심 매물 저장, 집주인과 직접 채팅한다.
- **안전하게 확인한다:** 모든 매물에 등기부등본을 받고 확인된 위험 신호와 그 의미를 보여준다.
- **편리하게 관리한다:** 계약서 업로드부터 월세 납부, 기한 알림, 입주 기록, 퇴실 정산까지 연결한다.

## 3. 기술 구성과 실행

```text
bogeum/
├─ backend/   Spring Boot 4.1.1, Java 17, Gradle Wrapper
└─ frontend/  React 19, Vite, JavaScript
```

- 백엔드 패키지 루트: `com.bogeum`
- 기본 설정: `backend/src/main/resources/application.yml`
- DB / ORM: MySQL + Spring Data JPA, Lombok 사용
- 테스트 DB: H2 인메모리 DB
- 프론트엔드 들여쓰기: 스페이스 2칸
- 개발 중 Vite는 `/api` 요청을 `http://localhost:8080`으로 프록시한다. 이 개발 구성에서는 별도 CORS 설정이 필요하지 않다.
- 라이브러리 버전과 실행 스크립트는 실제 `build.gradle`, `package.json`도 확인한다.

명령은 각 폴더 안에서 실행한다.

| 위치 | 작업 | macOS / Linux | Windows PowerShell |
|---|---|---|---|
| `backend` | 서버 실행(8080) | `./gradlew bootRun` | `.\gradlew.bat bootRun` |
| `backend` | Java 컴파일 | `./gradlew compileJava` | `.\gradlew.bat compileJava` |
| `backend` | 테스트 | `./gradlew test` | `.\gradlew.bat test` |
| `frontend` | 개발 서버 실행(5173) | `npm run dev` | `npm run dev` |
| `frontend` | 린트(oxlint) | `npm run lint` | `npm run lint` |
| `frontend` | 빌드 | `npm run build` | `npm run build` |

## 4. Git 규칙

- 커밋은 사용자가 명시적으로 요청할 때만 한다.
- 기본 작업 범위는 변경 파일 정리와 커밋 메시지 제안까지다.
- **push와 PR 생성은 하지 않는다. 사용자가 직접 한다.**
- 커밋 메시지는 한국어로 작성한다.
- 커밋 메시지에 `Co-Authored-By`를 넣지 않는다.

## 5. 패키지와 계층

기능별로 `com.bogeum.domain.{기능}` 아래에 코드를 둔다. 엔티티와 해당 도메인의 DTO는 `model`에 둔다.

계층별 하위 패키지를 사용하는 기본안은 다음과 같다.

```text
com.bogeum
├─ domain
│  └─ {기능}
│     ├─ controller
│     ├─ service
│     ├─ repository
│     ├─ model
│     │  ├─ {엔티티}.java
│     │  └─ {도메인}Dto.java
│     └─ support           필요한 경우에만 추가
├─ common
│  ├─ enums
│  ├─ exception
│  └─ model
├─ config
└─ utils
```

현재 프로젝트에는 Controller·Service·Repository를 도메인 바로 아래에 둔 구조도 사용한다. 기존 기능을 수정할 때는 해당 기능의 배치를 따르고, 위 기본안에 맞추기 위한 파일 이동을 별도로 수행하지 않는다.

| 계층 | 담당 | 금지 |
|---|---|---|
| Controller | HTTP 요청 검증, 서비스 호출, 응답 매핑 | 비즈니스 규칙 적용, 트랜잭션 본문 작성 |
| Service | 유스케이스 구현, 도메인 규칙 적용, 트랜잭션 관리 | HTTP 상태나 API 요청·응답 DTO에 직접 의존 |
| Repository | DB 및 외부 저장소 조회·저장 | 비즈니스 판단과 로직 |

- 공통 코드는 `common`, Bean·보안·전역 설정은 `config`, 공통 유틸은 `utils`에 둔다.
- 기능에 속하는 보조 코드는 필요할 때 해당 도메인의 `support` 아래에 둔다.
- 보조 코드 경로 예: `domain/booking/support/reservation/ReservationReader.java`
- 애플리케이션 메인 클래스는 `com.bogeum` 최상위 패키지에 두어 하위 컴포넌트가 자동 스캔되도록 한다.

## 6. Java 공통 규칙

| 구분 | 규칙 | 예시 |
|---|---|---|
| 패키지 | 소문자, 점으로 구분 | `com.bogeum.domain.user` |
| 클래스·인터페이스 | PascalCase, 명사형 | `OrderService`, `PaymentGateway` |
| 메서드 | camelCase, 동사 또는 동사구 | `findById`, `cancelOrder` |
| 변수 | camelCase, 의미가 드러나는 이름 | `orderCount`, `isPaid` |
| 상수 | 대문자와 밑줄 | `MAX_PAGE_SIZE` |
| boolean | `is`, `has`, `can` 등으로 의도 표현 | `isReady`, `hasConsent`, `canCancel` |

- `tmp`, `str1`처럼 의미 없는 축약을 피한다.
- Java 들여쓰기는 탭을 사용한다.
- 여는 중괄호는 선언과 같은 줄에 놓는다(K&R 스타일).
- `if`, `for`, `while` 뒤와 연산자 양쪽에 공백을 둔다.
- 주변 코드 스타일을 따른다.
- 컬렉션과 반환값에 제네릭 타입을 명시한다.
- 메서드는 한 가지 책임을 갖도록 작성하고, 길어지면 역할별로 분리한다.
- 다른 모듈이나 팀에서 사용하는 공개 클래스·메서드에는 Javadoc을 권장한다. 필요에 따라 `@param`, `@return`, `@throws`를 작성한다.
- 비즈니스 규칙 주석은 구현 방법보다 그렇게 처리하는 이유를 짧고 명료하게 설명한다.
- 예외를 이유 없이 삼키지 않는다. 필요한 로그·메시지와 함께 처리하거나 상위로 전달한다.

## 7. Controller

- `@RestController`를 사용한다.
- 각 엔드포인트에 `@GetMapping`, `@PostMapping` 등 HTTP 매핑을 명시한다.
- API 경로는 `/api/`로 시작하는 것을 기본으로 하되 절대적인 강제 규칙은 아니다.
- 메서드는 **생성 → 조회 → 수정 → 삭제(CRUD)** 순서로 배치한다.
- CQRS를 적용해도 조회용·명령용이라는 이유만으로 Controller를 둘로 나누지 않는다.
- 요청 DTO에 `@NotBlank` 등 검증 어노테이션이 있으면 해당 요청 매개변수에 `@Valid`를 붙인다.
- 경로 변수는 `엔티티명 + Id`의 camelCase로 작성하고 Service에서도 같은 이름을 사용한다.
- 경로 변수 예: `@PathVariable("userId") Long userId`
- 요청 DTO에서 필요한 값을 꺼내 Service에 전달하고, Service 결과를 응답 DTO로 변환한다.
- 특수한 경우를 제외하고 Controller에서 예외를 직접 `try-catch` 하지 않는다.

## 8. Service와 의존성 주입

- `@Service`를 사용한다.
- Service는 별도 인터페이스 없이 클래스로 직접 구현한다. `ServiceImpl`을 만들지 않는다.
- 메서드는 **CRUD 순서**로 배치한다.
- CQRS를 도입해 명령 처리를 분리한 경우 명령 메서드는 **생성 → 수정 → 삭제(CUD)** 순서로 배치한다.
- 트랜잭션 경계는 Service 계층의 `@Transactional`로 모은다.
- 비즈니스 규칙 위반은 Service에서 예외를 던져 전역 처리기로 전달한다.
- **생성자 주입을 표준으로 사용한다.** 새 코드에 필드 주입용 `@Autowired`를 사용하지 않는다.

## 9. Repository

- `JpaRepository`를 상속하거나 필요한 저장소 구현에 `@Repository`를 사용한다.
- 이름에 `Repository`를 명시한다. 예: `UserRepository`
- 다른 저장소 기술과 혼동하지 않도록 저장소의 역할이 드러나는 이름을 사용한다.
- 조회와 저장을 담당하며 비즈니스 판단을 넣지 않는다.

## 10. Entity와 데이터베이스

- 엔티티는 해당 도메인의 `model`에 둔다.
- 엔티티 이름은 단수형으로 작성한다.
- `@Entity`, `@Table`을 사용해 매핑을 명확히 한다.
- **엔티티에 `@Setter`를 사용하지 않는다.**
- 엔티티 내부 `create()` 메서드의 배치 기준은 다른 도메인의 로직으로 생성되는지 여부다. 이 기준만으로 배치 방향이 불분명하면 기존 사례와 사용자 의도를 확인하고 결정한다.

| 대상 | 규칙 | 예시 |
|---|---|---|
| 상태 컬럼 | 단독 `status` 대신 `테이블명_status` | `user_status` |
| 상태 Java 필드 | 컬럼 의미에 대응하는 camelCase | `userStatus` |
| 시간 컬럼 | `TIMESTAMP`, 이름은 `_at`로 끝냄 | `created_at`, `updated_at` |
| 시간 이름 | `_date` 사용 금지 | `created_date` 사용 금지 |
| PK 컬럼 | `테이블명_id`, 자료형 `BIGINT(20)` | `user_id` |
| PK Java 필드 | 식별자에 맞는 이름과 `Long` 타입 | `Long userId` |

## 11. DTO

### 위치와 구조

- 기능별로 **`{도메인명}Dto.java` 파일 하나**에 관련 요청·응답 DTO를 모은다.
- 위치는 **`com.bogeum.domain.{기능}.model`**이다.
- 요청·응답은 외부 DTO 클래스 안에 **`public static class`**로 선언한다.
- 요청·응답 DTO에 `record`를 사용하지 않는다.
- 요청·응답을 각각 별도 파일로 분리하지 않는다.
- DTO 규칙은 기존 `dto.request` / `dto.response` 분리 방식과 `Request` / `Response` 접미사 규칙을 대체한다.

```text
domain/user/model/
├─ User.java
└─ UserDto.java
   ├─ LoginReq
   └─ LoginRes
```

### 이름과 Lombok

| 구분 | 이름 | 어노테이션 | 사용 예 |
|---|---|---|---|
| 외부 클래스 | `{도메인명}Dto` | 필요한 것만 사용 | `UserDto` |
| 요청 | `{기능}Req` | `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor` | `UserDto.LoginReq` |
| 응답 | `{기능}Res` | `@Getter`, `@Builder` | `UserDto.LoginRes` |

- 등록·목록 등 실제 기능에 맞게 `RegReq`, `RegRes`, `ListRes` 등으로 구분한다.
- 필드는 `private`으로 선언한다.
- 요청값은 `request.getEmail()`처럼 getter로 읽는다.
- 필요한 DTO만 추가한다. 사용하지 않는 요청·응답 클래스를 미리 만들지 않는다.

### 입력 검증과 변환

- 요청 필드에 `@NotBlank`, `@Email` 등 용도에 맞는 검증을 선언하고 한국어 오류 메시지를 작성한다.
- 해당 요청을 받는 Controller에서 `@Valid`로 검증을 실행한다.
- 엔티티를 응답으로 변환할 때는 `public static {응답타입} from({엔티티타입} entity)`를 두고 builder로 필요한 필드만 매핑한다.
- 요청으로 새 엔티티를 만드는 경우에만 `toEntity()`를 둔다. 기존 데이터를 조회·검증하는 요청에는 추가하지 않는다.
- 변환 메서드는 데이터 매핑만 담당한다.
- DB 조회, 비밀번호 검증, 계정 상태 판단, 로그인 수단 갱신 등의 비즈니스 로직을 DTO에 넣지 않는다.
- 비밀번호 해시 등 민감한 값은 응답에 포함하지 않는다.

### 주석과 확인

- 외부 DTO 클래스, 내부 요청·응답 클래스, 변환 메서드에 역할을 설명하는 한국어 Javadoc을 작성한다.
- 필드 의미나 주의점이 필요한 곳에 짧은 주석을 작성한다.
- Java 들여쓰기는 탭을 사용한다.
- 현재 프로젝트에는 Swagger 의존성이 없으므로 `@Schema`를 추가하지 않는다. 참고 예시의 어노테이션을 그대로 복사하지 않는다.
- DTO 변경 후 컴파일을 확인하고, 관련 동작을 바꿨으면 해당 테스트도 실행한다.

## 12. 예외와 API 오류 응답

- `@RestControllerAdvice`와 `@ExceptionHandler`로 예외를 전역 처리한다.
- 에러 응답은 공통 DTO인 `ErrorResponse`를 사용한다.
- HTTP 상태는 `ErrorCode`에 정의된 `HttpStatus`를 사용한다.
- 오류 코드·메시지·필드 오류 등은 팀의 공통 응답 형식에 맞춘다.
- 도메인 예외 파일에서 사용하는 enum 값과 정적 팩토리 메서드의 대응을 한눈에 볼 수 있도록 작성한다.
- 현재 공통 오류 enum은 `common.exception.ErrorCode`에 있다. 기존 오류 처리 구조를 확인하고 재사용한다.
- 예외 생성은 `UserException.loginFailed()` 같은 의미 있는 정적 팩토리 메서드로 표현한다.

## 13. 설정과 보안·로깅

- `application.yml` 또는 `.properties`를 사용하고 `dev`, `prod`, `test` 등 환경별 프로파일을 분리한다.
- `ddl-auto`는 개발 환경에서 `update`, 운영 환경에서 `validate`를 사용한다.
- DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로 받는다.
- 비밀번호와 API 키를 코드에 하드코딩하지 않는다. 환경 변수나 팀 표준 시크릿 관리 방식을 사용한다.
- 설정 값이 많아지면 `@ConfigurationProperties`로 묶는다.
- 기본 로깅은 Logback을 사용한다.
- 패키지별 로그 레벨은 환경별 `application-*.yml`에서 조정한다.
- 운영 이슈를 추적할 수 있도록 Request ID, User ID 등 필요한 최소한의 컨텍스트를 팀 로깅 정책에 맞춰 포함한다.
- 인증 방식, 프론트엔드 라우팅·상태관리·스타일링, 세부 테스트 작성 기준은 이 문서만으로 새 정책을 정하지 않는다. 작업 시 해당 구현과 별도 지침을 확인한다.

## 14. 작업 완료 확인

- 요청한 범위만 변경했는가?
- 기능별 패키지 배치와 주변 코드 스타일을 따랐는가?
- Controller·Service·Repository의 역할과 CRUD 배치를 지켰는가?
- 생성자 주입을 사용하고 엔티티에 `@Setter`를 추가하지 않았는가?
- DTO의 위치, 중첩 클래스 구조, `Req` / `Res` 이름과 Lombok 규칙을 지켰는가?
- 요청 검증과 `@Valid`, DTO 변환 책임을 확인했는가?
- 공통 예외 처리와 `ErrorResponse`, `ErrorCode`를 사용했는가?
- DB 컬럼과 식별자·시간 명명 규칙을 지켰는가?
- 민감한 정보를 코드나 응답에 노출하지 않았는가?
- 변경 범위에 맞는 테스트·린트·빌드를 확인하고 결과를 보고했는가?
- 명시적 요청 없이 커밋하거나 push·PR 생성을 하지 않았는가?
