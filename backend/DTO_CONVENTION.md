# Backend DTO 작성 규칙

## 기본 구조

- 기능별로 `{도메인명}Dto` 파일 하나에 관련 요청·응답 DTO를 모은다.
- 파일은 `com.bogeum.domain.{기능}.model` 패키지에 둔다.
- 요청·응답은 외부 DTO 클래스 안에 `public static class`로 선언한다.
- `record`를 사용하거나 요청·응답을 각각 별도 파일로 분리하지 않는다.
- 이 문서는 DTO 위치·구조·이름에 관해 기존의 `dto.request` / `dto.response` 분리 규칙 대신 적용한다. 그 외 백엔드 코드 규칙은 유지한다.

```text
domain/user/model/
├─ User.java
└─ UserDto.java
   ├─ LoginReq
   └─ LoginRes
```

## 이름과 Lombok

| 구분 | 이름 | 어노테이션 | 사용 예시 |
|---|---|---|---|
| 요청 | `{기능}Req` | `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor` | `UserDto.LoginReq` |
| 응답 | `{기능}Res` | `@Getter`, `@Builder` | `UserDto.LoginRes` |

- 등록·목록 등 기능에 따라 `RegReq`, `RegRes`, `ListRes`처럼 구분한다.
- 필드는 `private`으로 선언한다.
- 요청값은 `request.getEmail()`처럼 getter로 읽는다.
- 필요한 DTO만 추가하고, 사용하지 않는 요청·응답 클래스를 미리 만들지 않는다.

## 검증과 변환 메서드

- 요청 필드에 `@NotBlank`, `@Email` 등 용도에 맞는 검증을 선언하고 한국어 오류 메시지를 작성한다.
- 해당 요청을 받는 컨트롤러에서는 `@Valid`로 검증을 실행한다.
- 응답에는 `public static {응답타입} from({엔티티타입} entity)` 메서드를 두고, builder로 필요한 필드만 매핑한다.
- 요청으로 새 엔티티를 만드는 경우에만 `toEntity()`를 둔다. 로그인처럼 기존 사용자를 조회·검증하는 요청에는 추가하지 않는다.
- 변환 메서드는 데이터 매핑만 담당한다. DB 조회, 비밀번호 검증, 계정 상태 판단, 로그인 수단 갱신 등은 서비스·도메인 로직에서 처리한다.
- 비밀번호 해시 등 민감한 값은 응답에 포함하지 않는다.

## 주석과 포맷

- 외부 DTO 클래스, 내부 요청·응답 클래스, 변환 메서드에 역할을 설명하는 한국어 Javadoc을 작성한다.
- 필드 의미나 주의점이 필요한 곳에는 짧은 주석을 단다.
- Java 코드는 탭으로 들여쓴다.
- 현재 프로젝트에는 Swagger 의존성이 없으므로 `@Schema`를 추가하지 않는다. 예시 코드의 Swagger 어노테이션을 그대로 복사하지 않는다.

## 로그인 DTO 예시

```java
package com.bogeum.domain.user.model;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 기능의 요청·응답 DTO.
 */
public class UserDto {

	/**
	 * 이메일과 비밀번호를 이용한 로그인 요청.
	 */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LoginReq {

		// User.email에 저장된 로그인 아이디
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식으로 입력해 주세요.")
		private String email;

		// 서버에서 User.passwordHash와 검증할 입력 비밀번호
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		private String password;
	}

	/**
	 * 로그인 성공 후 반환할 사용자 정보.
	 * 비밀번호 해시는 응답에 포함하지 않는다.
	 */
	@Getter
	@Builder
	public static class LoginRes {

		private Long userId;
		private String name;
		private Role role;
		private LoginProvider lastLoginProvider;

		/**
		 * 사용자 엔티티를 로그인 응답으로 변환한다.
		 */
		public static LoginRes from(User user) {
			return LoginRes.builder()
				.userId(user.getUserId())
				.name(user.getName())
				.role(user.getRole())
				.lastLoginProvider(user.getLastLoginProvider())
				.build();
		}
	}
}
```

## 작성 후 확인

- 요청·응답 이름이 `Req` / `Res`로 끝나는지 확인한다.
- DTO가 해당 도메인의 외부 DTO 클래스 안에 `public static class`로 선언되었는지 확인한다.
- 입력 검증과 응답 필드가 실제 기능에 필요한 범위인지 확인한다.
- `from()`과 필요한 경우의 `toEntity()`에 비즈니스 로직이 섞이지 않았는지 확인한다.
- DTO 코드를 변경한 뒤 `backend` 폴더에서 `./gradlew compileJava`로 컴파일을 확인한다. Windows PowerShell에서는 `.\gradlew.bat compileJava`를 사용한다.
