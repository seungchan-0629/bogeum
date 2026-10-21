package com.bogeum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberProfile;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.MemberJoinDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.dto.VerifiedIdentityDTO;
import com.bogeum.repository.MemberProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * 일반 회원가입의 전체 순서를 조율한다.
 * 계정 생성 → 인증 토큰 소비 → 인증된 정보로 프로필 저장 → 필수 동의 저장 → 회원 응답 반환.
 */
@Service
@RequiredArgsConstructor
public class MemberRegistrationServiceImpl implements MemberRegistrationService {

	private final MemberService memberService;
	private final MemberProfileRepository profileRepository;
	private final ConsentService consentService;
	private final IdentityVerificationService identityVerificationService;

	@Override
	// 아래 과정 중 예외가 발생하면 계정·토큰 소비·프로필·동의 변경이 모두 롤백된다.
	@Transactional
	public MemberResponseDTO register(MemberJoinDTO request) {
		// 1. 현재 환경에서 가입을 허용하는지 확인하고 공통 계정을 생성한다.
		consentService.requireRegistrationAvailable();
		Member member = memberService.createAccount(request.getAccount(), MemberType.GENERAL);
		// 2. 이 이메일에 발급된 미사용·미만료 토큰을 소비하고, 저장된 인증 정보를 가져온다.
		VerifiedIdentityDTO identity = identityVerificationService.consume(
			request.getIdentityVerificationToken(), member.getEmail());
		// 3. 이름과 전화번호는 인증 결과를 사용한다. 주거 역할은 계약마다 정한다.
		profileRepository.save(new MemberProfile(member, identity.getName(), identity.getPhoneNumber()));
		// 4. 최신 필수 약관의 동의를 검증·저장한 뒤 공개할 회원 정보만 응답 DTO로 만든다.
		consentService.saveRequiredConsents(member, request.getConsents());
		return MemberResponseDTO.from(member);
	}
}
