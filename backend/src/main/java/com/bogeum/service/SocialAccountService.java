package com.bogeum.service;

import java.util.List;
import com.bogeum.dto.SocialAccountResponseDTO;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.bogeum.dto.SocialJoinDTO;
import com.bogeum.security.dto.MemberSecurityDTO;
import com.bogeum.security.oauth.PendingSocialUser;
import com.bogeum.security.oauth.SocialProfile;

public interface SocialAccountService {
	OAuth2User loadUser(SocialProfile profile);
	MemberSecurityDTO register(PendingSocialUser pending, SocialJoinDTO request);
	MemberSecurityDTO link(PendingSocialUser pending, Long memberId);
	void requireLinkAvailable(Long memberId, String provider);
	List<SocialAccountResponseDTO> linkedAccounts(Long memberId);
}
