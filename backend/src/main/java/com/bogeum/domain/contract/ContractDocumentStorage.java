package com.bogeum.domain.contract;

/**
 * 계약서 이미지 저장소의 연결 지점. S3 구현은 S3ContractDocumentStorage다.
 * 계약서에는 개인정보가 있어 매물 사진과 다른 계약서 전용 버킷을 쓴다. 서비스가 만든 키로만 호출한다.
 */
public interface ContractDocumentStorage {
	/** 변환한 JPEG를 서버가 만든 키로 저장한다. */
	void write(String fileKey, byte[] jpeg);
	/** 계약 등록이 롤백되면 저장을 시도한 키를 정리한다. */
	void delete(String fileKey);
}
