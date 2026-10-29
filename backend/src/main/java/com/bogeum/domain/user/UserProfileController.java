package com.bogeum.domain.user;

import com.bogeum.domain.user.model.UserDto.LoginUser;
import com.bogeum.domain.user.model.UserDto.ProfileReq;
import com.bogeum.domain.user.model.UserDto.ProfileRes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 회원 본인의 정보만 조회하고 수정한다. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {
	private final UserService userService;

	@GetMapping
	public ProfileRes me(@AuthenticationPrincipal LoginUser loginUser) {
		return ProfileRes.from(userService.findUser(loginUser.getUserId()));
	}

	@PatchMapping
	public ProfileRes update(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody ProfileReq request) {
		return ProfileRes.from(userService.updateProfile(loginUser.getUserId(), request.getPhone(),
			request.getCurrentPassword(), request.getNewPassword(), request.getPasswordConfirm()));
	}
}
