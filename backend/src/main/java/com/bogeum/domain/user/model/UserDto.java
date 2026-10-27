package com.bogeum.domain.user.model;

import com.bogeum.common.enums.LoginProvider;
import com.bogeum.common.enums.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 기능의 요청·응답 DTO.
 */
public class UserDto {

	/** 임대인·임차인의 일반 회원가입 요청. 본인 인증 결과는 받지 않는다. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SignupRequest {

		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식으로 입력해 주세요.")
		@Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
		private String email;

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		private String password;

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
		private String name;

		@NotBlank(message = "전화번호를 입력해 주세요.")
		@Size(max = 20, message = "전화번호는 20자 이하로 입력해 주세요.")
		private String phone;

		@Valid
		@NotNull(message = "필수 항목에 동의해 주세요.")
		private AgreementsRequest agreements;
	}

	/** 가입 시 각각 확인하는 두 필수 개인정보 동의 항목. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AgreementsRequest {

		@NotNull(message = "개인정보 수집·이용에 동의해 주세요.")
		@AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
		private Boolean privacyCollect;

		@NotNull(message = "개인정보 제3자 제공에 동의해 주세요.")
		@AssertTrue(message = "개인정보 제3자 제공에 동의해 주세요.")
		private Boolean thirdPartyProvide;
	}

	/** 가입 완료 후 반환할 사용자 식별자. */
	@Getter
	@Builder
	public static class SignupResponse {

		private Long userId;

		/** 저장된 사용자에서 가입 응답에 필요한 값만 가져온다. */
		public static SignupResponse from(User user) {
			return SignupResponse.builder().userId(user.getUserId()).build();
		}
	}

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
		//계약목록(임시)
		private String[] contractRoles;

		/**
		 * 사용자 엔티티를 로그인 응답으로 변환한다.
		 */
		public static LoginRes from(User user) {
			return LoginRes.builder()
				.userId(user.getUserId())
				.name(user.getName())
				.role(user.getRole())
				.contractRoles(new String[]{})
				.lastLoginProvider(user.getLastLoginProvider())
				.build();
		}
	}
}
