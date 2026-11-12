package com.bogeum.domain.agent;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.bogeum.domain.agent.model.AgentDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminAgentController {

	private final AgentService agentService;

	/** 관리자용 중개사 목록. 서류 열람과 승인 처리는 별도 기능이다 (AGENT_001). */
	@GetMapping("/admin/agents")
	public AgentDto.ListRes readAgents(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "1") int page) {
		return AgentDto.ListRes.from(agentService.readAgents(keyword, page));
	}
}
