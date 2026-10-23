package com.bogeum.domain.movein.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.bogeum.domain.contract.model.Contract;
import com.bogeum.domain.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입주 하자 사진. 계약 체결 후 1주 이내에만 올릴 수 있다 (MOVEIN_001)
 */
@Entity
@Table(name = "movein_defect_photo")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveInDefectPhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "movein_defect_photo_id")
	private Long moveInDefectPhotoId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "contract_id", nullable = false)
	private Contract contract;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private User uploadedBy;

	@Column(nullable = false, length = 500)
	private String fileKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
