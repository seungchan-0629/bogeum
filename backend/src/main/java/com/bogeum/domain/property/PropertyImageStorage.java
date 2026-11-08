package com.bogeum.domain.property;

/**
 * 매물 사진 저장소의 연결 지점. S3 구현은 S3PropertyImageStorage다.
 * AS 사진과 다른 매물 전용 버킷을 쓴다. 서비스가 만든 키로만 호출하며 클라이언트가 보낸 키는 받지 않는다.
 */
public interface PropertyImageStorage {
	/** 변환한 JPEG를 서버가 만든 키로 저장한다. */
	void write(String fileKey, byte[] jpeg);
	/** 사진 추가가 롤백되면 저장을 시도한 키를 정리하고, 사진 삭제가 커밋되면 그 키를 지운다. */
	void delete(String fileKey);
	/** 권한 확인을 마친 사진에 대해 일정 시간만 열리는 조회 주소를 만든다. */
	String createReadUrl(String fileKey);
}
