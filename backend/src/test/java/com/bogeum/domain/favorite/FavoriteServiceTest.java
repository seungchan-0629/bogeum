package com.bogeum.domain.favorite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

/** 관심 매물 등록 규칙(공개 매물만, 본인 매물 금지, 중복 없이 한 번만)을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

	@Mock
	private FavoriteRepository favoriteRepository;
	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private PropertyService propertyService;
	@Mock
	private UserService userService;

	private FavoriteService favoriteService;
	private final User landlord = User.builder().userId(1L).name("박임대").build();
	private final User user = User.builder().userId(2L).name("최세입").build();

	@BeforeEach
	void setUp() {
		favoriteService = new FavoriteService(favoriteRepository, propertyRepository, propertyService, userService);
	}

	@Test
	@DisplayName("공개 중인 남의 매물은 관심 매물로 저장한다")
	void createsFavorite() {
		Property property = givenActiveProperty();
		when(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(2L, 20L)).thenReturn(false);
		when(userService.findUser(2L)).thenReturn(user);

		favoriteService.createFavorite(2L, 20L);

		var saved = ArgumentCaptor.forClass(Favorite.class);
		verify(favoriteRepository).saveAndFlush(saved.capture());
		assertSame(user, saved.getValue().getUser());
		assertSame(property, saved.getValue().getProperty());
	}

	@Test
	@DisplayName("이미 담은 매물이면 다시 저장하지 않고 성공으로 끝낸다")
	void skipsAlreadyFavorited() {
		givenActiveProperty();
		when(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(2L, 20L)).thenReturn(true);

		favoriteService.createFavorite(2L, 20L);

		verify(favoriteRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("없거나 공개 중이 아닌 매물(공개 전·숨김·계약 완료·삭제)은 담을 수 없다")
	void rejectsPropertyNotPublic() {
		when(propertyRepository.findByPropertyIdAndPropertyStatus(20L, PropertyStatus.ACTIVE)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> favoriteService.createFavorite(2L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(favoriteRepository);
	}

	@Test
	@DisplayName("본인이 중개를 신청한 매물은 담을 수 없다")
	void rejectsOwnProperty() {
		givenActiveProperty();

		var exception = assertThrows(FavoriteException.class, () -> favoriteService.createFavorite(1L, 20L));

		assertEquals(ErrorCode.FAVORITE_OWN_PROPERTY, exception.getErrorCode());
		verifyNoInteractions(favoriteRepository);
	}

	@Test
	@DisplayName("동시에 두 번 들어와 중복 금지 제약에 걸려도, 이미 담겨 있으면 성공으로 끝낸다")
	void treatsConcurrentDuplicateAsSuccess() {
		givenActiveProperty();
		when(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(2L, 20L)).thenReturn(false, true);
		when(userService.findUser(2L)).thenReturn(user);
		when(favoriteRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertDoesNotThrow(() -> favoriteService.createFavorite(2L, 20L));
	}

	@Test
	@DisplayName("저장 중 다른 제약 오류가 나서 담기지 않았으면 오류를 그대로 올린다")
	void rethrowsOtherIntegrityError() {
		givenActiveProperty();
		when(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(2L, 20L)).thenReturn(false, false);
		when(userService.findUser(2L)).thenReturn(user);
		when(favoriteRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("other"));

		assertThrows(DataIntegrityViolationException.class, () -> favoriteService.createFavorite(2L, 20L));
	}

	@Test
	@DisplayName("관심 매물 해제는 매물 상태를 보지 않고 로그인 사용자의 그 매물만 지운다")
	void deletesFavorite() {
		favoriteService.deleteFavorite(2L, 20L);

		verify(favoriteRepository).deleteByUserAndProperty(2L, 20L);
		verifyNoInteractions(propertyRepository, userService);
	}

	@Test
	@DisplayName("관심 매물 목록은 최근에 담은 순서로 12개씩 주고, 대표 사진과 공개 중인지 함께 담는다")
	void readsFavorites() {
		Property active = Property.builder().propertyId(20L).landlord(landlord).propertyStatus(PropertyStatus.ACTIVE).build();
		Property contracted = Property.builder().propertyId(21L).landlord(landlord).propertyStatus(PropertyStatus.CONTRACTED).build();
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(favoriteRepository.findAllByUser_UserId(eq(2L), pageable.capture())).thenAnswer(invocation -> new PageImpl<>(
			List.of(Favorite.builder().user(user).property(active).build(),
				Favorite.builder().user(user).property(contracted).build()),
			invocation.getArgument(1), 14));
		when(propertyService.readThumbnailUrls(List.of(active, contracted))).thenReturn(Map.of(20L, "https://s3/20.jpg"));

		var response = favoriteService.readFavorites(2L, 2);

		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals(12, pageable.getValue().getPageSize());
		assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "favoritePropertyId"), pageable.getValue().getSort());
		assertEquals(List.of(20L, 21L),
			response.getFavorites().stream().map(item -> item.getProperty().getPropertyId()).toList());
		assertEquals("https://s3/20.jpg", response.getFavorites().get(0).getProperty().getThumbnailUrl());
		assertEquals(null, response.getFavorites().get(1).getProperty().getThumbnailUrl());
		assertEquals(true, response.getFavorites().get(0).getIsAvailable());
		assertEquals(false, response.getFavorites().get(1).getIsAvailable());
		assertEquals(2, response.getPage());
		assertEquals(2, response.getTotalPages());
		assertEquals(14L, response.getTotalElements());
	}

	@Test
	@DisplayName("관심 매물 페이지가 1보다 작으면 첫 페이지를 준다")
	void readsFirstPageWhenPageBelowOne() {
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(favoriteRepository.findAllByUser_UserId(eq(2L), pageable.capture()))
			.thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));
		when(propertyService.readThumbnailUrls(List.of())).thenReturn(Map.of());

		var response = favoriteService.readFavorites(2L, 0);

		assertEquals(0, pageable.getValue().getPageNumber());
		assertEquals(List.of(), response.getFavorites());
	}

	@Test
	@DisplayName("담은 매물 번호는 로그인 사용자 기준으로 전부 준다")
	void readsFavoriteIds() {
		when(favoriteRepository.findPropertyIdsByUserId(2L)).thenReturn(List.of(54L, 31L));

		var response = favoriteService.readFavoriteIds(2L);

		assertEquals(List.of(54L, 31L), response.getPropertyIds());
	}

	private Property givenActiveProperty() {
		Property property = Property.builder().propertyId(20L).landlord(landlord).propertyStatus(PropertyStatus.ACTIVE).build();
		when(propertyRepository.findByPropertyIdAndPropertyStatus(20L, PropertyStatus.ACTIVE)).thenReturn(Optional.of(property));
		return property;
	}
}
