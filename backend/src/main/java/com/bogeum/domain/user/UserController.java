package com.bogeum.domain.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.bogeum.domain.user.model.UserDto;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	/** 일반 회원가입 정보를 검증하고 생성된 사용자 식별자를 반환한다. */
	@PostMapping("/signup/user")
	@ResponseStatus(HttpStatus.CREATED)
	public UserDto.SignupResponse signup(@Valid @RequestBody UserDto.SignupRequest request) {
		return UserDto.SignupResponse.from(userService.signup(
			request.getEmail(), request.getPassword(), request.getName(), request.getPhone(),
			request.getAgreements().getPrivacyCollect(), request.getAgreements().getThirdPartyProvide()));
	}

	/**
	 * 일반 로그인 요청을 검증하고 공개 가능한 사용자 정보만 반환한다.
	 * 입력 오류는 @Valid, 인증 실패는 전역 예외 처리기가 HTTP 오류로 변환한다.
	 * 세션 발급·유지는 후속 인증 설정에서 연결한다.
	 *
	 * @param request 이메일과 원문 비밀번호
	 * @return 비밀번호와 해시를 제외한 로그인 응답
	 */
	@PostMapping("/login")
	public UserDto.LoginRes login(@Valid @RequestBody UserDto.LoginReq request) {
		return UserDto.LoginRes.from(userService.login(request.getEmail(), request.getPassword()));
	}
}
