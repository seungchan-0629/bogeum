package com.bogeum.domain.favorite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

/**
 * 관심 매물 해제가 실제 DB에서 본인의 그 매물만 지우는지 H2로 확인한다 (FAVORITE_002).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-favorite-delete-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FavoriteService.class)
class FavoriteDeleteTest {

	@Autowired
	private FavoriteService favoriteService;
	@Autowired
	private FavoriteRepository favoriteRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	// 해제에는 쓰지 않는 회원 조회
	@MockitoBean
	private UserService userService;
	// 해제에는 쓰지 않는 매물 서비스 (관심 매물 목록의 대표 사진용)
	@MockitoBean
	private PropertyService propertyService;

	private User landlord;
	private User user;
	private User otherUser;

	@BeforeEach
	void setUp() {
		landlord = user("박임대");
		user = user("최세입");
		otherUser = user("오세입");
	}

	@Test
	@DisplayName("담은 매물을 해제하면 본인 것만 지워지고, 다른 사람이 같은 매물을 담은 것은 남는다")
	void deletesOnlyOwnFavorite() {
		Property property = property(PropertyStatus.ACTIVE);
		favorite(user, property);
		favorite(otherUser, property);

		favoriteService.deleteFavorite(user.getUserId(), property.getPropertyId());

		assertFalse(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(user.getUserId(), property.getPropertyId()));
		assertTrue(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(otherUser.getUserId(), property.getPropertyId()));
	}

	@Test
	@DisplayName("담은 뒤 계약 완료된 매물도 관심 매물에서 뺄 수 있다")
	void deletesFavoriteOfContractedProperty() {
		Property property = property(PropertyStatus.CONTRACTED);
		favorite(user, property);

		favoriteService.deleteFavorite(user.getUserId(), property.getPropertyId());

		assertFalse(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(user.getUserId(), property.getPropertyId()));
	}

	@Test
	@DisplayName("담지 않았거나 없는 매물을 해제해도 오류 없이 끝난다")
	void ignoresNotFavorited() {
		Property property = property(PropertyStatus.ACTIVE);

		assertDoesNotThrow(() -> favoriteService.deleteFavorite(user.getUserId(), property.getPropertyId()));
		assertDoesNotThrow(() -> favoriteService.deleteFavorite(user.getUserId(), 999_999L));
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
