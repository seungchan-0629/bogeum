package com.bogeum.domain.chatbot;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatbotController {

	private final ChatbotService chatbotService;
}
