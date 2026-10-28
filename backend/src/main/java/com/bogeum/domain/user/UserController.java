package com.bogeum.domain.user;

import com.bogeum.domain.user.model.UserDto;
import com.bogeum.domain.user.model.UserDto.CsrfResponse;
import com.bogeum.domain.user.model.UserDto.LoginReq;
import com.bogeum.domain.user.model.UserDto.LoginRes;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import com.bogeum.domain.user.model.UserDto.TokenResponse;
import com.bogeum.domain.user.model.UserDto.RefreshRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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


	/** C: 기존 보금과 같은 JSON {email,password}를 받는다. */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginReq request) {
		return TokenResponse.from(userService.login(request.getEmail(), request.getPassword()));
	}

	/** R: 요청 파라미터 userId 대신 검증된 principal에서 회원 번호를 꺼낸다. */
	@GetMapping("/me")
	public LoginRes me(@AuthenticationPrincipal LoginUser loginUser) {
		return LoginRes.from(userService.findUser(loginUser.getUserId()));
	}

	/** 마스킹된 CSRF 값은 프론트의 메모리에 보관한다. 쿠키 원문은 JS로 읽지 않는다. */
	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	/** U: JSON으로 받은 Refresh Token과 CSRF 헤더로 갱신한다. */
	@PostMapping("/refresh")
	public TokenResponse refresh(@RequestBody(required = false) RefreshRequest request) {
		return TokenResponse.from(userService.refresh(request == null ? null : request.refreshToken()));
	}

	/** D: 서버 로그인 기록을 폐기한다. 브라우저 쿠키 삭제는 프론트에서 처리한다. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestBody(required = false) RefreshRequest request) {
		userService.logout(request == null ? null : request.refreshToken());
	}
}
