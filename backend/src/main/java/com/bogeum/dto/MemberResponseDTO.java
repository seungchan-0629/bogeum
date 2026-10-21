package com.bogeum.dto;

import java.time.Instant;

import com.bogeum.domain.AgentProfile;
import com.bogeum.domain.AgentVerificationStatus;
import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 가입·로그인·현재 회원 조회가 공유하는 공개 회원 정보. 비밀번호 해시는 포함하지 않고 null인 중개사 정보는 JSON에서 생략한다.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberResponseDTO {

	private final Long id;
	private final String email;
	private final MemberType memberType;
	private final AgentVerificationStatus agentVerificationStatus;
	private final Instant documentSubmissionDeadline;

	public static MemberResponseDTO from(Member member) {
		return from(member, null);
	}

	public static MemberResponseDTO from(Member member, AgentProfile agentProfile) {
		return new MemberResponseDTO(member.getId(), member.getEmail(), member.getMemberType(),
			agentProfile == null ? null : agentProfile.getVerificationStatus(),
			agentProfile == null ? null : agentProfile.getDocumentSubmissionDeadline());
	}
}
