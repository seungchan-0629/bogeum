package com.bogeum.domain.aftersales.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import com.bogeum.common.enums.AsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AS 접수와 임대인 조회의 요청·응답 DTO (AS_001, AS_002). */
public class AfterSalesDto {

	/** 계약 번호와 수리 요청 내용만 받는다. 임차인과 매물은 서버가 계약에서 확인한다. */
	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CreateRequest {
		@NotNull
		@Positive
		private Long contractId;

		@NotBlank
		@Size(max = 2000)
		private String content;
	}

	/** 저장된 접수 번호와 상태만 반환하며 계약·사용자 엔티티는 노출하지 않는다. */
	@Getter
	@Builder
	public static class CreateResponse {
		private Long asRequestId;
		private AsStatus asStatus;

		public static CreateResponse from(AfterSalesRequest request) {
			return CreateResponse.builder().asRequestId(request.getAsRequestId())
				.asStatus(request.getAsStatus()).build();
		}
	}

	/** 임대인에게 필요한 접수 내용과 매물·임차인 정보만 반환한다 (AS_002). */
	@Getter
	@Builder
	public static class ItemResponse {
		private Long asRequestId;
		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private String tenantName;
		private String content;
		private AsStatus asStatus;
		private LocalDateTime createdAt;

		public static ItemResponse from(AfterSalesRequest request) {
			return ItemResponse.builder().asRequestId(request.getAsRequestId())
				.propertyId(request.getProperty().getPropertyId())
				.roadAddress(request.getProperty().getRoadAddress()).detailAddress(request.getProperty().getDetailAddress())
				.tenantName(request.getTenant().getName()).content(request.getContent())
				.asStatus(request.getAsStatus()).createdAt(request.getCreatedAt()).build();
		}
	}

	/** 기존 중개 신청 목록처럼 화면의 페이지 번호는 1부터 센다 (AS_002). */
	@Getter
	@Builder
	public static class ListResponse {
		private List<ItemResponse> requests;
		private int page;
		private int totalPages;
		private long totalElements;

		public static ListResponse from(Page<AfterSalesRequest> requests) {
			return ListResponse.builder().requests(requests.map(ItemResponse::from).getContent())
				.page(requests.getNumber() + 1).totalPages(requests.getTotalPages())
				.totalElements(requests.getTotalElements()).build();
		}
	}

	/** 사진 파일의 존재·열람 가능 여부를 보장하지 않는 DB 메타데이터다. 저장 키는 노출하지 않는다. */
	@Getter
	@Builder
	public static class ImageResponse {
		private Long asRequestImageId;
		private LocalDateTime createdAt;

		public static ImageResponse from(AfterSalesRequestImage image) {
			return ImageResponse.builder().asRequestImageId(image.getAsRequestImageId())
				.createdAt(image.getCreatedAt()).build();
		}
	}

	/** 소유 권한을 확인한 접수 상세와 사진 메타데이터만 반환한다 (AS_002). */
	@Getter
	@Builder
	public static class DetailResponse {
		private Long asRequestId;
		private Long propertyId;
		private String roadAddress;
		private String detailAddress;
		private String tenantName;
		private String content;
		private AsStatus asStatus;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
		private List<ImageResponse> images;

		public static DetailResponse from(AfterSalesRequest request, List<AfterSalesRequestImage> images) {
			return DetailResponse.builder().asRequestId(request.getAsRequestId())
				.propertyId(request.getProperty().getPropertyId())
				.roadAddress(request.getProperty().getRoadAddress()).detailAddress(request.getProperty().getDetailAddress())
				.tenantName(request.getTenant().getName()).content(request.getContent())
				.asStatus(request.getAsStatus()).createdAt(request.getCreatedAt()).updatedAt(request.getUpdatedAt())
				.images(images.stream().map(ImageResponse::from).toList()).build();
		}
	}
}
