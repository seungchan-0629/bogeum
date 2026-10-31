package com.bogeum.domain.settlement.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 임대인 정산 계좌 (USER_015)
 */
@Entity
@Table(name = "settlement_account")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long settlementAccountId;

	// 임대인당 1개
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "landlord_id", nullable = false, unique = true)
	private User landlord;

	@Column(nullable = false, length = 10)
	private String bankCode;

	@Column(nullable = false)
	private String accountNumberEncrypted;

	// 화면에 보여줄 때 쓴다
	@Column(nullable = false, length = 30)
	private String accountNumberMasked;

	@Column(nullable = false, length = 50)
	private String holderName;

	// 기존 미검증 계좌는 null로 남겨 수납·지급에 사용하지 않는다.
	private LocalDateTime verifiedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public void changeAccount(String bankCode, String encrypted, String masked, String holderName) {
		this.bankCode = bankCode;
		this.accountNumberEncrypted = encrypted;
		this.accountNumberMasked = masked;
		this.holderName = holderName;
		this.verifiedAt = null;
	}

	public void markVerified(LocalDateTime verifiedAt) {
		this.verifiedAt = verifiedAt;
	}
}
