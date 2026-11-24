package com.bogeum.common.enums;

/**
 * 채팅 신고 처리 상태 (CHAT_001). 신고한 사람 화면에 관리자가 확인했는지 보여 준다
 */
public enum ChatReportStatus {
	RECEIVED, // 접수
	CHECKED, // 관리자가 대화를 열람함
	RESOLVED // 처리 완료. 같은 방을 다시 신고할 수 있다
}
