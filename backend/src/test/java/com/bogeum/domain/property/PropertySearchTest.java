package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 매물 목록 검색 조건·정렬·커서가 실제 쿼리에서 맞는 매물만 고르는지 H2로 확인한다 (PROPERTY_004, PROPERTY_005).
 * 사진 저장소는 등록하지 않아 대표 사진 주소는 비어 있다.
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-property-search-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PropertyService.class)
class PropertySearchTest {

	private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 9, 0);

	@Autowired
	private PropertyService propertyService;
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

	@BeforeEach
	void setUp() {
		landlord = userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
			.passwordHash("hash").name("박임대").phone("010" + (int) (Math.random() * 100_000_000)).role(Role.USER).build());
	}

	@Test
	@DisplayName("조건이 없으면 공개 중인 매물만 공개 시각 최신순(같으면 번호 큰 순서)으로 12개씩 주고, 커서로 겹치지 않게 이어진다")
	void readsActiveNewestFirstWithCursor() {
		List<Long> expected = new java.util.ArrayList<>();
		for (int index = 0; index < 13; index++) {
			// 마지막 두 매물은 공개 시각이 같아 번호로 순서를 정한다
			LocalDateTime publishedAt = BASE.plusHours(Math.min(index, 11));
			expected.add(0, save(property -> property.publishedAt(publishedAt)).getPropertyId());
		}
		save(property -> property.propertyStatus(PropertyStatus.REQUESTED).publishedAt(null));
		save(property -> property.propertyStatus(PropertyStatus.HIDDEN).publishedAt(BASE.plusDays(1)));
		save(property -> property.propertyStatus(PropertyStatus.CONTRACTED).publishedAt(BASE.plusDays(1)));

		var first = propertyService.readProperties(noSearch(), null, null);
		var cursor = first.getNextCursor();
		var second = propertyService.readProperties(noSearch(), cursor.getCursorPublishedAt(), cursor.getCursorPropertyId());

		assertEquals(expected.subList(0, 12), ids(first));
		assertTrue(first.isHasNext());
		assertEquals(expected.subList(12, 13), ids(second));
		assertFalse(second.isHasNext());
	}

	@Test
	@DisplayName("매물 종류·임대 형식은 고른 것 중 하나면 나온다")
	void filtersByTypes() {
		Long apartment = save(property -> property.propertyType(PropertyType.APARTMENT)).getPropertyId();
		Long villa = save(property -> property.propertyType(PropertyType.VILLA).leaseType(LeaseType.JEONSE)
			.monthlyRent(BigDecimal.ZERO)).getPropertyId();
		save(property -> property.propertyType(PropertyType.OFFICETEL));

		assertEquals(List.of(villa, apartment), search(builder -> builder
			.propertyTypes(List.of(PropertyType.APARTMENT, PropertyType.VILLA))));
		assertEquals(List.of(villa), search(builder -> builder.leaseTypes(List.of(LeaseType.JEONSE))));
	}

	@Test
	@DisplayName("보증금·월세는 최소 이상 최대 이하이고, 한쪽만 보내면 그쪽만 본다")
	void filtersByPriceRange() {
		Long cheap = save(property -> property.deposit(won(10_000_000)).monthlyRent(won(500_000))).getPropertyId();
		Long middle = save(property -> property.deposit(won(100_000_000)).monthlyRent(won(900_000))).getPropertyId();
		Long jeonse = save(property -> property.leaseType(LeaseType.JEONSE).deposit(won(300_000_000))
			.monthlyRent(BigDecimal.ZERO)).getPropertyId();

		assertEquals(List.of(middle), search(builder -> builder.minDeposit(won(50_000_000)).maxDeposit(won(200_000_000))));
		assertEquals(List.of(jeonse, middle), search(builder -> builder.minDeposit(won(100_000_000))));
		// 전세는 월세가 0원이라 최대 월세 조건을 통과한다
		assertEquals(List.of(jeonse, cheap), search(builder -> builder.maxMonthlyRent(won(600_000))));
		assertEquals(List.of(middle), search(builder -> builder.minMonthlyRent(won(600_000))));
	}

	@Test
	@DisplayName("면적·세대 수·주차·층·방 개수는 최소 이상이고, 값이 비어 있는 매물은 빠진다")
	void filtersByMinimumsExcludingEmptyValues() {
		Long large = save(property -> property.areaPyeong(new BigDecimal("30.00")).householdCount(500).parkingCount(2)
			.floor(10).roomCount(3)).getPropertyId();
		Long small = save(property -> property.areaPyeong(new BigDecimal("8.50")).householdCount(10).parkingCount(0)
			.floor(-1).roomCount(1)).getPropertyId();
		// 세대 수·주차 대수만 비어 있고 나머지는 어떤 최소 조건에도 걸리지 않는 매물
		save(property -> property.householdCount(null).parkingCount(null).areaPyeong(new BigDecimal("5.00"))
			.floor(-2).roomCount(1));

		assertEquals(List.of(large), search(builder -> builder.minArea(new BigDecimal("10"))));
		assertEquals(List.of(small, large), search(builder -> builder.minHouseholdCount(10)));
		assertEquals(List.of(small, large), search(builder -> builder.minParkingCount(0)));
		assertEquals(List.of(large), search(builder -> builder.minParkingCount(1)));
		assertEquals(List.of(large), search(builder -> builder.minFloor(1)));
		assertEquals(List.of(large), search(builder -> builder.minRoomCount(2)));
	}

	@Test
	@DisplayName("지역은 띄어쓰기로 나눈 낱말이 모두 도로명주소에 있어야 하고, 지하철역은 글자가 들어 있으면 나온다")
	void filtersByRegionWordsAndSubway() {
		Long gangnam = save(property -> property.roadAddress("서울특별시 강남구 개포로 267")
			.nearbySubwayStation("개포동역")).getPropertyId();
		save(property -> property.roadAddress("서울특별시 강서구 공항대로 305").nearbySubwayStation("발산역"));
		save(property -> property.roadAddress("서울특별시 강남구 역삼로 1").nearbySubwayStation(null));

		assertEquals(2, search(builder -> builder.region("  서울   강남구 ")).size());
		assertEquals(List.of(gangnam), search(builder -> builder.region("강남구 개포로")));
		assertEquals(List.of(gangnam), search(builder -> builder.subway("개포")));
		assertEquals(List.of(), search(builder -> builder.region("부산")));
	}

	@Test
	@DisplayName("지역·지하철역 글자의 %·_는 일반 글자로 찾아 모든 매물로 번지지 않는다")
	void searchesWildcardsAsPlainText() {
		save(property -> property.roadAddress("서울특별시 마포구 마포대로 237").nearbySubwayStation("공덕역"));

		assertEquals(List.of(), search(builder -> builder.region("%")));
		assertEquals(List.of(), search(builder -> builder.region("_")));
		assertEquals(List.of(), search(builder -> builder.subway("%")));
		assertEquals(List.of(), search(builder -> builder.subway("_")));
	}

	@Test
	@DisplayName("여러 조건은 모두 맞아야 하고, 조건을 둔 채 커서로 다음 목록을 이어서 본다")
	void combinesConditionsWithCursor() {
		List<Long> expected = new java.util.ArrayList<>();
		for (int index = 0; index < 13; index++) {
			LocalDateTime publishedAt = BASE.plusHours(index);
			expected.add(0, save(property -> property.publishedAt(publishedAt).leaseType(LeaseType.JEONSE)
				.monthlyRent(BigDecimal.ZERO).roadAddress("서울특별시 강남구 개포로 " + publishedAt.getHour())).getPropertyId());
			// 조건에 하나만 안 맞는 매물은 섞여 있어도 빠진다
			save(property -> property.publishedAt(publishedAt).leaseType(LeaseType.JEONSE).monthlyRent(BigDecimal.ZERO)
				.roadAddress("서울특별시 강서구 공항대로 1"));
		}
		var search = PropertyDto.SearchRequest.builder().leaseTypes(List.of(LeaseType.JEONSE)).region("강남구").build();

		var first = propertyService.readProperties(search, null, null);
		var cursor = first.getNextCursor();
		var second = propertyService.readProperties(search, cursor.getCursorPublishedAt(), cursor.getCursorPropertyId());

		assertEquals(expected.subList(0, 12), ids(first));
		assertEquals(expected.subList(12, 13), ids(second));
		assertFalse(second.isHasNext());
	}

	// 공개 중이고 상세 정보를 다 채운 월세 매물. 테스트마다 바꿀 값만 고친다
	private Property save(Consumer<Property.PropertyBuilder> customizer) {
		Property.PropertyBuilder builder = Property.builder().landlord(landlord).propertyType(PropertyType.OFFICETEL)
			.leaseType(LeaseType.MONTHLY).deposit(won(10_000_000)).monthlyRent(won(700_000))
			.roadAddress("서울특별시 마포구 월드컵로 61").detailAddress("302호").areaPyeong(new BigDecimal("12.00"))
			.householdCount(100).parkingCount(1).floor(3).totalFloor(10).roomCount(1).nearbySubwayStation("월드컵경기장역")
			.propertyStatus(PropertyStatus.ACTIVE).publishedAt(BASE);
		customizer.accept(builder);
		return propertyRepository.saveAndFlush(builder.build());
	}

	// 조건 하나로 첫 목록을 찾아 매물 번호를 돌려준다. 같은 공개 시각이라 번호 큰 순서다
	private List<Long> search(Consumer<PropertyDto.SearchRequest.SearchRequestBuilder> customizer) {
		var builder = PropertyDto.SearchRequest.builder();
		customizer.accept(builder);
		return ids(propertyService.readProperties(builder.build(), null, null));
	}

	private static List<Long> ids(PropertyDto.PropertyListResponse response) {
		return response.getProperties().stream().map(PropertyDto.PropertyItemResponse::getPropertyId).toList();
	}

	private static PropertyDto.SearchRequest noSearch() {
		return PropertyDto.SearchRequest.builder().build();
	}

	private static BigDecimal won(long amount) {
		return BigDecimal.valueOf(amount);
	}
}
