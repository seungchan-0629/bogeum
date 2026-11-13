package com.bogeum.domain.favorite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
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
	private UserService userService;

	private FavoriteService favoriteService;
	private final User landlord = User.builder().userId(1L).name("박임대").build();
	private final User user = User.builder().userId(2L).name("최세입").build();

	@BeforeEach
	void setUp() {
		favoriteService = new FavoriteService(favoriteRepository, propertyRepository, userService);
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

	private Property givenActiveProperty() {
		Property property = Property.builder().propertyId(20L).landlord(landlord).propertyStatus(PropertyStatus.ACTIVE).build();
		when(propertyRepository.findByPropertyIdAndPropertyStatus(20L, PropertyStatus.ACTIVE)).thenReturn(Optional.of(property));
		return property;
	}
}
