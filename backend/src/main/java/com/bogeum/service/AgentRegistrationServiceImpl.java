package com.bogeum.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.config.RegistrationProperties;
import com.bogeum.domain.AgentProfile;
import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.AgentJoinDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.dto.OfficeDTO;
import com.bogeum.repository.AgentProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * 중개사 가입의 전체 순서를 조율한다.
 * 계정 생성 → 사무소 프로필 및 서류 제출 기한 저장 → 필수 동의 저장 → 회원 응답 반환.
 */
@Service
@RequiredArgsConstructor
public class AgentRegistrationServiceImpl implements AgentRegistrationService {

	private final MemberService memberService;
	private final AgentProfileRepository profileRepository;
	private final ConsentService consentService;
	private final RegistrationProperties properties;

	@Override
	// 사무소 프로필이나 동의 저장이 실패하면 앞서 생성한 계정도 함께 롤백된다.
	@Transactional
	public MemberResponseDTO register(AgentJoinDTO request) {
		// 1. 가입 가능 여부를 확인하고, 서버에서 회원 유형을 AGENT로 지정해 계정을 생성한다.
		consentService.requireRegistrationAvailable();
		Member member = memberService.createAccount(request.getAccount(), MemberType.AGENT);
		// 2. 일반 회원의 토큰 소비 과정 없이 사무소 정보를 저장하고, 가입 시점부터 제출 기한을 계산한다.
		OfficeDTO office = request.getOffice();
		AgentProfile profile = profileRepository.save(AgentProfile.builder()
			.member(member).phoneNumber(request.getPhoneNumber())
			.officeName(office.getName().strip()).officeAddress(office.getAddress().strip())
			.officePhoneNumber(office.getPhoneNumber())
			.officeRegistrationNumber(office.getRegistrationNumber())
			.representativeName(office.getRepresentativeName())
			.documentSubmissionDeadline(Instant.now().plus(properties.getDocumentSubmissionDays(), ChronoUnit.DAYS))
			.build());
		// 3. 중개사용 최신 필수 약관 동의를 저장하고 서류 심사 상태를 포함해 응답한다.
		consentService.saveRequiredConsents(member, request.getConsents());
		return MemberResponseDTO.from(member, profile);
	}
}
