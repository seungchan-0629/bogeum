package com.bogeum.service;

import com.bogeum.dto.AgentJoinDTO;
import com.bogeum.dto.MemberResponseDTO;

/**
 * 중개사 가입 요청을 받아 계정·사무소 프로필·필수 동의를 함께 생성하는 서비스 계약.
 */
public interface AgentRegistrationService {

	MemberResponseDTO register(AgentJoinDTO request);
}
