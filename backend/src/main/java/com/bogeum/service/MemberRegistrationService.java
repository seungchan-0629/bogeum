package com.bogeum.service;

import com.bogeum.dto.MemberJoinDTO;
import com.bogeum.dto.MemberResponseDTO;

/**
 * 일반 회원 가입 요청을 받아 계정·본인 인증·프로필·동의 저장을 조율하는 서비스 계약.
 */
public interface MemberRegistrationService {

	MemberResponseDTO register(MemberJoinDTO request);
}
