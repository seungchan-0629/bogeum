package com.bogeum.domain.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.LeaseType;
import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.common.exception.ErrorCode;
import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

/** 계약 당사자 규칙을 DB 없이 확인한다. */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private ChatRepository chatRepository;

	private ContractService contractService;
	private final User landlord = User.builder().userId(1L).name("박임대").email("park@example.com").build();

	@BeforeEach
	void setUp() {
		contractService = new ContractService(propertyRepository, chatRepository);
	}

	@Test
	@DisplayName("임대인은 매물의 임대인으로, 임차인 후보는 이 매물로 상담한 채팅방 문의자로 최근 순서대로 준다")
	void readsLandlordAndTenantCandidates() {
		givenAssignedProperty();
		givenTenantChatRooms(tenant(2L, "최세입", "seip@example.com", UserStatus.ACTIVE),
			tenant(3L, "김보금", "b@example.com", UserStatus.ACTIVE));

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(20L, response.getPropertyId());
		assertEquals(LeaseType.MONTHLY, response.getLeaseType());
		assertEquals(new BigDecimal("650000"), response.getMonthlyRent());
		assertEquals("박임대", response.getLandlord().getName());
		assertEquals("pa****@example.com", response.getLandlord().getMaskedEmail());
		assertEquals(List.of(2L, 3L), response.getTenants().stream().map(ContractDto.PartyResponse::getUserId).toList());
		// 앞부분이 두 글자보다 짧으면 있는 글자만 남긴다
		assertEquals("b****@example.com", response.getTenants().get(1).getMaskedEmail());
	}

	@Test
	@DisplayName("탈퇴한 회원과 임대인 본인은 임차인 후보에서 뺀다")
	void excludesWithdrawnUsersAndLandlord() {
		givenAssignedProperty();
		givenTenantChatRooms(tenant(2L, "최세입", "seip@example.com", UserStatus.WITHDRAWN), landlord,
			tenant(3L, "김보금", "bogeum@example.com", UserStatus.ACTIVE));

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(List.of(3L), response.getTenants().stream().map(ContractDto.PartyResponse::getUserId).toList());
	}

	@Test
	@DisplayName("상담한 사람이 없으면 임차인 후보를 빈 목록으로 준다")
	void returnsEmptyTenantsWithoutChatRooms() {
		givenAssignedProperty();
		givenTenantChatRooms();

		var response = contractService.readContractCandidates(101L, 20L);

		assertEquals(List.of(), response.getTenants());
	}

	@Test
	@DisplayName("없거나 다른 중개사가 맡은 매물은 조회할 수 없다")
	void rejectsUnassignedProperty() {
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 999L)).thenReturn(Optional.empty());

		var exception = assertThrows(PropertyException.class, () -> contractService.readContractCandidates(999L, 20L));

		assertEquals(ErrorCode.PROPERTY_NOT_FOUND, exception.getErrorCode());
		verifyNoInteractions(chatRepository);
	}

	private void givenAssignedProperty() {
		Property property = Property.builder().propertyId(20L).landlord(landlord).leaseType(LeaseType.MONTHLY)
			.deposit(new BigDecimal("10000000")).monthlyRent(new BigDecimal("650000"))
			.propertyStatus(PropertyStatus.ACTIVE).build();
		when(propertyRepository.findByPropertyIdAndAgent_UserId(20L, 101L)).thenReturn(Optional.of(property));
	}

	private void givenTenantChatRooms(User... inquirers) {
		when(chatRepository.findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(20L, 101L,
			ChatRoomType.TENANT_AGENT))
			.thenReturn(java.util.Arrays.stream(inquirers)
				.map(inquirer -> ChatRoom.builder().roomType(ChatRoomType.TENANT_AGENT).inquirer(inquirer).build())
				.toList());
	}

	private static User tenant(Long userId, String name, String email, UserStatus userStatus) {
		return User.builder().userId(userId).name(name).email(email).userStatus(userStatus).build();
	}
}
