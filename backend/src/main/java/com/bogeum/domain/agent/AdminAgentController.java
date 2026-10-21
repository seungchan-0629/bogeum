package com.bogeum.domain.agent;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminAgentController {

	private final AgentService agentService;
}
