package com.bogeum.domain.contract;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.ChatRoomType;
import com.bogeum.common.enums.UserStatus;
import com.bogeum.domain.chat.ChatRepository;
import com.bogeum.domain.chat.model.ChatRoom;
import com.bogeum.domain.contract.model.ContractDto;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContractService {

	private final PropertyRepository propertyRepository;
	private final ChatRepository chatRepository;

	/**
	 * 중개사가 맡은 매물의 계약 등록 화면 첫 값을 조회한다 (PROPERTY_011).
	 * 임대인은 중개를 신청한 매물의 임대인이고, 임차인 후보는 이 매물로 본인과 상담한 채팅방의 문의자다.
	 *
	 * @throws PropertyException 없거나 본인이 맡은 매물이 아닌 경우
	 */
	@Transactional(readOnly = true)
	public ContractDto.CandidateResponse readContractCandidates(Long agentUserId, Long propertyId) {
		Property property = propertyRepository.findByPropertyIdAndAgent_UserId(propertyId, agentUserId)
			.orElseThrow(PropertyException::propertyNotFound);
		Long landlordUserId = property.getLandlord().getUserId();
		// 탈퇴한 회원과는 계약할 수 없고, 임대인이 같은 매물의 임차인이 될 수는 없다
		List<User> tenants = chatRepository
			.findAllByProperty_PropertyIdAndAgent_UserIdAndRoomTypeOrderByCreatedAtDesc(propertyId, agentUserId,
				ChatRoomType.TENANT_AGENT)
			.stream()
			.map(ChatRoom::getInquirer)
			.filter(user -> user.getUserStatus() == UserStatus.ACTIVE && !user.getUserId().equals(landlordUserId))
			.toList();
		return ContractDto.CandidateResponse.from(property, tenants);
	}
}
