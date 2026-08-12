package com.co.kc.imchat.plugin.session.token;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void createsAndParsesTokenPayload() {
        JwtTokenService tokenService = new JwtTokenService();
        TokenDTO source = new TokenDTO(1001L, LocalDateTime.of(2026, 6, 14, 12, 0));

        String token = tokenService.create(source);

        assertThat(token).isNotBlank();
        assertThat(tokenService.parse(token)).isEqualTo(source);
    }

    @Test
    void returnsNullForBlankOrInvalidToken() {
        JwtTokenService tokenService = new JwtTokenService();

        assertThat(tokenService.parse("")).isNull();
        assertThat(tokenService.parse("bad-token")).isNull();
    }
}
