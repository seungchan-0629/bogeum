package com.bogeum.domain.aftersales;

import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** 인증된 사용자의 AS 접수와 임대인 조회 요청을 서비스로 전달한다 (AS_001, AS_002). */
@RestController
@RequiredArgsConstructor
public class AfterSalesController {

	private final AfterSalesService afterSalesService;

	// 최초 접수에서만 사진을 받는다 (AS_001).
	// 접수자는 요청 본문이 아닌 인증된 사용자에서 가져오며 계약 당사자 검증은 서비스가 맡는다.
	@PostMapping(value = "/as-requests", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public AfterSalesDto.CreateResponse createAfterSalesRequest(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestPart("request") AfterSalesDto.CreateRequest request,
			@RequestPart(value = "images", required = false) List<MultipartFile> images) {
		return afterSalesService.createAfterSalesRequest(loginUser.getUserId(), request, images);
	}

	/** JSON 댓글만 받으며 작성자와 완료 여부는 서비스가 소유 관계 확인 후 판단한다 (AS_003). */
	@PostMapping(value = "/as-requests/{asRequestId}/comments", consumes = "application/json")
	@ResponseStatus(HttpStatus.CREATED)
	public AfterSalesDto.CommentResponse createAfterSalesComment(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("asRequestId") Long asRequestId, @Valid @RequestBody AfterSalesDto.CommentCreateRequest request) {
		return afterSalesService.createAfterSalesComment(loginUser.getUserId(), asRequestId, request);
	}

	// 임대인 ID는 요청에서 받지 않고 JWT 인증 정보에서 가져온다 (AS_002).
	@GetMapping("/as-requests/received")
	public AfterSalesDto.ListResponse readReceivedAfterSalesRequests(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		return afterSalesService.readReceivedAfterSalesRequests(loginUser.getUserId(), page);
	}

	// 상세도 목록과 같은 소유 조건을 적용하며, 사진은 저장된 메타데이터만 반환한다 (AS_002).
	@GetMapping("/as-requests/{asRequestId}")
	public AfterSalesDto.DetailResponse readAfterSalesRequest(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("asRequestId") Long asRequestId) {
		return afterSalesService.readAfterSalesRequest(loginUser.getUserId(), asRequestId);
	}


}
