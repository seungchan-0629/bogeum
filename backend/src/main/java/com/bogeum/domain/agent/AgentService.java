package com.bogeum.domain.agent;

import java.time.LocalDateTime;
import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.UserException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentService {
	private final UserService userService;
	private final AgentRepository agentRepository;

	/** 계정·필수 동의·중개사 프로필 중 하나라도 실패하면 전체 가입을 롤백한다. */
	@Transactional
	public Agent signup(String email, String password, String representativeName, String phone,
			boolean privacyCollect, boolean thirdPartyProvide,
			@NotBlank @Size(max = 100) String officeName,
			@NotBlank @Size(max = 255) String officeAddress) {
		var user = userService.signupAgent(email, password, representativeName, phone,
			privacyCollect, thirdPartyProvide);
		var agent = Agent.builder().user(user).officeName(officeName).officeAddress(officeAddress)
			.documentDueAt(LocalDateTime.now().plusDays(3)).build();
		try {
			return agentRepository.saveAndFlush(agent);
		} catch (DataIntegrityViolationException exception) {
			throw UserException.signupConflict(exception);
		}
	}

	@Transactional(readOnly = true)
	public Agent findByUserId(Long userId) {
		return agentRepository.findByUser_UserId(userId).orElseThrow(AgentException::notFound);
	}

	/** 승인 변경을 기존 로그인 세션에도 즉시 반영하기 위해 DB에서 확인한다. */
	@Transactional(readOnly = true)
	public boolean isApproved(Long userId) {
		return agentRepository.existsByUser_UserIdAndApprovalStatus(userId, ApprovalStatus.APPROVED);
	}
}
