package com.bogeum.domain.favorite.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.property.model.PropertyDto;

import lombok.Builder;
import lombok.Getter;

public class FavoriteDto {

	/**
	 * 관심 매물 카드 한 장 (FAVORITE_003).
	 * 카드 칸은 매물 찾기 카드와 같아서 그 응답(PropertyItemResponse)을 그대로 담는다.
	 * 담은 뒤 계약 완료·숨김·삭제된 매물도 목록에 남아 해제할 수 있게 하고, isAvailable로만 알린다.
	 * 숨김은 관리자가 위험 매물로 가린 것이라 그 이유는 나누지 않는다.
	 */
	@Getter
	@Builder
	public static class FavoriteItemResponse {

		private PropertyDto.PropertyItemResponse property;
		private LocalDateTime favoritedAt;
		// 지금도 공개 중인지. 아니면 화면이 카드를 회색으로 보여 주고 상세로 보내지 않는다
		private Boolean isAvailable;

		public static FavoriteItemResponse from(Favorite favorite, String thumbnailUrl) {
			return FavoriteItemResponse.builder()
				.property(PropertyDto.PropertyItemResponse.from(favorite.getProperty(), thumbnailUrl))
				.favoritedAt(favorite.getCreatedAt())
				.isAvailable(favorite.getProperty().getPropertyStatus() == PropertyStatus.ACTIVE)
				.build();
		}
	}

	/** 관심 매물 목록과 페이지 정보. page는 화면의 페이지 번호와 같이 1부터 센다 (FAVORITE_003) */
	@Getter
	@Builder
	public static class FavoriteListResponse {

		private List<FavoriteItemResponse> favorites;
		private int page;
		private int totalPages;
		private long totalElements;

		// thumbnailUrls: 매물 번호 → 대표 사진 주소 (사진이 없으면 들어 있지 않다)
		public static FavoriteListResponse from(Page<Favorite> favorites, Map<Long, String> thumbnailUrls) {
			return FavoriteListResponse.builder()
				.favorites(favorites.map(favorite -> FavoriteItemResponse.from(favorite,
					thumbnailUrls.get(favorite.getProperty().getPropertyId()))).getContent())
				.page(favorites.getNumber() + 1)
				.totalPages(favorites.getTotalPages())
				.totalElements(favorites.getTotalElements())
				.build();
		}
	}

	/** 내가 담은 매물 번호 전부. 매물 찾기·상세가 처음부터 하트를 채워 보여 줄 때 쓴다 (FAVORITE_003) */
	@Getter
	@Builder
	public static class FavoriteIdsResponse {

		private List<Long> propertyIds;
	}
}
