package com.bogeum.dto;

import java.util.List;

import com.bogeum.domain.MemberType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원 유형별 약관 목록, 가입 허용 여부, 초안 여부를 함께 전달하는 응답.
 */
@Getter
@AllArgsConstructor
public class TermsResponseDTO {

	private final MemberType memberType;
	private final boolean registrationAvailable;
	private final boolean draft;
	private final List<TermDTO> terms;
}
