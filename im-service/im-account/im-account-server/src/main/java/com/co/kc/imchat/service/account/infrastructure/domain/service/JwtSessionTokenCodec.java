package com.co.kc.imchat.service.account.infrastructure.domain.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.plugin.session.token.model.TokenClaims;
import com.co.kc.imchat.plugin.session.token.model.TokenType;
import com.co.kc.imchat.service.account.domain.session.model.AccessToken;
import com.co.kc.imchat.service.account.domain.session.model.DecodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.EncodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.RefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.service.SessionTokenCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

public final class JwtSessionTokenCodec implements SessionTokenCodec {
    private final JwtTokenCodec codec;
    private final byte[] fingerprintKey;

    public JwtSessionTokenCodec(JwtTokenCodec codec, byte[] fingerprintKey) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fingerprintKey = Objects.requireNonNull(fingerprintKey, "fingerprintKey").clone();
        if (fingerprintKey.length < 32) {
            throw new IllegalArgumentException("fingerprintKey must contain at least 32 bytes");
        }
    }

    @Override
    public EncodedSessionToken encodeAccess(UserId userId, SessionVersion version) {
        return encode(userId, version, TokenType.ACCESS);
    }

    @Override
    public EncodedSessionToken encodeRefresh(UserId userId, SessionVersion version) {
        return encode(userId, version, TokenType.REFRESH);
    }

    @Override
    public Optional<DecodedSessionToken> decodeAccess(AccessToken token) {
        return decode(token.value(), TokenType.ACCESS);
    }

    @Override
    public Optional<DecodedSessionToken> decodeRefresh(RefreshToken token) {
        return decode(token.value(), TokenType.REFRESH);
    }

    private EncodedSessionToken encode(UserId userId, SessionVersion version, TokenType type) {
        String value = codec.encode(new TokenClaims(userId.value(), version.value(), type));
        return new EncodedSessionToken(value, codec.decode(value, type).expiresAt());
    }

    private Optional<DecodedSessionToken> decode(String token, TokenType type) {
        try {
            com.co.kc.imchat.plugin.session.token.model.DecodedToken decoded =
                    codec.decode(token, type);
            return Optional.of(new DecodedSessionToken(
                    new UserId(decoded.userId()),
                    new SessionVersion(decoded.sessionVersion()),
                    decoded.expiresAt()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public RefreshFingerprint fingerprint(RefreshToken refreshToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
            String value = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(refreshToken.value().getBytes(StandardCharsets.UTF_8)));
            return new RefreshFingerprint(value);
        } catch (Exception exception) {
            throw new IllegalStateException("refresh credential fingerprint unavailable", exception);
        }
    }
}
