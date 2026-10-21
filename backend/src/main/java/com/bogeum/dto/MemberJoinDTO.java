package com.bogeum.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 일반 회원 가입 입력값. 이름·전화번호는 요청으로 직접 받지 않고 인증 토큰에 연결된 정보에서 가져온다.
 */
@Getter
@Setter
public class MemberJoinDTO {

	@Valid
	@NotNull(message = "계정 정보를 입력해 주세요.")
	private AccountJoinDTO account;

	@NotBlank(message = "본인 인증 증빙이 필요합니다.")
	@Size(max = 200)
	private String identityVerificationToken;

	@Valid
	@NotEmpty(message = "필수 동의가 필요합니다.")
	@Size(max = 2)
	private List<@NotNull ConsentAgreementDTO> consents;
}
