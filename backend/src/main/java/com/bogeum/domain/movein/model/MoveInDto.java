package com.bogeum.domain.movein.model;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 입주 사진 등록·조회의 요청과 응답을 모은다 (MOVEIN_001~002). */
public class MoveInDto {

	/** captions[i]는 multipart images의 i번째 사진에 대응한다. 빈 설명은 허용한다. */
	@Getter
	@NoArgsConstructor
	public static class UploadReq {
		@NotNull(message = "사진별 설명 목록을 보내 주세요.")
		private List<@NotNull(message = "사진별 설명은 null일 수 없습니다.")
			@Size(max = MoveInDefectPhoto.MAX_CAPTION_LENGTH, message = "사진 설명은 255자 이내로 입력해 주세요.") String> captions;
	}

	/** 내부 저장 키나 회원 정보를 노출하지 않는 사진별 등록 결과다. */
	@Getter
	@Builder
	public static class PhotoRes {
		private Long moveInDefectPhotoId;
		private String caption;
		private LocalDateTime createdAt;

		/** 저장된 식별자·설명·생성 시각만 응답으로 변환한다. */
		public static PhotoRes from(MoveInDefectPhoto photo) {
			return PhotoRes.builder().moveInDefectPhotoId(photo.getMoveInDefectPhotoId())
				.caption(photo.getCaption()).createdAt(photo.getCreatedAt()).build();
		}
	}

	/** 계약과 이번 요청에서 새로 등록한 사진 목록을 반환한다. */
	@Getter
	@Builder
	public static class UploadRes {
		private Long contractId;
		private List<PhotoRes> photos;

		/** 요청 순서대로 등록 결과를 반환한다. */
		public static UploadRes from(Long contractId, List<MoveInDefectPhoto> photos) {
			return UploadRes.builder().contractId(contractId).photos(photos.stream().map(PhotoRes::from).toList()).build();
		}
	}

	/** 기존 null 설명을 보존하며 내부 저장 키 대신 임시 조회 주소만 반환한다. */
	@Getter
	@Builder
	public static class ReadPhotoRes {
		private Long moveInDefectPhotoId;
		private String caption;
		private LocalDateTime createdAt;
		private String url;

		/** 저장소 미연결 시 url은 null이며 사진 등록 정보는 그대로 제공한다. */
		public static ReadPhotoRes from(MoveInDefectPhoto photo, String url) {
			return ReadPhotoRes.builder().moveInDefectPhotoId(photo.getMoveInDefectPhotoId())
				.caption(photo.getCaption()).createdAt(photo.getCreatedAt()).url(url).build();
		}
	}

	/** 임대인 권한을 확인한 계약의 사진을 생성 시각·사진 ID 오름차순으로 반환한다. */
	@Getter
	@Builder
	public static class ReadRes {
		private Long contractId;
		private List<ReadPhotoRes> photos;

		/** 서비스에서 조회한 순서와 사진별 URL을 그대로 응답에 담는다. */
		public static ReadRes from(Long contractId, List<ReadPhotoRes> photos) {
			return ReadRes.builder().contractId(contractId).photos(photos).build();
		}
	}
}
