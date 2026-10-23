package com.bogeum.dto;

import java.time.Instant;

/** 제공자 사용자 ID와 토큰은 공개하지 않는다. */
public record SocialAccountResponseDTO(String provider, Instant linkedAt) { }
