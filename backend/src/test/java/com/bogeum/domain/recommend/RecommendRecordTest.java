package com.bogeum.domain.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.recommend.model.SearchLog;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

import jakarta.persistence.EntityManager;

/**
 * 추천에 쓰는 검색·조회 기록이 실제 DB에 규칙대로 쌓이는지 H2로 확인한다 (RECOMMEND_001).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-recommend-record-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecommendService.class, PropertyService.class})
class RecommendRecordTest {

	@Autowired
	private RecommendService recommendService;
	@Autowired
	private SearchLogRepository searchLogRepository;
	@Autowired
	private PropertyViewLogRepository propertyViewLogRepository;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;
	// 기록에 쓰지 않는 매물 서비스의 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User tenant;
	private User landlord;
	private Property property;

	@BeforeEach
	void setUp() {
		tenant = user("김임차", Role.USER);
		landlord = user("박임대", Role.USER);
		property = propertyRepository.saveAndFlush(Property.builder().landlord(landlord).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.roadAddress("서울특별시 마포구 월드컵로 61").propertyStatus(PropertyStatus.ACTIVE).build());
	}

	@Test
	@DisplayName("첫 목록 검색의 지역·지하철역을 앞뒤 공백 없이 남긴다")
	void recordsSearch() {
		recommendService.recordSearch(tenant.getUserId(), " 서울 마포구 ", "합정", true);

		SearchLog log = searchLogs().get(0);
		assertEquals("서울 마포구", log.getRegion());
		assertEquals("합정", log.getSubwayStation());
	}

	@Test
	@DisplayName("지역·역이 없거나, 스크롤로 부른 다음 목록이거나, 중개사의 검색은 남기지 않는다")
	void skipsSearchWithoutRegionOrNotFirstPageOrAgent() {
		recommendService.recordSearch(tenant.getUserId(), " ", null, true);
		recommendService.recordSearch(tenant.getUserId(), "서울 마포구", null, false);
		recommendService.recordSearch(user("이중개", Role.AGENT).getUserId(), "서울 마포구", null, true);

		assertEquals(0, searchLogs().size());
	}

	@Test
	@DisplayName("바로 앞과 같은 검색은 다시 남기지 않는다")
	void skipsSameSearchAsLast() {
		saveSearch("서울 마포구", null, LocalDateTime.now().minusMinutes(5));

		recommendService.recordSearch(tenant.getUserId(), "서울 마포구", null, true);

		assertEquals(1, searchLogs().size());
	}

	@Test
	@DisplayName("10초 안에 이어서 고친 검색은 앞 검색을 덮어써서 입력 중의 글자가 쌓이지 않는다")
	void overwritesSearchWithinTenSeconds() {
		recommendService.recordSearch(tenant.getUserId(), "서울 마", null, true);
		recommendService.recordSearch(tenant.getUserId(), "서울 마포구", null, true);

		List<SearchLog> logs = searchLogs();
		assertEquals(1, logs.size());
		assertEquals("서울 마포구", logs.get(0).getRegion());
	}

	@Test
	@DisplayName("10초가 지난 다른 검색은 새로 남긴다")
	void addsSearchAfterTenSeconds() {
		saveSearch("서울 마포구", null, LocalDateTime.now().minusSeconds(20));

		recommendService.recordSearch(tenant.getUserId(), "서울 강남구", null, true);

		assertEquals(2, searchLogs().size());
	}

	@Test
	@DisplayName("같은 매물은 24시간에 한 번만 조회로 남기고, 24시간이 지나면 다시 남긴다")
	void recordsViewOncePerDay() {
		recommendService.recordView(tenant.getUserId(), property.getPropertyId());
		recommendService.recordView(tenant.getUserId(), property.getPropertyId());
		assertEquals(1, propertyViewLogRepository.count());

		// 앞 조회를 25시간 전으로 옮긴다
		entityManager.createNativeQuery("UPDATE property_view_log SET viewed_at = ?")
			.setParameter(1, LocalDateTime.now().minusHours(25)).executeUpdate();
		entityManager.clear();
		recommendService.recordView(tenant.getUserId(), property.getPropertyId());

		assertEquals(2, propertyViewLogRepository.count());
	}

	@Test
	@DisplayName("임대인이 본인 매물을 본 것과 중개사의 조회는 남기지 않는다")
	void skipsOwnPropertyAndAgentView() {
		recommendService.recordView(landlord.getUserId(), property.getPropertyId());
		recommendService.recordView(user("이중개", Role.AGENT).getUserId(), property.getPropertyId());

		assertEquals(0, propertyViewLogRepository.count());
	}

	private void saveSearch(String region, String subwayStation, LocalDateTime searchedAt) {
		searchLogRepository.saveAndFlush(SearchLog.builder().user(tenant).region(region).subwayStation(subwayStation)
			.searchedAt(searchedAt).build());
	}

	private List<SearchLog> searchLogs() {
		entityManager.flush();
		entityManager.clear();
		return searchLogRepository.findAll();
	}

	private User user(String name, Role role) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(role).build());
	}
}
