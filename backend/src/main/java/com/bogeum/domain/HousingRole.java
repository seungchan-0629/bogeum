package com.bogeum.domain;

/**
 * 계약 당사자의 임대인·임차인 역할. 동일 계정도 계약마다 다른 역할을 맡을 수 있다.
 * 중개사의 위탁·상담 고객 관계와는 별개다.
 */
public enum HousingRole {

	LANDLORD, TENANT
}
