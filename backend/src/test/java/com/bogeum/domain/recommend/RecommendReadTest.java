package com.bogeum.domain.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import com.bogeum.domain.favorite.FavoriteRepository;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.chat.ChatService;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyViewLog;
import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.recommend.model.SearchLog;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 매물 추천이 실제 DB에서 관심 지역을 정하고 인기 순으로 고르는지 H2로 확인한다 (RECOMMEND_001).
 * 마포구 매물 3개(망원 61·합정 62·상수 63)와 강남구 매물 2개(강남 64·역삼 65)를 공개하고,
 * 다른 사용자들의 조회·관심으로 인기 순서를 정해 둔다: 강남 64 > 합정 62 > 망원 61, 나머지는 기록 없음.
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-recommend-read-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecommendService.class, PropertyService.class, ChatService.class})
class RecommendReadTest {

	@Autowired
	private RecommendService recommendService;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private PropertyViewLogRepository propertyViewLogRepository;
	@Autowired
	private SearchLogRepository searchLogRepository;
	@Autowired
	private FavoriteRepository favoriteRepository;
	@Autowired
	private UserRepository userRepository;
	// 추천에 쓰지 않는 매물 서비스의 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User tenant;
	private User landlord;
	private Property mangwon;
	private Property hapjeong;
	private Property sangsu;
	private Property gangnam;
	private Property yeoksam;

	@BeforeEach
	void setUp() {
		tenant = user("김임차");
		landlord = user("박임대");
		mangwon = property(landlord, "서울특별시 마포구 월드컵로 61", "망원역", PropertyStatus.ACTIVE, 5);
		hapjeong = property(landlord, "서울특별시 마포구 독막로 332", "합정역", PropertyStatus.ACTIVE, 4);
		sangsu = property(landlord, "서울특별시 마포구 와우산로 21", "상수역", PropertyStatus.ACTIVE, 3);
		gangnam = property(landlord, "서울특별시 강남구 테헤란로 152", "강남역", PropertyStatus.ACTIVE, 2);
		yeoksam = property(landlord, "서울특별시 강남구 역삼로 1", "역삼역", PropertyStatus.ACTIVE, 1);
		// 인기: 강남 = 관심 2 × 3 = 6, 합정 = 조회 3, 망원 = 조회 1
		User other1 = user("이이웃");
		User other2 = user("최이웃");
		favorite(other1, gangnam);
		favorite(other2, gangnam);
		view(other1, hapjeong);
		view(other2, hapjeong);
		view(user("정이웃"), hapjeong);
		view(other1, mangwon);
	}

	@Test
	@DisplayName("기록이 없으면 전체 인기 순서로 주고 isPersonalized는 false다")
	void readsGlobalPopularWithoutSignals() {
		var response = recommendService.readRecommendations(tenant.getUserId(), 3);

		assertEquals(List.of(gangnam.getPropertyId(), hapjeong.getPropertyId(), mangwon.getPropertyId()), idsOf(response));
		assertFalse(response.getIsPersonalized());
		assertTrue(response.getRegions().isEmpty());
	}

	@Test
	@DisplayName("본 매물의 시·구와 지하철역 근처 매물을 인기 순으로 먼저 주고, 모자라면 전체 인기 매물로 채운다")
	void readsNearViewedProperty() {
		view(tenant, sangsu);

		var response = recommendService.readRecommendations(tenant.getUserId(), 4);

		// 마포구 3개 뒤에 전체 인기 1위 강남. 상수는 이번 조회로 망원과 인기가 같아져 더 최근에 공개된 상수가 앞이다
		assertEquals(List.of(hapjeong.getPropertyId(), sangsu.getPropertyId(), mangwon.getPropertyId(),
			gangnam.getPropertyId()), idsOf(response));
		assertEquals(List.of("서울특별시 마포구"), response.getRegions());
		assertEquals(List.of("상수역"), response.getStations());
		assertTrue(response.getIsPersonalized());
	}

	@Test
	@DisplayName("검색한 지역은 검색과 같은 규칙으로 맞춘다 (\"서울 강남구\" → 서울특별시 강남구 매물)")
	void readsNearSearchedRegion() {
		searchLogRepository.saveAndFlush(SearchLog.builder().user(tenant).region("서울 강남구")
			.searchedAt(LocalDateTime.now().minusDays(1)).build());

		var response = recommendService.readRecommendations(tenant.getUserId(), 2);

		assertEquals(List.of(gangnam.getPropertyId(), yeoksam.getPropertyId()), idsOf(response));
		assertEquals(List.of("서울 강남구"), response.getRegions());
	}

	@Test
	@DisplayName("이미 담은 매물, 본인 매물, 공개 중이 아닌 매물은 추천하지 않는다")
	void excludesFavoritedOwnAndNotPublished() {
		favorite(tenant, hapjeong);
		property(tenant, "서울특별시 마포구 성미산로 101", "망원역", PropertyStatus.ACTIVE, 6);
		property(landlord, "서울특별시 마포구 망원로 54", "망원역", PropertyStatus.HIDDEN, 7);

		var response = recommendService.readRecommendations(tenant.getUserId(), 12);

		assertEquals(List.of(mangwon.getPropertyId(), sangsu.getPropertyId(), gangnam.getPropertyId(),
			yeoksam.getPropertyId()), idsOf(response));
	}

	@Test
	@DisplayName("최근 30일이 지난 기록은 관심 지역에 쓰지 않는다")
	void ignoresOldSignals() {
		searchLogRepository.saveAndFlush(SearchLog.builder().user(tenant).region("서울 강남구")
			.searchedAt(LocalDateTime.now().minusDays(31)).build());

		assertFalse(recommendService.readRecommendations(tenant.getUserId(), 3).getIsPersonalized());
	}

	@Test
	@DisplayName("개수가 1보다 작으면 1개를 준다")
	void readsAtLeastOne() {
		assertEquals(1, recommendService.readRecommendations(tenant.getUserId(), 0).getProperties().size());
	}

	private List<Long> idsOf(RecommendDto.RecommendListResponse response) {
		return response.getProperties().stream().map(PropertyDto.PropertyItemResponse::getPropertyId).toList();
	}

	private void view(User viewer, Property property) {
		propertyViewLogRepository.saveAndFlush(PropertyViewLog.builder().user(viewer).property(property).build());
	}

	private void favorite(User owner, Property property) {
		favoriteRepository.saveAndFlush(Favorite.builder().user(owner).property(property).build());
	}

	// 공개 시각이 최근일수록 hoursAgo가 작다. 인기 점수가 같으면 최근 공개 순서다
	private Property property(User owner, String roadAddress, String station, PropertyStatus status, int hoursAgo) {
		return propertyRepository.saveAndFlush(Property.builder().landlord(owner).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000)).roadAddress(roadAddress)
			.nearbySubwayStation(station).propertyStatus(status).publishedAt(LocalDateTime.now().minusHours(hoursAgo))
			.build());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
