package com.bogeum.common.enums;

/**
 * 채팅 상대와의 차단 상태 (CHAT_010). 화면이 입력창 자리의 안내 문구를 고른다
 */
public enum ChatBlockStatus {
	NONE, // 차단 없음
	BLOCKED_BY_ME, // 내가 상대를 차단함
	BLOCKED_ME // 상대가 나를 차단함
}
