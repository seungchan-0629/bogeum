package com.bogeum.domain.property;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.enums.BrokerageStatus;
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

	// 승인된 중개사가 맡은 매물에 건물·방 사진을 추가한다 (PROPERTY_010)
	// 사진이 없는 요청도 서비스가 사진 오류(INVALID_IMAGE)로 거절하도록 필수로 두지 않는다
	@PostMapping(value = "/{propertyId}/images", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public List<PropertyDto.ImageResponse> createPropertyImages(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId,
			@RequestPart(value = "images", required = false) List<MultipartFile> images) {
		return propertyService.createPropertyImages(loginUser.getUserId(), propertyId, images);
	}

	// 로그인한 사용자가 공개 중인 매물을 최근 공개 순서로 스크롤하며 본다 (PROPERTY_004)
	// 첫 목록은 커서 없이, 다음 목록은 응답의 nextCursor 값을 그대로 보낸다
	@GetMapping
	public PropertyDto.PropertyListResponse readProperties(
			@RequestParam(value = "cursorPublishedAt", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorPublishedAt,
			@RequestParam(value = "cursorPropertyId", required = false) Long cursorPropertyId) {
		return propertyService.readProperties(cursorPublishedAt, cursorPropertyId);
	}

	// 로그인한 사용자가 공개 중인 매물의 상세 정보·사진·담당 중개사를 본다 (PROPERTY_003)
	@GetMapping("/{propertyId}")
	public PropertyDto.PublicDetailResponse readPublicProperty(@PathVariable("propertyId") Long propertyId) {
		return propertyService.readPublicProperty(propertyId);
	}

	// 승인된 중개사가 본인에게 온 중개 신청을 '새 신청'(REQUESTED)·'수락한 신청'(ACCEPTED)으로 나눠 본다 (PROPERTY_012)
	@GetMapping("/brokerage-requests/received")
	public PropertyDto.BrokerageListResponse readBrokerageRequests(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam("status") BrokerageStatus status,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		return propertyService.readBrokerageRequests(loginUser.getUserId(), status, page);
	}

	// 승인된 중개사가 맡은 매물의 정보 올리기 화면 첫 값을 본다 (PROPERTY_010)
	@GetMapping("/{propertyId}/details")
	public PropertyDto.DetailResponse readPropertyDetail(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId) {
		return propertyService.readPropertyDetail(loginUser.getUserId(), propertyId);
	}

	// 승인된 중개사가 본인에게 온 중개 신청을 수락한다 (PROPERTY_008)
	@PostMapping("/brokerage-requests/{brokerageRequestId}/accept")
	public PropertyDto.AcceptResponse acceptBrokerageRequest(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("brokerageRequestId") Long brokerageRequestId) {
		return propertyService.acceptBrokerageRequest(loginUser.getUserId(), brokerageRequestId);
	}

	// 승인된 중개사가 맡은 매물의 상세 정보를 임시 저장한다 (PROPERTY_010)
	@PutMapping("/{propertyId}/details")
	public PropertyDto.DetailSaveResponse updatePropertyDetail(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId, @Valid @RequestBody PropertyDto.DetailRequest request) {
		return propertyService.updatePropertyDetail(loginUser.getUserId(), propertyId, request);
	}

	// 승인된 중개사가 상세 정보와 사진을 다 올린 매물을 공개한다 (PROPERTY_010)
	@PostMapping("/{propertyId}/publish")
	public PropertyDto.DetailSaveResponse publishProperty(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId) {
		return propertyService.publishProperty(loginUser.getUserId(), propertyId);
	}

	// 승인된 중개사가 맡은 공개 전 매물의 사진 하나를 지운다 (PROPERTY_010)
	@DeleteMapping("/{propertyId}/images/{propertyImageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePropertyImage(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable("propertyId") Long propertyId, @PathVariable("propertyImageId") Long propertyImageId) {
		propertyService.deletePropertyImage(loginUser.getUserId(), propertyId, propertyImageId);
	}
}
