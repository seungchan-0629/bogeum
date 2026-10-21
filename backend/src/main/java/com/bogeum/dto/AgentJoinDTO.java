package com.bogeum.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 중개사 가입 입력값. @Valid가 계정·사무소·개별 동의 항목의 내부 필드까지 검증하도록 연결한다.
 */
@Getter
@Setter
public class AgentJoinDTO {

	@Valid
	@NotNull(message = "계정 정보를 입력해 주세요.")
	private AccountJoinDTO account;

	@NotBlank(message = "휴대폰 번호를 입력해 주세요.")
	@Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
	private String phoneNumber;

	@Valid
	@NotNull(message = "중개사무소 정보를 입력해 주세요.")
	private OfficeDTO office;

	@Valid
	@NotEmpty(message = "필수 동의가 필요합니다.")
	@Size(max = 2)
	private List<@NotNull ConsentAgreementDTO> consents;
}
