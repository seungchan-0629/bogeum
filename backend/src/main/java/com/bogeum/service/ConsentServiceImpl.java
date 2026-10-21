package com.bogeum.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.config.RegistrationProperties;
import com.bogeum.domain.ConsentRecord;
import com.bogeum.domain.ConsentType;
import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.ConsentAgreementDTO;
import com.bogeum.dto.TermDTO;
import com.bogeum.dto.TermsResponseDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.repository.ConsentRecordRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 유형별 약관을 제공하고, 모든 필수 항목에 최신 버전으로 동의했는지 검증해 기록한다.
 */
@Service
@RequiredArgsConstructor
public class ConsentServiceImpl implements ConsentService {

	private final RegistrationProperties properties;
	private final Environment environment;
	private final ConsentRecordRepository consentRecordRepository;

	@Override
	public void requireRegistrationAvailable() {
		// 현재 약관은 개발용 초안이므로 설정만 켜서 운영 가입을 우회할 수 없게 한다.
		if (!isRegistrationAvailable()) {
			throw new ApiException(ErrorCode.REGISTRATION_UNAVAILABLE);
		}
	}

	@Override
	public TermsResponseDTO getTerms(MemberType memberType) {
		if (memberType != MemberType.GENERAL && memberType != MemberType.AGENT) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
				Map.of("memberType", "GENERAL 또는 AGENT를 선택해 주세요."));
		}
		String version = termsVersion(memberType);
		String purpose = memberType == MemberType.GENERAL
			? "거래 상대방에게 위험 정보를 안내하기 위한 개인정보 제공"
			: "중개 업무와 클레임 처리를 위한 중개사 정보 제공";
		return new TermsResponseDTO(memberType, isRegistrationAvailable(), true, List.of(
			new TermDTO(ConsentType.PRIVACY_COLLECTION, "개인정보 수집·이용 동의", version, true,
				"개발용 초안입니다. 운영 전 수집 항목, 목적, 보유 기간 등을 확정해야 합니다."),
			new TermDTO(ConsentType.THIRD_PARTY_PROVISION, "개인정보 제3자 제공 동의", version, true,
				"개발용 초안: " + purpose + ". 제공받는 자, 항목, 기간, 거부 시 불이익 등은 운영 전 확정해야 합니다.")));
	}

	@Override
	// 회원가입 트랜잭션에 반드시 참여해 동의만 저장되거나 계정만 남는 부분 저장을 방지한다.
	@Transactional(propagation = Propagation.MANDATORY)
	public void saveRequiredConsents(Member member, List<ConsentAgreementDTO> agreements) {
		// 항목 수뿐 아니라 중복·미동의도 검사해 모든 필수 종류가 정확히 한 번씩 포함되도록 한다.
		Map<ConsentType, ConsentAgreementDTO> selected = new EnumMap<>(ConsentType.class);
		if (agreements == null || agreements.size() != ConsentType.values().length) {
			throw new ApiException(ErrorCode.CONSENT_REQUIRED);
		}
		for (ConsentAgreementDTO agreement : agreements) {
			if (agreement == null || agreement.getType() == null || !agreement.isAgreed()
					|| selected.put(agreement.getType(), agreement) != null) {
				throw new ApiException(ErrorCode.CONSENT_REQUIRED);
			}
			// 화면에서 약관을 조회한 뒤 서버 버전이 바뀌었다면, 최신 약관을 다시 확인하도록 거절한다.
			if (!termsVersion(member.getMemberType()).equals(agreement.getVersion())) {
				throw new ApiException(ErrorCode.CONSENT_VERSION_MISMATCH);
			}
		}
		consentRecordRepository.saveAll(selected.values().stream()
			.map(agreement -> new ConsentRecord(member, agreement.getType(), agreement.getVersion())).toList());
	}

	private boolean isRegistrationAvailable() {
		// enabled 설정과 dev/test 프로필이 모두 충족되어야 한다. 설정만으로 운영 가입을 열 수 없다.
		return properties.isEnabled() && environment.matchesProfiles("dev", "test");
	}

	private String termsVersion(MemberType memberType) {
		return memberType == MemberType.AGENT
			? properties.getAgentTermsVersion() : properties.getGeneralTermsVersion();
	}
}
