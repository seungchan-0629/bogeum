package com.bogeum.domain.movein;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.bogeum.domain.movein.model.MoveInDto;
import com.bogeum.domain.user.model.UserDto.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 계약 임차인의 입주 사진 등록과 계약 임대인의 조회를 제공한다 (MOVEIN_001~002). */
@RestController
@RequiredArgsConstructor
public class MoveInController {
	private final MoveInService moveInService;

	/** 작성자는 인증 정보로 결정하고 사진과 설명은 요청 순서로 대응한다. */
	@PostMapping(value = "/contract/{contractId}/movein-defects", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public MoveInDto.UploadRes uploadPhotos(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("contractId") Long contractId,
			@Valid @RequestPart(value = "request", required = false) MoveInDto.UploadReq request,
			@RequestPart(value = "images", required = false) List<MultipartFile> images) {
		return MoveInDto.UploadRes.from(contractId,
			moveInService.uploadPhotos(loginUser.getUserId(), contractId, images,
				request == null ? null : request.getCaptions()));
	}

	/** 작성자·조회자 ID는 인증 정보에서 얻고 실제 계약 권한은 서비스가 확인한다. */
	@GetMapping("/contract/{contractId}/movein-defects")
	public MoveInDto.ReadRes readPhotos(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("contractId") Long contractId) {
		var photos = moveInService.readPhotos(loginUser.getUserId(), contractId).stream()
			.map(result -> MoveInDto.ReadPhotoRes.from(result.photo(), result.url())).toList();
		return MoveInDto.ReadRes.from(contractId, photos);
	}
}
