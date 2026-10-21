package com.bogeum.domain;

import java.util.Optional;

import org.hibernate.annotations.Check;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계약의 두 당사자. 같은 회원도 계약에 따라 다른 주거 역할을 맡을 수 있다.
 */
@Entity
@Table(name = "housing_contracts")
@Check(constraints = "landlord_member_id <> tenant_member_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "landlord_member_id", nullable = false)
	private Member landlord;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_member_id", nullable = false)
	private Member tenant;

	public Contract(Member landlord, Member tenant) {
		if (landlord == null || tenant == null || landlord.getId() == null || tenant.getId() == null) {
			throw new IllegalArgumentException("계약 당사자는 저장된 회원이어야 합니다.");
		}
		if (landlord.getId().equals(tenant.getId())) {
			throw new IllegalArgumentException("같은 회원이 한 계약의 양쪽 당사자가 될 수 없습니다.");
		}
		this.landlord = landlord;
		this.tenant = tenant;
	}

	public Optional<HousingRole> roleOf(Long memberId) {
		if (memberId == null) {
			return Optional.empty();
		}
		if (memberId.equals(landlord.getId())) {
			return Optional.of(HousingRole.LANDLORD);
		}
		if (memberId.equals(tenant.getId())) {
			return Optional.of(HousingRole.TENANT);
		}
		return Optional.empty();
	}
}
