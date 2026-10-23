package com.bogeum.domain.user;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminUserController {

	private final UserService userService;
}
