package com.bogeum.domain.contract;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import lombok.RequiredArgsConstructor;

@RequestMapping("/contract")
@RestController
@RequiredArgsConstructor
public class ContractController {

	private final ContractService contractService;

	// 승인된 중개사가 맡은 매물의 계약 등록 화면에서 임대인과 임차인 후보를 본다 (PROPERTY_011)
	@GetMapping("/candidates")
	public ContractDto.CandidateResponse readContractCandidates(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("propertyId") Long propertyId) {
		return contractService.readContractCandidates(loginUser.getUserId(), propertyId);
	}
}
