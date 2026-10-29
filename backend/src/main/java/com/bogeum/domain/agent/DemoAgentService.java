package com.bogeum.domain.agent;

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.domain.agent.model.Agent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실습본에서만 사용하는 본인 승인 기능. 운영 관리자 심사와 분리한다. */
@Service
@Profile("demo & !prod")
@RequiredArgsConstructor
public class DemoAgentService {
	private final AgentRepository agentRepository;

	@Transactional
	public Agent approve(Long userId) {
		Agent agent = agentRepository.findByUser_UserId(userId).orElseThrow(AgentException::notFound);
		if (agent.getApprovalStatus() != ApprovalStatus.APPROVED) {
			agent.approveForDemo();
		}
		return agent;
	}
}
