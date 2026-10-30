package com.bogeum.domain.agent;

import com.bogeum.domain.agent.model.AgentDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo & !prod")
@RequiredArgsConstructor
public class DemoAgentController {
	private final DemoAgentService demoAgentService;

	/** 대상 ID는 요청으로 받지 않고 인증된 본인 계정만 승인한다. */
	@PostMapping("/api/v1/agents/me/demo-approval")
	public AgentDto.ProfileResponse approve(@AuthenticationPrincipal LoginUser loginUser) {
		return AgentDto.ProfileResponse.from(demoAgentService.approve(loginUser.getUserId()), true);
	}
}
