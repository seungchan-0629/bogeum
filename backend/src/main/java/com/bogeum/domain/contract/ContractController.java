package com.bogeum.domain.contract;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ContractController {

	private final ContractService contractService;

	// 승인된 중개사가 맡은 공개 매물의 계약 정보와 계약서 이미지를 등록한다 (PROPERTY_011)
	// 이미지가 없는 요청도 서비스가 사진 오류(INVALID_IMAGE)로 거절하도록 필수로 두지 않는다
	@PostMapping(value = "/contract-details", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public ContractDto.SaveResponse createContract(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestPart("request") ContractDto.ContractRequest request,
			@RequestPart(value = "images", required = false) List<MultipartFile> images) {
		return contractService.createContract(loginUser.getUserId(), request, images);
	}

	// 승인된 중개사가 본인이 중개해 계약 완료된 계약을 최근 계약일 순서로 본다 (AGENT_003)
	@GetMapping("/contract-details")
	public ContractDto.ContractListResponse readContracts(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		return contractService.readContracts(loginUser.getUserId(), page);
	}

	// 승인된 중개사가 맡은 매물의 계약 등록 화면에서 임대인과 임차인 후보를 본다 (PROPERTY_011)
	@GetMapping("/contract/candidates")
	public ContractDto.CandidateResponse readContractCandidates(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("propertyId") Long propertyId) {
		return contractService.readContractCandidates(loginUser.getUserId(), propertyId);
	}
}
