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
