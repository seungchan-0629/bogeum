package com.bogeum.domain.aftersales;

import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import com.bogeum.domain.aftersales.model.AfterSalesDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** 계약 임차인의 AS 최초 접수 요청을 서비스로 전달한다 (AS_001). */
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
}
