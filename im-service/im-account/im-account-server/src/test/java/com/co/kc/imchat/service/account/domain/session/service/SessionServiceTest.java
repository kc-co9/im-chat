package com.co.kc.imchat.service.account.domain.session.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {
    private static final UserId USER_ID = new UserId(1001L);
    private static final SessionVersion VERSION = new SessionVersion("session-v1");
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Test
    void signOutRequiresTheCurrentOnlineSessionVersion() {
        SessionRepository repository = mock(SessionRepository.class);
        Session session = onlineSession();
        when(repository.find(USER_ID)).thenReturn(Optional.of(session));
        SessionService service = new SessionService(mock(SessionTokenCodec.class), repository);

        SessionVersion signedOutVersion = service.signOut(USER_ID, VERSION, NOW);

        assertThat(signedOutVersion).isEqualTo(VERSION);
        assertThat(session.isSignIn()).isFalse();
        assertThat(session.getSignOutTime()).isEqualTo(NOW);
        verify(repository).save(session);
    }

    @Test
    void signOutRejectsAMismatchedSessionVersion() {
        SessionRepository repository = mock(SessionRepository.class);
        Session session = onlineSession();
        when(repository.find(USER_ID)).thenReturn(Optional.of(session));
        SessionService service = new SessionService(mock(SessionTokenCodec.class), repository);

        assertThatThrownBy(() -> service.signOut(
                USER_ID, new SessionVersion("session-v2"), NOW))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("会话无效");

        assertThat(session.isSignIn()).isTrue();
        verify(repository, never()).save(session);
    }

    @Test
    void kickOutReturnsTheRevokedVersionForAnOnlineSession() {
        SessionRepository repository = mock(SessionRepository.class);
        Session session = onlineSession();
        when(repository.find(USER_ID)).thenReturn(Optional.of(session));
        SessionService service = new SessionService(mock(SessionTokenCodec.class), repository);

        Optional<SessionVersion> kickedVersion = service.kickOut(USER_ID, NOW);

        assertThat(kickedVersion).contains(VERSION);
        assertThat(session.isSignIn()).isFalse();
        verify(repository).save(session);
    }

    @Test
    void kickOutDoesNothingWithoutAnOnlineSession() {
        SessionRepository repository = mock(SessionRepository.class);
        when(repository.find(USER_ID)).thenReturn(Optional.empty());
        SessionService service = new SessionService(mock(SessionTokenCodec.class), repository);

        Optional<SessionVersion> kickedVersion = service.kickOut(USER_ID, NOW);

        assertThat(kickedVersion).isEmpty();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Session onlineSession() {
        Session session = new Session(USER_ID);
        session.signIn(
                VERSION,
                new RefreshFingerprint("fingerprint"),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60));
        return session;
    }
}
