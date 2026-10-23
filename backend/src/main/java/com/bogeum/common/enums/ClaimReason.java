package com.bogeum.common.enums;

/**
 * 클레임 사유 (CLAIM_001)
 */
public enum ClaimReason {
	RENT_UNPAID, // 월세 미납 (임대인이 접수)
	BAD_TENANT, // 악성 세입자 (임대인이 접수)
	AS_NO_RESPONSE, // AS 요청 미응답 (임차인이 접수)
	LISTING_MISMATCH // 실제 매물과 다름 (임차인이 접수)
}
