package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

import jakarta.persistence.EntityManager;

/**
 * 임대인 매물 수정이 실제 DB에서 본인 매물만, 계약·삭제 전까지만 바뀌는지 H2로 확인한다 (PROPERTY_006).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-my-property-update-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PropertyService.class)
class MyPropertyUpdateTest {

	@Autowired
	private PropertyService propertyService;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private BrokerageRequestRepository brokerageRequestRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EntityManager entityManager;
	// 수정에 쓰지 않는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User landlord;
	private User agent;

	@BeforeEach
	void setUp() {
		landlord = user("박임대", Role.USER);
		agent = user("김중개", Role.AGENT);
	}

	@Test
	@DisplayName("수정 화면 첫 값으로 본인 매물의 거래 조건·주소·진행 상태를 준다")
	void readsMyProperty() {
		Long propertyId = request(landlord, PropertyStatus.ACTIVE);

		var response = propertyService.readMyProperty(landlord.getUserId(), propertyId);

		assertEquals(LeaseType.MONTHLY, response.getLeaseType());
		assertEquals(0, BigDecimal.valueOf(700_000).compareTo(response.getMonthlyRent()));
		assertEquals("302호", response.getDetailAddress());
		assertEquals(BrokerageStatus.ACCEPTED, response.getBrokerageStatus());
		assertTrue(response.getIsUpdatable());
	}

	@Test
	@DisplayName("계약이 끝난 매물은 조회는 되지만 수정할 수 없다고 알려 준다")
	void readsContractedPropertyAsNotUpdatable() {
		Long propertyId = request(landlord, PropertyStatus.CONTRACTED);

		assertFalse(propertyService.readMyProperty(landlord.getUserId(), propertyId).getIsUpdatable());
	}

	@Test
	@DisplayName("남의 매물과 삭제한 매물은 조회·수정 모두 PROPERTY_NOT_FOUND로 거절한다")
	void rejectsOthersOrDeletedProperty() {
		Long othersId = request(user("오임대", Role.USER), PropertyStatus.ACTIVE);
		Long deletedId = request(landlord, PropertyStatus.DELETED);

		for (Long propertyId : new Long[] {othersId, deletedId}) {
			assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
				() -> propertyService.readMyProperty(landlord.getUserId(), propertyId));
			assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
				() -> propertyService.updateMyProperty(landlord.getUserId(), propertyId, monthly(700_000)));
		}
	}

	@Test
	@DisplayName("공개 중인 매물의 거래 조건을 바꾸면 공개 상태는 그대로이고 공개 상세에도 바뀐 값이 보인다")
	void updatesActiveProperty() {
		Long propertyId = request(landlord, PropertyStatus.ACTIVE);

		propertyService.updateMyProperty(landlord.getUserId(), propertyId,
			new PropertyDto.LeaseTermsRequest(LeaseType.MONTHLY, BigDecimal.valueOf(20_000_000), BigDecimal.valueOf(650_000)));
		Property property = reload(propertyId);

		assertEquals(0, BigDecimal.valueOf(20_000_000).compareTo(property.getDeposit()));
		assertEquals(0, BigDecimal.valueOf(650_000).compareTo(property.getMonthlyRent()));
		assertEquals(PropertyStatus.ACTIVE, property.getPropertyStatus());
		assertEquals("서울특별시 마포구 월드컵로 61", property.getRoadAddress());
	}

	@Test
	@DisplayName("전세로 바꾸면 월세를 비워 보내도 0원으로 저장한다")
	void storesZeroRentForJeonse() {
		Long propertyId = request(landlord, PropertyStatus.REQUESTED);

		propertyService.updateMyProperty(landlord.getUserId(), propertyId,
			new PropertyDto.LeaseTermsRequest(LeaseType.JEONSE, BigDecimal.valueOf(300_000_000), null));
		Property property = reload(propertyId);

		assertEquals(LeaseType.JEONSE, property.getLeaseType());
		assertEquals(0, property.getMonthlyRent().signum());
	}

	@Test
	@DisplayName("관리자가 숨긴 매물도 고칠 수 있고 숨김 상태는 그대로다")
	void updatesHiddenPropertyKeepingHidden() {
		Long propertyId = request(landlord, PropertyStatus.HIDDEN);

		propertyService.updateMyProperty(landlord.getUserId(), propertyId, monthly(500_000));

		assertEquals(PropertyStatus.HIDDEN, reload(propertyId).getPropertyStatus());
	}

	@Test
	@DisplayName("계약이 끝난 매물은 PROPERTY_NOT_UPDATABLE로 거절하고 값을 바꾸지 않는다")
	void rejectsContractedProperty() {
		Long propertyId = request(landlord, PropertyStatus.CONTRACTED);

		assertErrorCode(ErrorCode.PROPERTY_NOT_UPDATABLE,
			() -> propertyService.updateMyProperty(landlord.getUserId(), propertyId, monthly(500_000)));
		assertEquals(0, BigDecimal.valueOf(700_000).compareTo(reload(propertyId).getMonthlyRent()));
	}

	@Test
	@DisplayName("임대 형식에 맞지 않는 가격은 중개 신청과 같이 INVALID_LEASE_PRICE로 거절한다")
	void rejectsLeasePriceMismatch() {
		Long propertyId = request(landlord, PropertyStatus.ACTIVE);

		assertErrorCode(ErrorCode.INVALID_LEASE_PRICE,
			() -> propertyService.updateMyProperty(landlord.getUserId(), propertyId, monthly(0)));
		assertErrorCode(ErrorCode.INVALID_LEASE_PRICE, () -> propertyService.updateMyProperty(landlord.getUserId(),
			propertyId, new PropertyDto.LeaseTermsRequest(LeaseType.JEONSE, BigDecimal.valueOf(300_000_000),
				BigDecimal.valueOf(100_000))));
	}

	private PropertyDto.LeaseTermsRequest monthly(long rent) {
		return new PropertyDto.LeaseTermsRequest(LeaseType.MONTHLY, BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(rent));
	}

	// 바뀐 값이 DB에 저장됐는지 보려고 영속성 컨텍스트를 비우고 다시 읽는다
	private Property reload(Long propertyId) {
		entityManager.flush();
		entityManager.clear();
		return propertyRepository.findById(propertyId).orElseThrow();
	}

	private void assertErrorCode(ErrorCode expected, Runnable action) {
		PropertyException exception = assertThrows(PropertyException.class, action::run);
		assertEquals(expected, exception.getErrorCode());
	}

	// 중개 신청처럼 매물과 신청을 한 쌍으로 저장한다. 매물 번호를 돌려준다
	private Long request(User owner, PropertyStatus propertyStatus) {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(owner).agent(agent)
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.roadAddress("서울특별시 마포구 월드컵로 61").detailAddress("302호").propertyStatus(propertyStatus).build());
		brokerageRequestRepository.saveAndFlush(BrokerageRequest.builder().property(property).landlord(owner).agent(agent)
			.isDesignated(true).brokerageStatus(BrokerageStatus.ACCEPTED).build());
		return property.getPropertyId();
	}

	private User user(String name, Role role) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(role).build());
	}
}
