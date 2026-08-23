package com.co.kc.imchat.plugin.session.token.codec;

import com.co.kc.imchat.plugin.session.token.model.DecodedToken;
import com.co.kc.imchat.plugin.session.token.model.TokenClaims;
import com.co.kc.imchat.plugin.session.token.model.TokenType;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenCodecTest {

    private static final Instant NOW = Instant.parse("2026-08-14T04:00:00Z");
    private static final String ISSUER = "im-account-test";
    private static final Duration ACCESS_TTL = Duration.ofHours(2);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Key KEY = key("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void encodesAndDecodesAccessClaimsWithDeterministicTimes() {
        JwtTokenCodec codec = codec(KEY, ISSUER);
        TokenClaims claims = new TokenClaims(1001L, "session-v1", TokenType.ACCESS);

        String token = codec.encode(claims);
        DecodedToken decoded = codec.decode(token, TokenType.ACCESS);

        assertThat(decoded.userId()).isEqualTo(1001L);
        assertThat(decoded.sessionVersion()).isEqualTo("session-v1");
        assertThat(decoded.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(decoded.tokenId()).isNotBlank();
        assertThat(decoded.issuedAt()).isEqualTo(NOW);
        assertThat(decoded.expiresAt()).isEqualTo(NOW.plus(ACCESS_TTL));
    }

    @Test
    void refreshTokenUsesRefreshLifetime() {
        JwtTokenCodec codec = codec(KEY, ISSUER);

        DecodedToken decoded = codec.decode(codec.encode(
                new TokenClaims(1001L, "session-v1", TokenType.REFRESH)), TokenType.REFRESH);

        assertThat(decoded.expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtTokenCodec issuer = codec(KEY, ISSUER);
        JwtTokenCodec verifier = codec(key("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"), ISSUER);

        String token = issuer.encode(new TokenClaims(1001L, "session-v1", TokenType.ACCESS));

        assertThatThrownBy(() -> verifier.decode(token, TokenType.ACCESS)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromDifferentIssuer() {
        String token = codec(KEY, ISSUER).encode(
                new TokenClaims(1001L, "session-v1", TokenType.ACCESS));

        assertThatThrownBy(() -> codec(KEY, "another-issuer").decode(token, TokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = codec(KEY, ISSUER).encode(
                new TokenClaims(1001L, "session-v1", TokenType.ACCESS));
        Clock expiredClock = Clock.fixed(NOW.plus(ACCESS_TTL).plusSeconds(1), ZoneOffset.UTC);
        JwtTokenCodec expiredVerifier = new JwtTokenCodec(
                KEY, ISSUER, ACCESS_TTL, REFRESH_TTL, expiredClock);

        assertThatThrownBy(() -> expiredVerifier.decode(token, TokenType.ACCESS)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnexpectedTokenType() {
        JwtTokenCodec codec = codec(KEY, ISSUER);
        String token = codec.encode(new TokenClaims(1001L, "session-v1", TokenType.REFRESH));

        assertThatThrownBy(() -> codec.decode(token, TokenType.ACCESS))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("token type");
    }

    @Test
    void generatesIndependentTokenIdForEachEncoding() {
        JwtTokenCodec codec = codec(KEY, ISSUER);
        TokenClaims claims = new TokenClaims(1001L, "session-v1", TokenType.ACCESS);

        DecodedToken first = codec.decode(codec.encode(claims), TokenType.ACCESS);
        DecodedToken second = codec.decode(codec.encode(claims), TokenType.ACCESS);

        assertThat(first.tokenId()).isNotBlank().isNotEqualTo(second.tokenId());
    }

    private JwtTokenCodec codec(Key key, String issuer) {
        return new JwtTokenCodec(key, issuer, ACCESS_TTL, REFRESH_TTL, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Key key(String value) {
        return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS512.getJcaName());
    }
}
