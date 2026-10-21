package com.bogeum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 중개사 가입에 포함되는 사무소 입력값. 등록번호와 대표자명은 선택 항목이다.
 */
@Getter
@Setter
public class OfficeDTO {

	@NotBlank(message = "중개사무소 이름을 입력해 주세요.")
	@Size(max = 100)
	private String name;

	@NotBlank(message = "중개사무소 주소를 입력해 주세요.")
	@Size(max = 300)
	private String address;

	@NotBlank(message = "중개사무소 전화번호를 입력해 주세요.")
	@Pattern(regexp = "^[0-9]{2,4}-?[0-9]{3,4}-?[0-9]{4}$", message = "전화번호 형식이 올바르지 않습니다.")
	private String phoneNumber;

	@Size(max = 50)
	private String registrationNumber;

	@Size(max = 50)
	private String representativeName;
}
