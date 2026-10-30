package com.bogeum.domain.agent;

import com.bogeum.domain.agent.model.AgentDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AgentController {
	private final AgentService agentService;
	private final org.springframework.core.env.Environment environment;

	@PostMapping("/api/v1/auth/signup/agent")
	@ResponseStatus(HttpStatus.CREATED)
	public AgentDto.SignupResponse signup(@Valid @RequestBody AgentDto.SignupRequest request) {
		return AgentDto.SignupResponse.from(agentService.signup(request.getEmail(), request.getPassword(),
			request.getRepresentativeName(), request.getPhone(), request.getAgreements().getPrivacyCollect(),
			request.getAgreements().getThirdPartyProvide(), request.getOfficeName(), request.getOfficeAddress()));
	}

	@GetMapping("/api/v1/agents/me")
	public AgentDto.ProfileResponse me(@AuthenticationPrincipal LoginUser loginUser) {
		return AgentDto.ProfileResponse.from(agentService.findByUserId(loginUser.getUserId()),
			environment.matchesProfiles("demo & !prod"));
	}
}
