package com.bogeum.domain.openbanking.FakeKFTC;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, StateData> store =
            new ConcurrentHashMap<>();

    // 인증 시작 시 state와 로그인한 사용자의 ID 저장
    public void save(String state, Long userId) {

        if (state == null || !state.matches("[a-zA-Z0-9]{32}")) {
            throw new IllegalArgumentException(
                    "state는 영문·숫자 32자리여야 합니다."
            );
        }

        if (userId == null) {
            throw new IllegalArgumentException("userId가 필요합니다.");
        }

        Instant now = Instant.now();

        // 인증을 끝내지 않은 요청도 계속 쌓이지 않도록 만료된 데이터 정리
        store.entrySet().removeIf(
                entry -> !now.isBefore(entry.getValue().expiresAt)
        );

        StateData previous = store.putIfAbsent(
                state,
                new StateData(userId, now.plus(STATE_TTL))
        );

        if (previous != null) {
            throw new IllegalStateException("이미 저장된 state입니다.");
        }
    }

    // 콜백으로 받은 state에 해당하는 userId 반환 후 삭제
    public Long consume(String state) {

        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "state가 없습니다."
            );
        }

        // 값을 가져오면서 삭제 → 같은 state는 한 번만 사용 가능
        StateData data = store.remove(state);

        if (data == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "유효하지 않거나 이미 사용한 state입니다."
            );
        }

        if (!Instant.now().isBefore(data.expiresAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "인증 요청이 만료되었습니다. 다시 시작해 주세요."
            );
        }

        return data.userId;
    }

    // state에 연결해 저장할 정보
    private static class StateData {

        private final Long userId;
        private final Instant expiresAt;

        private StateData(Long userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
