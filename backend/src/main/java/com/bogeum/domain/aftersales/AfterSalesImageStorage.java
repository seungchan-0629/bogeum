package com.bogeum.domain.aftersales;

/**
 * 비공개 사진 저장소의 연결 지점. S3 구현은 S3AfterSalesImageStorage다.
 * 서비스가 생성한 키로 호출하며 클라이언트가 전달한 저장 키는 받지 않는다.
 */
public interface AfterSalesImageStorage {
	/** 최적화된 JPEG를 서버가 생성한 키로 저장하는 구현체의 진입점이다. */
	void write(String fileKey, byte[] jpeg);
	/** 접수 롤백 시 저장을 시도한 키를 정리하는 구현체의 진입점이다. */
	void delete(String fileKey);
	/** 권한 확인을 마친 사진에 대해 일정 시간만 열리는 조회 주소를 만든다. */
	String createReadUrl(String fileKey);
}
