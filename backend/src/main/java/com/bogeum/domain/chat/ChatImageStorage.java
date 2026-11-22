package com.bogeum.domain.chat;

/**
 * 채팅 사진 저장소 (CHAT_004).
 * S3 설정이 없는 환경에서는 빈이 없어 ChatService가 사진 보내기를 막고, 대화 내역의 사진 주소만 비운다.
 */
public interface ChatImageStorage {
	/** 변환한 JPEG를 서버가 만든 키로 저장한다. */
	void write(String fileKey, byte[] jpeg);
	/** 사진 메시지 저장이 롤백되면 저장을 시도한 키를 정리한다. */
	void delete(String fileKey);
	/** 참여자 확인을 마친 사진에 대해 일정 시간만 열리는 조회 주소를 만든다. */
	String createReadUrl(String fileKey);
}
