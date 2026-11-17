package com.bogeum.domain.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.bogeum.common.enums.PropertyType;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.favorite.FavoriteRepository;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyViewLog;
import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.recommend.model.SimilarBasis;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 비슷한 매물 추천이 실제 DB에서 임대 형식이 같은 공개 매물만 비슷한 순서로 고르는지 H2로 확인한다 (RECOMMEND_002).
 * 기준은 마포구 합정역 오피스텔 월세(base). 후보:
 * twin(같은 조건, 85점) > sameStation(다른 구 빌라, 같은 역·비슷한 가격, 55점) > sameRegion(같은 구 빌라, 가격 다름, 30점)
 * (모든 매물이 10평·방 1개라 면적·방 점수 20점은 공통)
 * 그리고 전세(jeonse)는 임대 형식이 달라 후보가 아니다.
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-recommend-similar-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecommendService.class, PropertyService.class})
class RecommendSimilarTest {

	@Autowired
	private RecommendService recommendService;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private PropertyViewLogRepository propertyViewLogRepository;
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
	private Property base;
	private Property twin;
	private Property sameStation;
	private Property sameRegion;
	private Property jeonse;

	@BeforeEach
	void setUp() {
		tenant = user("김임차");
		landlord = user("박임대");
		base = property(landlord, PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000,
			"서울특별시 마포구 독막로 332", "합정역", PropertyStatus.ACTIVE);
		twin = property(landlord, PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000,
			"서울특별시 마포구 와우산로 21", "합정역", PropertyStatus.ACTIVE);
		sameStation = property(landlord, PropertyType.VILLA, LeaseType.MONTHLY, 10_500_000, 720_000,
			"서울특별시 서대문구 연희로 129", "합정역", PropertyStatus.ACTIVE);
		sameRegion = property(landlord, PropertyType.VILLA, LeaseType.MONTHLY, 50_000_000, 2_000_000,
			"서울특별시 마포구 월드컵로 61", "망원역", PropertyStatus.ACTIVE);
		jeonse = property(landlord, PropertyType.OFFICETEL, LeaseType.JEONSE, 200_000_000, 0,
			"서울특별시 마포구 독막로 300", "합정역", PropertyStatus.ACTIVE);
	}

	@Test
	@DisplayName("상세의 비슷한 매물은 임대 형식이 같은 매물만 비슷한 순서로 주고 기준 매물 자신은 뺀다")
	void readsSimilarToDetail() {
		var response = recommendService.readSimilarProperties(tenant.getUserId(), base.getPropertyId(), 12);

		assertEquals(List.of(twin.getPropertyId(), sameStation.getPropertyId(), sameRegion.getPropertyId()),
			idsOf(response));
	}

	@Test
	@DisplayName("이미 담은 매물, 본인 매물, 공개 중이 아닌 매물은 빼고 개수만큼 준다")
	void excludesFavoritedOwnAndNotPublished() {
		favorite(tenant, twin);
		property(tenant, PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000,
			"서울특별시 마포구 와우산로 30", "합정역", PropertyStatus.ACTIVE);
		property(landlord, PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000,
			"서울특별시 마포구 와우산로 40", "합정역", PropertyStatus.CONTRACTED);

		var response = recommendService.readSimilarProperties(tenant.getUserId(), base.getPropertyId(), 1);

		assertEquals(List.of(sameStation.getPropertyId()), idsOf(response));
	}

	@Test
	@DisplayName("공개 중이 아닌 매물의 비슷한 매물은 PROPERTY_NOT_FOUND로 거절한다")
	void rejectsNotPublishedBase() {
		Property hidden = property(landlord, PropertyType.OFFICETEL, LeaseType.MONTHLY, 10_000_000, 700_000,
			"서울특별시 마포구 독막로 1", "합정역", PropertyStatus.HIDDEN);

		PropertyException exception = assertThrows(PropertyException.class,
			() -> recommendService.readSimilarProperties(tenant.getUserId(), hidden.getPropertyId(), 3));
		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	@DisplayName("최근 본 매물 기준은 본 매물과 비슷한 매물을 주고, 본 매물 자신은 뺀다")
	void readsSimilarToViewed() {
		view(tenant, base);

		var response = recommendService.readSimilarByBasis(tenant.getUserId(), SimilarBasis.VIEW, 12);

		assertEquals(List.of(twin.getPropertyId(), sameStation.getPropertyId(), sameRegion.getPropertyId()),
			idsOf(response));
	}

	@Test
	@DisplayName("관심 매물 기준은 담은 매물과 비슷한 매물을 주고, 담은 매물은 모두 뺀다")
	void readsSimilarToFavorites() {
		favorite(tenant, base);
		favorite(tenant, sameRegion);

		var response = recommendService.readSimilarByBasis(tenant.getUserId(), SimilarBasis.FAVORITE, 12);

		assertEquals(List.of(twin.getPropertyId(), sameStation.getPropertyId()), idsOf(response));
	}

	@Test
	@DisplayName("기준 매물이 없으면 빈 목록이라 화면이 추천 기준을 안내한다")
	void readsEmptyWithoutBasis() {
		assertTrue(recommendService.readSimilarByBasis(tenant.getUserId(), SimilarBasis.VIEW, 3).getProperties().isEmpty());
		assertTrue(recommendService.readSimilarByBasis(tenant.getUserId(), SimilarBasis.FAVORITE, 3).getProperties()
			.isEmpty());
	}

	private List<Long> idsOf(RecommendDto.SimilarListResponse response) {
		return response.getProperties().stream().map(PropertyDto.PropertyItemResponse::getPropertyId).toList();
	}

	private void view(User viewer, Property property) {
		propertyViewLogRepository.saveAndFlush(PropertyViewLog.builder().user(viewer).property(property).build());
	}

	private void favorite(User owner, Property property) {
		favoriteRepository.saveAndFlush(Favorite.builder().user(owner).property(property).build());
	}

	private Property property(User owner, PropertyType type, LeaseType leaseType, long deposit, long monthlyRent,
			String roadAddress, String station, PropertyStatus status) {
		return propertyRepository.saveAndFlush(Property.builder().landlord(owner).propertyType(type).leaseType(leaseType)
			.deposit(BigDecimal.valueOf(deposit)).monthlyRent(BigDecimal.valueOf(monthlyRent))
			.areaPyeong(new BigDecimal("10.00")).roomCount(1).roadAddress(roadAddress).nearbySubwayStation(station)
			.propertyStatus(status).publishedAt(LocalDateTime.now().minusHours(1)).build());
	}

	private User user(String name) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}
}
