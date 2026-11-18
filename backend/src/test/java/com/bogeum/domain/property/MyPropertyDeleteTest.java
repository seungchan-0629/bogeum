package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
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

import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.ChatService;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

import jakarta.persistence.EntityManager;

/**
 * 임대인 매물 삭제가 실제 DB에서 상태만 바꾸고, 임대인·중개사 화면에 맞게 보이는지 H2로 확인한다 (PROPERTY_007).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-my-property-delete-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PropertyService.class, ChatService.class})
class MyPropertyDeleteTest {

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
	// 삭제에 쓰지 않는 의존성
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
	@DisplayName("공개 중인 매물을 삭제하면 행은 남고 상태만 바뀌어 내 매물 목록과 공개 상세에서 빠진다")
	void deletesActiveProperty() {
		Long propertyId = request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED);

		propertyService.deleteMyProperty(landlord.getUserId(), propertyId);

		assertEquals(PropertyStatus.DELETED, reload(propertyId).getPropertyStatus());
		assertEquals(0L, propertyService.readMyProperties(landlord.getUserId(), 1).getTotalElements());
		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND, () -> propertyService.readPublicProperty(propertyId));
	}

	@Test
	@DisplayName("공개 전·숨김 매물도 삭제할 수 있다")
	void deletesRequestedAndHiddenProperty() {
		Long requested = request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.REQUESTED);
		Long hidden = request(landlord, PropertyStatus.HIDDEN, BrokerageStatus.ACCEPTED);

		propertyService.deleteMyProperty(landlord.getUserId(), requested);
		propertyService.deleteMyProperty(landlord.getUserId(), hidden);

		assertEquals(PropertyStatus.DELETED, reload(requested).getPropertyStatus());
		assertEquals(PropertyStatus.DELETED, reload(hidden).getPropertyStatus());
	}

	@Test
	@DisplayName("계약이 끝난 매물은 PROPERTY_NOT_DELETABLE로 거절하고 상태를 바꾸지 않는다")
	void rejectsContractedProperty() {
		Long propertyId = request(landlord, PropertyStatus.CONTRACTED, BrokerageStatus.ACCEPTED);

		assertErrorCode(ErrorCode.PROPERTY_NOT_DELETABLE,
			() -> propertyService.deleteMyProperty(landlord.getUserId(), propertyId));
		assertEquals(PropertyStatus.CONTRACTED, reload(propertyId).getPropertyStatus());
	}

	@Test
	@DisplayName("남의 매물과 이미 삭제한 매물은 PROPERTY_NOT_FOUND로 거절한다")
	void rejectsOthersOrDeletedProperty() {
		Long othersId = request(user("오임대", Role.USER), PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED);
		Long deletedId = request(landlord, PropertyStatus.DELETED, BrokerageStatus.ACCEPTED);

		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
			() -> propertyService.deleteMyProperty(landlord.getUserId(), othersId));
		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
			() -> propertyService.deleteMyProperty(landlord.getUserId(), deletedId));
	}

	@Test
	@DisplayName("중개사의 새 신청에서는 삭제된 매물이 빠지고 수락할 수 없다")
	void hidesDeletedFromRequestedAndBlocksAccept() {
		Long kept = request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.REQUESTED);
		Long deleted = request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.REQUESTED);
		propertyService.deleteMyProperty(landlord.getUserId(), deleted);
		Long deletedRequestId = brokerageRequestIdOf(deleted);

		var response = propertyService.readBrokerageRequests(agent.getUserId(), BrokerageStatus.REQUESTED, 1, false);

		assertEquals(List.of(kept), propertyIdsOf(response));
		assertErrorCode(ErrorCode.BROKERAGE_REQUEST_NOT_FOUND,
			() -> propertyService.acceptBrokerageRequest(agent.getUserId(), deletedRequestId));
	}

	@Test
	@DisplayName("수락한 신청은 삭제된 매물도 기록으로 남기고, excludeDeleted면 내 계약 관리처럼 뺀다")
	void keepsDeletedInAcceptedUnlessExcluded() {
		Long kept = request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED);
		Long deleted = request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED);
		propertyService.deleteMyProperty(landlord.getUserId(), deleted);

		var history = propertyService.readBrokerageRequests(agent.getUserId(), BrokerageStatus.ACCEPTED, 1, false);
		var contracts = propertyService.readBrokerageRequests(agent.getUserId(), BrokerageStatus.ACCEPTED, 1, true);

		assertEquals(2L, history.getTotalElements());
		assertEquals(PropertyStatus.DELETED, history.getRequests().stream()
			.filter(item -> item.getPropertyId().equals(deleted)).findFirst().orElseThrow().getPropertyStatus());
		assertEquals(List.of(kept), propertyIdsOf(contracts));
	}

	@Test
	@DisplayName("담당 중개사도 삭제된 매물의 상세 조회·정보 저장·공개를 할 수 없다")
	void blocksAgentFromDeletedProperty() {
		Long propertyId = request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.ACCEPTED);
		propertyService.deleteMyProperty(landlord.getUserId(), propertyId);

		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
			() -> propertyService.readPropertyDetail(agent.getUserId(), propertyId));
		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND, () -> propertyService.updatePropertyDetail(agent.getUserId(),
			propertyId, new PropertyDto.DetailRequest()));
		assertErrorCode(ErrorCode.PROPERTY_NOT_FOUND,
			() -> propertyService.publishProperty(agent.getUserId(), propertyId));
	}

	private List<Long> propertyIdsOf(PropertyDto.BrokerageListResponse response) {
		return response.getRequests().stream().map(PropertyDto.BrokerageItemResponse::getPropertyId).toList();
	}

	private Long brokerageRequestIdOf(Long propertyId) {
		return brokerageRequestRepository.findAll().stream()
			.filter(request -> request.getProperty().getPropertyId().equals(propertyId))
			.findFirst().orElseThrow().getBrokerageRequestId();
	}

	// 바뀐 상태가 DB에 저장됐는지 보려고 영속성 컨텍스트를 비우고 다시 읽는다
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
	private Long request(User owner, PropertyStatus propertyStatus, BrokerageStatus brokerageStatus) {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(owner).agent(agent)
			.leaseType(LeaseType.MONTHLY).deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
			.roadAddress("서울특별시 마포구 월드컵로 61").detailAddress("302호").propertyStatus(propertyStatus).build());
		brokerageRequestRepository.saveAndFlush(BrokerageRequest.builder().property(property).landlord(owner).agent(agent)
			.isDesignated(true).brokerageStatus(brokerageStatus).build());
		return property.getPropertyId();
	}

	private User user(String name, Role role) {
		return userRepository.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash")
			.name(name).phone("010" + (int) (Math.random() * 100_000_000)).role(role).build());
	}
}
