package com.co.kc.imchat.service.account.infrastructure.domain.repository;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.model.cqrs.dto.SessionDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionRepositoryTest {

    @Test
    void savesAndRestoresSessionAggregate() {
        Cache<Long, SessionDTO> cache = mock(Cache.class);
        RedisSessionRepository repository = new RedisSessionRepository(cache);
        Instant now = Instant.parse("2026-08-14T10:00:00Z");
        Session session = new Session(new UserId(42L));
        session.signIn(
                new SessionVersion("version-1"), new RefreshFingerprint("fingerprint-1"),
                now.plusSeconds(30L * 24 * 3600), now);

        repository.save(session);

        verify(cache).put(org.mockito.ArgumentMatchers.eq(42L), any(SessionDTO.class));
        SessionDTO stored = new SessionDTO();
        stored.setUserId(42L);
        stored.setStatus(com.co.kc.imchat.service.account.model.enums.SessionStatusEnum.ONLINE);
        stored.setSessionVersion("version-1");
        stored.setRefreshFingerprint("fingerprint-1");
        stored.setRefreshTokenExpiresAt(now.plusSeconds(30L * 24 * 3600));
        when(cache.get(42L)).thenReturn(stored);

        Session restored = repository.find(new UserId(42L)).orElseThrow();

        assertThat(restored.matchesVersion(new SessionVersion("version-1"))).isTrue();
        assertThat(restored.getRefreshFingerprint())
                .isEqualTo(new RefreshFingerprint("fingerprint-1"));
    }
}
