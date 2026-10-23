package com.bogeum.service;

import java.util.UUID;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bogeum.domain.*;
import com.bogeum.dto.SocialJoinDTO;
import com.bogeum.dto.SocialAccountResponseDTO;
import com.bogeum.dto.VerifiedIdentityDTO;
import com.bogeum.exception.ApiException;
import com.bogeum.exception.ErrorCode;
import com.bogeum.repository.*;
import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.security.oauth.PendingSocialUser;
import com.bogeum.security.oauth.SocialProfile;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAccountServiceImpl implements SocialAccountService {
	private final SocialAccountRepository socialAccountRepository;
	private final MemberRepository memberRepository;
	private final MemberProfileRepository profileRepository;
	private final IdentityVerificationService identityVerificationService;
	private final ConsentService consentService;
	private final PasswordEncoder passwordEncoder;

	@Override
	public OAuth2User loadUser(SocialProfile profile) {
		return socialAccountRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
			.<OAuth2User>map(account -> principal(account.getMember()))
			.orElseGet(() -> new PendingSocialUser(profile));
	}

	@Override
	@Transactional
	public MemberSecurityDTO register(PendingSocialUser pending, SocialJoinDTO request) {
		pending.requireValid();
		consentService.requireRegistrationAvailable();
		requireUnlinked(pending.profile());
		if (memberRepository.existsByEmail(request.getEmail())) {
			// 소셜 제공자의 이메일 일치만으로 기존 계정을 연결하지 않는다.
			throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		VerifiedIdentityDTO identity = identityVerificationService.consume(
			request.getIdentityVerificationToken(), request.getEmail());
		// 사용자에게 발급하지 않는 임의 비밀번호 해시. 제공자 ID나 이메일을 비밀번호로 쓰지 않는다.
		Member member = memberRepository.saveAndFlush(new Member(request.getEmail(),
			passwordEncoder.encode(UUID.randomUUID().toString()), MemberType.GENERAL));
		profileRepository.save(new MemberProfile(member, identity.getName(), identity.getPhoneNumber()));
		consentService.saveRequiredConsents(member, request.getConsents());
		socialAccountRepository.saveAndFlush(new SocialAccount(member,
			pending.profile().provider(), pending.profile().providerUserId()));
		return principal(member);
	}

	@Override
	@Transactional
	public MemberSecurityDTO link(PendingSocialUser pending, Long memberId) {
		pending.requireValid();
		requireUnlinked(pending.profile());
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
		MemberSecurityDTO principal = principal(member);
		if (socialAccountRepository.existsByMemberIdAndProvider(memberId, pending.profile().provider())) {
			throw new ApiException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
		}
		socialAccountRepository.saveAndFlush(new SocialAccount(member,
			pending.profile().provider(), pending.profile().providerUserId()));
		return principal;
	}

	private void requireUnlinked(SocialProfile profile) {
		if (socialAccountRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId()).isPresent()) {
			throw new ApiException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
		}
	}

	@Override
	public void requireLinkAvailable(Long memberId, String provider) {
		if (socialAccountRepository.existsByMemberIdAndProvider(memberId, provider)) {
			throw new ApiException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
		}
	}

	@Override
	public List<SocialAccountResponseDTO> linkedAccounts(Long memberId) {
		principal(memberRepository.findById(memberId)
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS)));
		return socialAccountRepository.findAllByMemberIdOrderByProviderAsc(memberId).stream()
			.map(account -> new SocialAccountResponseDTO(account.getProvider(), account.getCreatedAt())).toList();
	}

	private MemberSecurityDTO principal(Member member) {
		if (member.getStatus() != AccountStatus.ACTIVE) {
			throw new OAuth2AuthenticationException("account_unavailable");
		}
		MemberSecurityDTO principal = new MemberSecurityDTO(member);
		principal.eraseCredentials();
		return principal;
	}
}
