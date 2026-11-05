package com.bogeum.domain.openbanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.bogeum.domain.openbanking.FakeKFTC.StateStore;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OpenBankingAuthorizationTest {

	@Test
	void authorizationUrlBindsUniqueOneTimeStateToLoggedInUser() {
		StateStore states = new StateStore();
		OpenBankingService service = new OpenBankingService(mock(OpenBankingRepository.class),
			mock(OpenBankingTransferRepository.class), states);
		URI uri = URI.create(service.createAuthorizationUrl(7L));
		var params = Arrays.stream(uri.getRawQuery().split("&"))
			.map(part -> part.split("=", 2))
			.collect(Collectors.toMap(part -> part[0], part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));

		assertEquals("http://localhost:8090/oauth/2.0/authorize", uri.getScheme() + "://" + uri.getAuthority() + uri.getPath());
		assertEquals("http://localhost:5173/mypage/openbanking/callback", params.get("redirect_uri"));
		assertEquals("code", params.get("response_type"));
		assertEquals("bogeum", params.get("client_id"));
		assertEquals("login inquiry transfer", params.get("scope"));
		assertEquals(7L, states.consume(params.get("state")));
		assertThrows(ResponseStatusException.class, () -> states.consume(params.get("state")));
		assertNotEquals(uri.toString(), service.createAuthorizationUrl(7L));
	}
}
