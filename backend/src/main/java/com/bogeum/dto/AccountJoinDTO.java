package com.bogeum.dto;

import com.bogeum.security.EmailNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 일반 회원·중개사 가입에서 공유하는 계정 입력값. 비밀번호 일치 여부와 바이트 길이는 서비스에서 추가 검증한다.
 */
@Getter
@Setter
public class AccountJoinDTO {

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 72, message = "비밀번호는 8자 이상, 72바이트 이하여야 합니다.")
	private String password;

	@NotBlank(message = "비밀번호 확인을 입력해 주세요.")
	@Size(max = 72, message = "비밀번호 확인은 72바이트 이하여야 합니다.")
	private String passwordConfirm;

	public void setEmail(String email) {
		this.email = EmailNormalizer.normalize(email);
	}
}
