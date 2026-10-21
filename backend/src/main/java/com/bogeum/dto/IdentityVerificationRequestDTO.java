package com.bogeum.dto;

import com.bogeum.security.EmailNormalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 개발용 인증 토큰 발급 입력값. 입력한 이름·전화번호를 모의 인증 정보로 저장하며 실제 본인 확인은 수행하지 않는다.
 */
@Getter
@Setter
public class IdentityVerificationRequestDTO {

	@NotBlank
	@Email
	@Size(max = 254)
	private String email;

	@NotBlank(message = "이름을 입력해 주세요.")
	@Size(max = 50)
	private String name;

	@NotBlank(message = "휴대폰 번호를 입력해 주세요.")
	@Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
	private String phoneNumber;

	public void setEmail(String email) {
		this.email = EmailNormalizer.normalize(email);
	}
}
