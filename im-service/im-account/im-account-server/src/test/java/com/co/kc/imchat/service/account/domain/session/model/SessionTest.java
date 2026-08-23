package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {
    private static final Instant SIGN_IN_TIME = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-09-13T10:00:00Z");

    private static RefreshFingerprint fingerprint(String value) {
        return new RefreshFingerprint(value);
    }

    @Test
    void signInEstablishesNewVersionAndReturnsReplacedVersion() {
        Session session = new Session(new UserId(42L));

        SessionVersion firstReplacedVersion = session.signIn(
                version("version-1"), fingerprint("fingerprint-1"), REFRESH_EXPIRES_AT, SIGN_IN_TIME);
        SessionVersion secondReplacedVersion = session.signIn(
                version("version-2"), fingerprint("fingerprint-2"),
                REFRESH_EXPIRES_AT.plusSeconds(86400), SIGN_IN_TIME.plusSeconds(3600));

        assertThat(firstReplacedVersion).isNull();
        assertThat(secondReplacedVersion).isEqualTo(version("version-1"));
        assertThat(session.matchesVersion(version("version-2"))).isTrue();
        assertThat(session.getRefreshFingerprint()).isEqualTo(fingerprint("fingerprint-2"));
        assertThat(session.getRefreshTokenExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT.plusSeconds(86400));
        assertThat(session.getSignInTime()).isEqualTo(SIGN_IN_TIME.plusSeconds(3600));
    }

    @Test
    void signOutClearsRefreshStateAndAuthentication() {
        Session session = new Session(new UserId(42L));
        session.signIn(version("version-1"), fingerprint("fingerprint-1"), REFRESH_EXPIRES_AT, SIGN_IN_TIME);
        Instant signOutTime = SIGN_IN_TIME.plusSeconds(7200);

        session.signOut(signOutTime);

        assertThat(session.matchesVersion(version("version-1"))).isFalse();
        assertThat(session.getRefreshFingerprint()).isNull();
        assertThat(session.getRefreshTokenExpiresAt()).isNull();
        assertThat(session.getSignOutTime()).isEqualTo(signOutTime);
    }

    @Test
    void onlineSessionRequiresMatchingVersion() {
        Session session = new Session(new UserId(42L));
        session.signIn(version("version-1"), fingerprint("fingerprint-1"), REFRESH_EXPIRES_AT, SIGN_IN_TIME);

        assertThat(session.matchesVersion(version("version-1"))).isTrue();
        assertThat(session.matchesVersion(version("version-2"))).isFalse();
        assertThat(session.matchesVersion(null)).isFalse();
    }

    @Test
    void expiredRefreshStateCannotRotate() {
        Session session = new Session(new UserId(42L));
        session.signIn(version("version-1"), fingerprint("fingerprint-1"), REFRESH_EXPIRES_AT, SIGN_IN_TIME);

        assertThatThrownBy(() -> session.rotateCredential(
                version("version-1"), fingerprint("fingerprint-1"), fingerprint("fingerprint-2"),
                REFRESH_EXPIRES_AT.plusSeconds(86400), REFRESH_EXPIRES_AT))
                .isInstanceOf(AuthException.class);

        assertThat(session.getRefreshFingerprint()).isEqualTo(fingerprint("fingerprint-1"));
    }

    @Test
    void rotateCredentialReplacesRefreshState() {
        Session session = new Session(new UserId(42L));
        session.signIn(version("version-1"), fingerprint("fingerprint-1"), REFRESH_EXPIRES_AT, SIGN_IN_TIME);

        session.rotateCredential(
                version("version-1"), fingerprint("fingerprint-1"), fingerprint("fingerprint-2"),
                REFRESH_EXPIRES_AT.plusSeconds(86400), SIGN_IN_TIME.plusSeconds(3600));

        assertThat(session.getRefreshFingerprint()).isEqualTo(fingerprint("fingerprint-2"));
        assertThat(session.getRefreshTokenExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT.plusSeconds(86400));
    }

    @Test
    void restoredLegacySessionWithoutVersionCannotAuthenticate() {
        Session session = Session.builder()
                .userId(new UserId(42L))
                .status(SessionStatus.ONLINE)
                .signInTime(SIGN_IN_TIME)
                .build();

        assertThat(session.isSignIn()).isTrue();
        assertThat(session.matchesVersion(null)).isFalse();

        session.signIn(version("version-1"), fingerprint("fingerprint-1"),
                REFRESH_EXPIRES_AT, SIGN_IN_TIME.plusSeconds(3600));

        assertThat(session.matchesVersion(version("version-1"))).isTrue();
    }

    private static SessionVersion version(String value) {
        return new SessionVersion(value);
    }
}
