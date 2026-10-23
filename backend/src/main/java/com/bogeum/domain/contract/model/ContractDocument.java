package com.bogeum.domain.contract.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
 * 계약서 파일. 중개사가 계약서 이미지를 올린다 (PROPERTY_011)
 */
@Entity
@Table(name = "contract_document")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long contractDocumentId;

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
