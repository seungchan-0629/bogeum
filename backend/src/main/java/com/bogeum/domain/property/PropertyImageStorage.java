package com.bogeum.domain.property;

/**
 * 매물 사진 저장소의 연결 지점. S3 구현은 S3PropertyImageStorage다.
 * AS 사진과 다른 매물 전용 버킷을 쓴다.
 */
public interface PropertyImageStorage {
	/** 권한 확인을 마친 사진에 대해 일정 시간만 열리는 조회 주소를 만든다. */
	String createReadUrl(String fileKey);
}
