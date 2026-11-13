package com.bogeum.domain.favorite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

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
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;

/**
 * 관심 매물이 바뀐 칼럼 이름(user_id)으로 저장되고, 같은 매물을 여러 번 담아도 한 줄만 남는지 H2로 확인한다 (FAVORITE_001).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-favorite-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FavoriteService.class)
class FavoriteCreateTest {

	@Autowired
	private FavoriteService favoriteService;
	@Autowired
	private FavoriteRepository favoriteRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	// 회원 조회는 회원 기능의 일이라 저장한 회원을 그대로 돌려주게 한다
	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("같은 매물을 여러 번 담아도 관심 매물은 한 줄만 남는다")
	void savesOnlyOnce() {
		User landlord = user("박임대");
		User user = user("최세입");
		when(userService.findUser(user.getUserId())).thenReturn(user);
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(landlord)
			.leaseType(LeaseType.JEONSE).deposit(BigDecimal.valueOf(300_000_000)).roadAddress("서울특별시 마포구 월드컵로 61")
			.propertyStatus(PropertyStatus.ACTIVE).build());

		favoriteService.createFavorite(user.getUserId(), property.getPropertyId());
		favoriteService.createFavorite(user.getUserId(), property.getPropertyId());

		assertEquals(1, favoriteRepository.count());
		assertTrue(favoriteRepository.existsByUser_UserIdAndProperty_PropertyId(user.getUserId(), property.getPropertyId()));
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
