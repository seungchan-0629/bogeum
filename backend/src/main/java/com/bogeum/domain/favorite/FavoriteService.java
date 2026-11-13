package com.bogeum.domain.favorite;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.favorite.model.FavoriteDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

	// 관심 매물 목록 한 페이지의 개수. 매물 찾기와 같아 카드가 2·3·4열 어느 쪽이어도 줄이 맞는다 (FAVORITE_003)
	private static final int PAGE_SIZE = 12;
	// 최근에 담은 순서. 같은 시각이면 관심 번호가 큰(나중에 담은) 것부터다
	private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "favoritePropertyId");

	private final FavoriteRepository favoriteRepository;
	private final PropertyRepository propertyRepository;
	private final PropertyService propertyService;
	private final UserService userService;

	/**
	 * 사용자가 공개 중인 매물을 관심 매물로 담는다 (FAVORITE_001).
	 * 이미 담은 매물이면 그대로 성공으로 끝내서, 하트를 여러 번 눌러도 한 번만 저장된다.
	 * 동시에 두 번 들어와 DB의 중복 금지 제약에 걸려도 이미 담긴 것이라 성공으로 본다.
	 * 이 처리를 위해 저장은 저장소가 연 짧은 트랜잭션으로 끝낸다. 메서드 전체를 트랜잭션으로 묶으면
	 * 중복 오류 뒤 트랜잭션이 되돌릴 상태로 남아, 성공으로 처리해도 끝에서 오류가 난다.
	 *
	 * @throws PropertyException 없거나 공개 중이 아닌 매물인 경우
	 * @throws FavoriteException 본인이 중개를 신청한 매물인 경우
	 */
	public void createFavorite(Long userId, Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndPropertyStatus(propertyId, PropertyStatus.ACTIVE)
			.orElseThrow(PropertyException::propertyNotFound);
		// 임대인이 자기 매물을 담으면 인기 매물 집계(RECOMMEND_001)의 관심 수가 부풀려진다
		if (property.getLandlord().getUserId().equals(userId)) {
			throw FavoriteException.ownProperty();
		}
		if (favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(userId, propertyId)) {
			return;
		}
		try {
			favoriteRepository.saveAndFlush(Favorite.builder().user(userService.findUser(userId)).property(property).build());
		} catch (DataIntegrityViolationException exception) {
			// 확인과 저장 사이에 같은 요청이 먼저 저장한 경우만 성공으로 끝낸다. 그 밖의 제약 오류는 그대로 올린다
			if (!favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(userId, propertyId)) {
				throw exception;
			}
		}
	}

	/**
	 * 사용자 본인이 담은 관심 매물을 최근에 담은 순서로 페이지 단위로 조회한다 (FAVORITE_003).
	 * 담은 뒤 계약 완료·숨김·삭제된 매물도 해제할 수 있게 목록에 남기고, 공개 중인지만 알린다.
	 *
	 * @param page 1부터 센다. 1보다 작으면 첫 페이지를 준다
	 */
	@Transactional(readOnly = true)
	public FavoriteDto.FavoriteListResponse readFavorites(Long userId, int page) {
		Page<Favorite> favorites = favoriteRepository.findAllByUser_UserId(userId,
			PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, LIST_SORT));
		Map<Long, String> thumbnailUrls = propertyService.readThumbnailUrls(
			favorites.map(Favorite::getProperty).getContent());
		return FavoriteDto.FavoriteListResponse.from(favorites, thumbnailUrls);
	}

	/**
	 * 사용자 본인이 담은 매물 번호를 전부 준다 (FAVORITE_003).
	 * 목록은 페이지로 나뉘어 번호를 다 모을 수 없어서, 매물 찾기·상세가 처음부터 하트를 채울 때 이걸 쓴다.
	 */
	@Transactional(readOnly = true)
	public FavoriteDto.FavoriteIdsResponse readFavoriteIds(Long userId) {
		return FavoriteDto.FavoriteIdsResponse.builder()
			.propertyIds(favoriteRepository.findPropertyIdsByUserId(userId))
			.build();
	}

	/**
	 * 사용자가 담아 둔 매물을 관심 매물에서 뺀다 (FAVORITE_002).
	 * 담은 뒤 계약 완료·숨김·삭제된 매물도 목록에서 뺄 수 있어야 해서 매물 상태는 보지 않는다.
	 * 담지 않았거나 없는 매물이면 지울 게 없어 그대로 성공으로 끝낸다. 등록과 같이 여러 번 보내도 결과가 같다.
	 */
	@Transactional
	public void deleteFavorite(Long userId, Long propertyId) {
		favoriteRepository.deleteByUserAndProperty(userId, propertyId);
	}
}
