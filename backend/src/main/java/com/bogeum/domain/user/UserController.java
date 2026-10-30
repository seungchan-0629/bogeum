package com.bogeum.domain.user;

import com.bogeum.domain.agent.AgentService;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.user.model.User;
import com.bogeum.common.enums.Role;

import com.bogeum.domain.user.model.UserDto;
import com.bogeum.domain.user.model.UserDto.CsrfResponse;
import com.bogeum.domain.user.model.UserDto.LoginReq;
import com.bogeum.domain.user.model.UserDto.LoginRes;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import com.bogeum.domain.user.model.UserDto.TokenResponse;
import com.bogeum.domain.user.model.UserDto.RefreshRequest;
import com.bogeum.domain.user.model.UserDto.AuthResult;
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
	private final AgentService agentService;

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
		return tokenResponse(userService.login(request.getEmail(), request.getPassword()));
	}

	/** R: 요청 파라미터 userId 대신 검증된 principal에서 회원 번호를 꺼낸다. */
	@GetMapping("/me")
	public LoginRes me(@AuthenticationPrincipal LoginUser loginUser) {
		var user = userService.findUser(loginUser.getUserId());
		return LoginRes.from(user, agentFor(user));
	}

	/** 마스킹된 CSRF 값은 프론트의 메모리에 보관한다. 쿠키 원문은 JS로 읽지 않는다. */
	@GetMapping("/csrf")
	public CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	/** U: JSON으로 받은 Refresh Token과 CSRF 헤더로 갱신한다. */
	@PostMapping("/refresh")
	public TokenResponse refresh(@RequestBody(required = false) RefreshRequest request) {
		return tokenResponse(userService.refresh(request == null ? null : request.getRefreshToken()));
	}

	/** 로그아웃 성공을 응답한다. 브라우저의 토큰 삭제는 프론트에서 처리한다. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout()
	{
	}
	private Agent agentFor(User user) {
		return user.getRole() == Role.AGENT
			? agentService.findByUserId(user.getUserId()) : null;
	}

	private TokenResponse tokenResponse(AuthResult result) {
		return TokenResponse.from(result, agentFor(result.getUser()));
	}

}
