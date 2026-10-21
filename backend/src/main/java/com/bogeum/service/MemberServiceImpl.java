package com.bogeum.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.bogeum.domain.AccountStatus;
import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.AccountJoinDTO;
import com.bogeum.dto.MemberResponseDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.repository.AgentProfileRepository;
import com.bogeum.repository.MemberRepository;
import com.bogeum.security.EmailNormalizer;
import com.bogeum.security.PasswordPolicy;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/**
 * 일반 회원과 중개사가 공유하는 계정 검증·저장 및 로그인 회원 조회를 담당한다.
 * 기본은 읽기 전용이며, 계정 생성은 상위 가입 서비스의 쓰기 트랜잭션에 참여한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

	private final MemberRepository memberRepository;
	private final AgentProfileRepository agentProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;

	@Override
	// MANDATORY는 기존 트랜잭션이 없으면 실행을 거절해 계정만 단독으로 생성되는 것을 막는다.
	@Transactional(propagation = Propagation.MANDATORY)
	public Member createAccount(AccountJoinDTO account, MemberType memberType) {
		// 컨트롤러를 거치지 않고 호출되어도 공통 계정 입력 규칙을 적용한다.
		Map<String, String> errors = new LinkedHashMap<>();
		validator.validate(account).forEach(violation -> errors.putIfAbsent(
			"account." + violation.getPropertyPath(), violation.getMessage()));
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, errors);
		}
		PasswordPolicy.validateForRegistration(account.getPassword(), account.getPasswordConfirm());
		// 정규화한 이메일로 중복을 검사한다. 동시 가입 경합은 DB의 유일성 제약이 최종 방어한다.
		String email = EmailNormalizer.normalize(account.getEmail());
		if (memberRepository.existsByEmail(email)) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		// 상위 가입 서비스의 트랜잭션에 참여해 프로필·동의 저장 실패 시 함께 취소한다.
		// flush는 SQL을 DB에 반영하지만 커밋은 아니다. 평문 비밀번호는 BCrypt 해시로 바꿔 저장한다.
		return memberRepository.saveAndFlush(new Member(email,
			passwordEncoder.encode(account.getPassword()), memberType));
	}

	@Override
	public MemberResponseDTO getCurrentMember(Long memberId) {
		// 세션에 회원 ID가 남아 있어도 정지·탈퇴한 계정은 거절하고, 프로필이 있으면 중개사 정보를 덧붙인다.
		return memberRepository.findById(memberId)
			.filter(member -> member.getStatus() == AccountStatus.ACTIVE)
			.map(member -> MemberResponseDTO.from(member,
				agentProfileRepository.findById(memberId).orElse(null)))
			.orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
	}
}
