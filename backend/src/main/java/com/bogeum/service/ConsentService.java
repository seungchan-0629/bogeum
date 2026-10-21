package com.bogeum.service;

import java.util.List;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.ConsentAgreementDTO;
import com.bogeum.dto.TermsResponseDTO;

/**
 * 가입 가능 여부 확인, 약관 조회, 가입 시 필수 동의 기록을 담당하는 서비스 계약.
 */
public interface ConsentService {

	void requireRegistrationAvailable();

	TermsResponseDTO getTerms(MemberType memberType);

	void saveRequiredConsents(Member member, List<ConsentAgreementDTO> agreements);
}
