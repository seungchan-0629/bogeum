package com.bogeum.domain.favorite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.favorite.model.FavoriteDto;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 관심 매물 목록·번호가 실제 DB에서 본인 것만, 최근에 담은 순서로 나오는지 H2로 확인한다 (FAVORITE_003).
 * 사진 저장소는 등록하지 않아 대표 사진 주소는 비어 있다.
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-favorite-list-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FavoriteService.class, PropertyService.class})
class FavoriteListTest {

	@Autowired
	private FavoriteService favoriteService;
	@Autowired
	private FavoriteRepository favoriteRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	// 목록 조회에 쓰지 않는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User landlord;
	private User user;

	@BeforeEach
	void setUp() {
		landlord = user("박임대");
		user = user("최세입");
	}

	@Test
	@DisplayName("관심 매물 목록은 본인 것만 최근에 담은 순서로 주고, 계약 완료된 매물도 공개 중이 아님으로 남긴다")
	void readsOwnFavoritesNewestFirst() {
		Property first = property(PropertyStatus.ACTIVE);
		Property contracted = property(PropertyStatus.CONTRACTED);
		Property last = property(PropertyStatus.ACTIVE);
		favorite(user, first);
		favorite(user, contracted);
		favorite(user, last);
		favorite(user("오세입"), first);

		var response = favoriteService.readFavorites(user.getUserId(), 1);

		assertEquals(List.of(last.getPropertyId(), contracted.getPropertyId(), first.getPropertyId()),
			response.getFavorites().stream().map(item -> item.getProperty().getPropertyId()).toList());
		assertEquals(List.of(true, false, true),
			response.getFavorites().stream().map(FavoriteDto.FavoriteItemResponse::getIsAvailable).toList());
		assertEquals(3L, response.getTotalElements());
	}

	@Test
	@DisplayName("관심 매물이 12개를 넘으면 다음 페이지로 나뉜다")
	void splitsIntoPages() {
		for (int index = 0; index < 13; index++) {
			favorite(user, property(PropertyStatus.ACTIVE));
		}

		var first = favoriteService.readFavorites(user.getUserId(), 1);
		var second = favoriteService.readFavorites(user.getUserId(), 2);

		assertEquals(12, first.getFavorites().size());
		assertEquals(1, second.getFavorites().size());
		assertEquals(2, first.getTotalPages());
		assertEquals(13L, first.getTotalElements());
	}

	@Test
	@DisplayName("담은 매물 번호는 본인이 담은 것만 전부 준다")
	void readsOwnFavoriteIds() {
		List<Long> expected = new ArrayList<>();
		for (int index = 0; index < 13; index++) {
			Property property = property(index == 0 ? PropertyStatus.CONTRACTED : PropertyStatus.ACTIVE);
			favorite(user, property);
			expected.add(property.getPropertyId());
		}
		favorite(user("오세입"), property(PropertyStatus.ACTIVE));

		Set<Long> ids = new HashSet<>(favoriteService.readFavoriteIds(user.getUserId()).getPropertyIds());

		assertEquals(new HashSet<>(expected), ids);
	}

	private Property property(PropertyStatus status) {
		return propertyRepository.saveAndFlush(Property.builder().landlord(landlord).leaseType(LeaseType.JEONSE)
			.deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61").propertyStatus(status).build());
	}

	private void favorite(User owner, Property property) {
		favoriteRepository.saveAndFlush(Favorite.builder().user(owner).property(property).build());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
