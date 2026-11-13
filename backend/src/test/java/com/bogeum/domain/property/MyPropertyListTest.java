package com.bogeum.domain.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

import com.bogeum.common.enums.ApprovalStatus;
import com.bogeum.common.enums.BrokerageStatus;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.agent.AgentRepository;
import com.bogeum.domain.agent.model.Agent;
import com.bogeum.domain.property.model.BrokerageRequest;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.UserService;
import com.bogeum.domain.user.model.User;
import com.bogeum.utils.ImageProcessor;

/**
 * 내 매물 목록이 실제 DB에서 본인 매물만, 삭제한 매물은 빼고, 최근 신청 순서로 나오는지 H2로 확인한다 (PROPERTY_013).
 */
@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bogeum-my-property-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PropertyService.class)
class MyPropertyListTest {

	@Autowired
	private PropertyService propertyService;
	@Autowired
	private PropertyRepository propertyRepository;
	@Autowired
	private BrokerageRequestRepository brokerageRequestRepository;
	@Autowired
	private AgentRepository agentRepository;
	@Autowired
	private UserRepository userRepository;
	// 목록 조회에 쓰지 않는 의존성
	@MockitoBean
	private UserService userService;
	@MockitoBean
	private ImageProcessor imageProcessor;

	private User landlord;
	private User agentWithOffice;
	private User agentWithoutOffice;

	@BeforeEach
	void setUp() {
		landlord = user("박임대", Role.USER);
		agentWithOffice = user("김중개", Role.AGENT);
		agentWithoutOffice = user("이중개", Role.AGENT);
		agentRepository.saveAndFlush(Agent.builder().user(agentWithOffice).officeName("망원 행복공인중개사사무소")
			.officeAddress("서울특별시 마포구 월드컵로 61").approvalStatus(ApprovalStatus.APPROVED).build());
	}

	@Test
	@DisplayName("본인이 신청한 매물만 최근 신청 순서로 주고, 삭제한 매물은 빼고 숨김 매물은 남긴다")
	void readsOwnPropertiesExceptDeleted() {
		Long waiting = request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.REQUESTED, agentWithOffice);
		request(landlord, PropertyStatus.DELETED, BrokerageStatus.ACCEPTED, agentWithOffice);
		Long hidden = request(landlord, PropertyStatus.HIDDEN, BrokerageStatus.ACCEPTED, agentWithOffice);
		Long active = request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED, agentWithoutOffice);
		request(user("오임대", Role.USER), PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED, agentWithOffice);

		var response = propertyService.readMyProperties(landlord.getUserId(), 1);

		assertEquals(List.of(active, hidden, waiting),
			response.getProperties().stream().map(PropertyDto.MyPropertyItemResponse::getPropertyId).toList());
		assertEquals(3L, response.getTotalElements());
	}

	@Test
	@DisplayName("담당 중개사의 사무소 이름을 붙이고, 사무소 정보가 없는 중개사는 이름만 준다")
	void attachesOfficeName() {
		request(landlord, PropertyStatus.REQUESTED, BrokerageStatus.REQUESTED, agentWithOffice);
		request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED, agentWithoutOffice);

		var properties = propertyService.readMyProperties(landlord.getUserId(), 1).getProperties();

		assertEquals("이중개", properties.get(0).getAgentName());
		assertNull(properties.get(0).getOfficeName());
		assertEquals("김중개", properties.get(1).getAgentName());
		assertEquals("망원 행복공인중개사사무소", properties.get(1).getOfficeName());
		assertEquals(BrokerageStatus.REQUESTED, properties.get(1).getBrokerageStatus());
		assertEquals("302호", properties.get(1).getDetailAddress());
	}

	@Test
	@DisplayName("내 매물이 10개를 넘으면 다음 페이지로 나뉜다")
	void splitsIntoPages() {
		for (int index = 0; index < 11; index++) {
			request(landlord, PropertyStatus.ACTIVE, BrokerageStatus.ACCEPTED, agentWithOffice);
		}

		var first = propertyService.readMyProperties(landlord.getUserId(), 1);
		var second = propertyService.readMyProperties(landlord.getUserId(), 2);

		assertEquals(10, first.getProperties().size());
		assertEquals(1, second.getProperties().size());
		assertEquals(2, first.getTotalPages());
	}

	// 중개 신청처럼 매물과 신청을 한 쌍으로 저장한다. 매물 번호를 돌려준다
	private Long request(User owner, PropertyStatus propertyStatus, BrokerageStatus brokerageStatus, User agent) {
		Property property = propertyRepository.saveAndFlush(Property.builder().landlord(owner).leaseType(LeaseType.MONTHLY)
			.deposit(BigDecimal.valueOf(10_000_000)).monthlyRent(BigDecimal.valueOf(700_000))
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
