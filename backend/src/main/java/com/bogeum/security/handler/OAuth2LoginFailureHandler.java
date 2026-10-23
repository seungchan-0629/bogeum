package com.bogeum.security.handler;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import com.bogeum.exception.ErrorCode;
import com.bogeum.security.oauth.SocialAuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
	private final JsonResponseWriter responseWriter;
	private final SecurityContextRepository contextRepository;

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		// 연결 취소/충돌 때문에 이미 로그인한 회원이 로그아웃되거나 다른 회원으로 바뀌지 않게 한다.
		if (request.getAttribute(SocialAuthorizationRequestRepository.ORIGINAL_AUTHENTICATION) instanceof Authentication original) {
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(original);
			SecurityContextHolder.setContext(context);
			contextRepository.saveContext(context, request, response);
		}
		ErrorCode code = ErrorCode.SOCIAL_LOGIN_FAILED;
		if (exception instanceof OAuth2AuthenticationException oauth) {
			if (ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED.name().equals(oauth.getError().getErrorCode())) {
				code = ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED;
			} else if (ErrorCode.SOCIAL_LINK_EXPIRED.name().equals(oauth.getError().getErrorCode())) {
				code = ErrorCode.SOCIAL_LINK_EXPIRED;
			}
		}
		responseWriter.writeError(response, code);
	}
}
