package com.co.kc.imchat.service.account.infrastructure.domain.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.plugin.session.token.model.TokenType;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionEstablishment;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtSessionTokenCodecTest {
    @Test
    void issueCreatesTypedTokenPairWithDeterministicExpiry() {
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtTokenCodec codec = new JwtTokenCodec(
                new SecretKeySpec("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                        .getBytes(StandardCharsets.UTF_8), "HmacSHA512"),
                "im-account", Duration.ofHours(2), Duration.ofDays(30), clock);
        JwtSessionTokenCodec tokenCodec = new JwtSessionTokenCodec(
                codec, "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        SessionService service = new SessionService(tokenCodec, mock(SessionRepository.class));

        SessionVersion sessionVersion = service.newVersion();
        CredentialPair pair = service.issue(new UserId(42L), sessionVersion);

        assertThat(codec.decode(pair.access().token().value(), TokenType.ACCESS).expiresAt())
                .isEqualTo(now.plus(Duration.ofHours(2)));
        assertThat(codec.decode(pair.refresh().token().value(), TokenType.REFRESH).expiresAt())
                .isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(pair.access().expiresAt()).isEqualTo(now.plus(Duration.ofHours(2)));
        assertThat(pair.refresh().expiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(service.authenticate(pair.access().token())).get()
                .extracting(token -> token.sessionVersion()).isEqualTo(sessionVersion);
        assertThat(service.authenticate(pair.refresh().token())).get()
                .extracting(token -> token.fingerprint()).isEqualTo(pair.refresh().fingerprint());
    }

    @Test
    void signInPersistsSessionAndReturnsPreviousVersion() {
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtTokenCodec codec = new JwtTokenCodec(
                new SecretKeySpec("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                        .getBytes(StandardCharsets.UTF_8), "HmacSHA512"),
                "im-account", Duration.ofHours(2), Duration.ofDays(30), clock);
        SessionRepository repository = mock(SessionRepository.class);
        when(repository.find(new UserId(42L))).thenReturn(Optional.empty());
        SessionService service = new SessionService(
                new JwtSessionTokenCodec(codec,
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)),
                repository);

        SessionEstablishment result = service.establish(new UserId(42L), now);

        assertThat(result.credentials().access().token().value()).isNotBlank();
        assertThat(result.replacedVersion()).isNull();
        verify(repository).save(org.mockito.ArgumentMatchers.any(Session.class));
    }
}
