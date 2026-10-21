package com.bogeum.dto;

import com.bogeum.domain.ConsentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 사용자가 동의한 약관의 종류·버전·동의 여부. 필수 동의 및 버전 일치는 ConsentService에서 확인한다.
 */
@Getter
@Setter
public class ConsentAgreementDTO {

	@NotNull(message = "동의 항목을 선택해 주세요.")
	private ConsentType type;

	@NotBlank(message = "약관 버전이 필요합니다.")
	@Size(max = 50)
	private String version;

	private boolean agreed;
}
