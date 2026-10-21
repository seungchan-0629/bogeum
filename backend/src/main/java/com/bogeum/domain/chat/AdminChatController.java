package com.bogeum.domain.chat;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminChatController {

	private final ChatService chatService;
}
