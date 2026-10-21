package com.bogeum.service;

import com.bogeum.domain.Member;
import com.bogeum.domain.MemberType;
import com.bogeum.dto.AccountJoinDTO;
import com.bogeum.dto.MemberResponseDTO;

/**
 * 회원 유형에 공통인 계정 생성과 현재 회원 조회 기능을 제공한다.
 */
public interface MemberService {

	Member createAccount(AccountJoinDTO account, MemberType memberType);

	MemberResponseDTO getCurrentMember(Long memberId);
}
