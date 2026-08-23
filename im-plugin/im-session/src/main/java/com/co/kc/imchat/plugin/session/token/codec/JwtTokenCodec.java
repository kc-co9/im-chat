package com.co.kc.imchat.plugin.session.token.codec;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.plugin.session.token.model.DecodedToken;
import com.co.kc.imchat.plugin.session.token.model.TokenClaims;
import com.co.kc.imchat.plugin.session.token.model.TokenType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** Encodes and verifies transport-neutral session token claims. */
public final class JwtTokenCodec {

    private static final int TOKEN_ID_BYTES = 32;
    private static final String USER_ID = "userId";
    private static final String SESSION_VERSION = "sessionVersion";
    private static final String TYPE = "type";

    private final Key signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public JwtTokenCodec(
            Key signingKey,
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Clock clock) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
        this.issuer = requireText(issuer, "issuer");
        this.accessTokenTtl = requirePositive(accessTokenTtl, "accessTokenTtl");
        this.refreshTokenTtl = requirePositive(refreshTokenTtl, "refreshTokenTtl");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String encode(TokenClaims claims) {
        Objects.requireNonNull(claims, "claims must not be null");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetimeFor(claims.tokenType()));
        return Jwts.builder()
                .setIssuer(issuer)
                .setId(GeneratorUtils.nextRandomId(TOKEN_ID_BYTES))
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .claim(USER_ID, claims.userId())
                .claim(SESSION_VERSION, claims.sessionVersion())
                .claim(TYPE, claims.tokenType().name().toLowerCase(Locale.ROOT))
                .signWith(SignatureAlgorithm.HS512, signingKey)
                .compact();
    }

    public DecodedToken decode(String token, TokenType expectedTokenType) {
        Objects.requireNonNull(expectedTokenType, "expectedTokenType must not be null");
        Claims claims = Jwts.parser()
                .setSigningKey(signingKey)
                .requireIssuer(issuer)
                .setClock(() -> Date.from(clock.instant()))
                .parseClaimsJws(token)
                .getBody();
        TokenType actualTokenType = parseTokenType(claims.get(TYPE, String.class));
        if (actualTokenType != expectedTokenType) {
            throw new JwtException("unexpected token type");
        }
        Number userId = claims.get(USER_ID, Number.class);
        return new DecodedToken(
                userId.longValue(),
                claims.get(SESSION_VERSION, String.class),
                actualTokenType,
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    private Duration lifetimeFor(TokenType tokenType) {
        return tokenType == TokenType.ACCESS ? accessTokenTtl : refreshTokenTtl;
    }

    private TokenType parseTokenType(String value) {
        if (value == null) {
            throw new JwtException("missing token type");
        }
        try {
            return TokenType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JwtException("invalid token type", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
