package com.bogeum.domain.property;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/property")
@RestController
@RequiredArgsConstructor
public class PropertyController {

	private final PropertyService propertyService;

	// 임대인이 매물 정보와 희망 중개사(선택)를 보내 중개를 신청한다 (PROPERTY_002)
	@PostMapping("/brokerage-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public PropertyDto.BrokerageResponse createBrokerageRequest(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody PropertyDto.BrokerageRequest request) {
		return propertyService.createBrokerageRequest(loginUser.getUserId(), request);
	}

	// 승인된 중개사가 본인에게 온 중개 신청을 수락한다 (PROPERTY_008)
	@PostMapping("/brokerage-requests/{brokerageRequestId}/accept")
	public PropertyDto.AcceptResponse acceptBrokerageRequest(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("brokerageRequestId") Long brokerageRequestId) {
		return propertyService.acceptBrokerageRequest(loginUser.getUserId(), brokerageRequestId);
	}
}
