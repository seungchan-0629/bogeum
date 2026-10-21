package com.bogeum.dto;

import com.bogeum.domain.ConsentType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 가입 화면에 표시할 약관 한 항목. 클라이언트는 동의 시 이 항목의 type과 version을 돌려보낸다.
 */
@Getter
@AllArgsConstructor
public class TermDTO {

	private final ConsentType type;
	private final String title;
	private final String version;
	private final boolean required;
	private final String description;
}
