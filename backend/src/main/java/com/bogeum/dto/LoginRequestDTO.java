package com.bogeum.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JSON 로그인 입력값. 컨트롤러의 @Valid 대신 JsonLoginFilter가 Validator를 직접 호출해 검증한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequestDTO {

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(max = 72, message = "비밀번호는 72바이트 이하여야 합니다.")
	private String password;
}
