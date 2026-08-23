package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import com.co.kc.imchat.service.account.application.lock.ImAccountLockScene;
import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.AccessToken;
import com.co.kc.imchat.service.account.adapter.broker.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.IssuedAccessToken;
import com.co.kc.imchat.service.account.domain.session.model.IssuedRefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.RefreshCredential;
import com.co.kc.imchat.service.account.domain.session.model.RefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.model.SessionEstablishment;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.RefreshTokenCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignOutCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;

class SessionAppServiceTest {

    private static final Instant NOW = Instant.now();

    @Test
    void signInReturnsCredentialPairAndDelegatesSessionPersistence() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserService userService = mock(UserService.class);
        SessionService credentialService = mock(SessionService.class);
        User user = new User(
                new UserId(42L),
                new UserEmail("user@example.com"),
                new UserName("user"),
                new UserPassword("encrypted-password"));
        RefreshFingerprint fingerprint = new RefreshFingerprint("fingerprint-1");
        CredentialPair pair = credentials(
                "access-token", NOW.plusSeconds(7200), "refresh-token",
                NOW.plusSeconds(30L * 24 * 3600), fingerprint);
        when(userService.authenticate(new UserEmail("user@example.com"),
                new com.co.kc.imchat.service.account.domain.user.model.UserRawPassword("password")))
                .thenReturn(user);
        when(credentialService.establish(eq(new UserId(42L)), any(Instant.class)))
                .thenReturn(new SessionEstablishment(pair, null));
        DistributedLockTemplate lockTemplate = executingLockTemplate();
        SessionAppService service = new SessionAppService(
                sessionRepository,
                userService,
                credentialService,
                mock(SessionConnectionAdapter.class),
                lockTemplate);

        SignInDTO result = service.signIn(new UserSignInCmd("user@example.com", "password"));

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.accessTokenExpiresAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
        verify(credentialService).establish(eq(new UserId(42L)), any(Instant.class));
        verify(lockTemplate).execute(
                any(Callable.class), eq(ImAccountLockScene.SESSION_WRITE), eq("42"),
                eq(LockOptions.AUTO_RENEW_DEFAULT_WAIT));
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void refreshDelegatesAuthenticatedCredentialAndReturnsNewPair() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserService userService = mock(UserService.class);
        SessionService credentialService = mock(SessionService.class);
        RefreshFingerprint oldFingerprint = new RefreshFingerprint("fingerprint-old");
        RefreshFingerprint newFingerprint = new RefreshFingerprint("fingerprint-new");
        RefreshCredential refreshCredential = new RefreshCredential(
                new UserId(42L), new SessionVersion("session-v1"), NOW.plusSeconds(3600), oldFingerprint);
        CredentialPair newPair = credentials(
                "access-new", NOW.plusSeconds(7200), "refresh-new",
                NOW.plusSeconds(30L * 24 * 3600), newFingerprint);
        when(credentialService.authenticate(new RefreshToken("refresh-old"))).thenReturn(Optional.of(refreshCredential));
        when(credentialService.refresh(eq(refreshCredential), any(Instant.class))).thenReturn(newPair);
        DistributedLockTemplate lockTemplate = executingLockTemplate();
        SessionAppService service = new SessionAppService(
                sessionRepository, userService, credentialService,
                mock(SessionConnectionAdapter.class), lockTemplate);

        SignInDTO result = service.refreshToken(new RefreshTokenCmd("refresh-old"));

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.accessToken()).isEqualTo("access-new");
        assertThat(result.refreshToken()).isEqualTo("refresh-new");
        verify(credentialService).refresh(eq(refreshCredential), any(Instant.class));
        verify(lockTemplate).execute(
                any(Callable.class), eq(ImAccountLockScene.SESSION_WRITE), eq("42"),
                eq(LockOptions.AUTO_RENEW_DEFAULT_WAIT));
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void signOutRequiresMatchingTrustedSessionVersion() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionService credentialService = mock(SessionService.class);
        SessionConnectionAdapter connectionAdapter = mock(SessionConnectionAdapter.class);
        Session session = new Session(new UserId(42L));
        session.signIn(
                new SessionVersion("session-v1"), new RefreshFingerprint("fingerprint"),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60));
        when(sessionRepository.find(new UserId(42L))).thenReturn(Optional.of(session));
        DistributedLockTemplate lockTemplate = executingLockTemplate();
        SessionAppService service = new SessionAppService(
                sessionRepository, mock(UserService.class), credentialService,
                connectionAdapter, lockTemplate);

        assertThatThrownBy(() -> service.signOut(new UserSignOutCmd(42L, "another-session")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("会话无效");
        assertThat(session.matchesVersion(new SessionVersion("session-v1"))).isTrue();
        verify(sessionRepository, never()).save(session);

        service.signOut(new UserSignOutCmd(42L, "session-v1"));

        assertThat(session.isSignIn()).isFalse();
        org.mockito.InOrder order = inOrder(sessionRepository, connectionAdapter);
        order.verify(sessionRepository).save(session);
        order.verify(connectionAdapter).closeConnections(
                new UserId(42L), new SessionVersion("session-v1"));
        verify(lockTemplate, org.mockito.Mockito.times(2)).execute(
                any(Callable.class), eq(ImAccountLockScene.SESSION_WRITE), eq("42"),
                eq(LockOptions.AUTO_RENEW_DEFAULT_WAIT));

        AccessCredential accessCredential = new AccessCredential(
                new UserId(42L), new SessionVersion("session-v1"), NOW.plusSeconds(3600));
        when(credentialService.authenticate(new AccessToken("access-old"))).thenReturn(Optional.of(accessCredential));
        assertThatThrownBy(() -> service.authenticate(new AccessTokenParams("access-old")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(10001);

        RefreshCredential refreshCredential = new RefreshCredential(
                new UserId(42L), new SessionVersion("session-v1"), NOW.plusSeconds(3600),
                new RefreshFingerprint("fingerprint"));
        when(credentialService.authenticate(new RefreshToken("refresh-old"))).thenReturn(Optional.of(refreshCredential));
        when(credentialService.refresh(eq(refreshCredential), any(Instant.class)))
                .thenThrow(new AuthException("用户认证失败"));
        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenCmd("refresh-old")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("用户认证失败");
    }

    @Test
    void refreshRejectsWrongTokenTypeAndFailedAtomicRotation() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionService credentialService = mock(SessionService.class);
        SessionAppService service = new SessionAppService(
                sessionRepository, mock(UserService.class), credentialService,
                mock(SessionConnectionAdapter.class), executingLockTemplate());
        when(credentialService.authenticate(new RefreshToken("access-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenCmd("access-token")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("用户认证失败");

        RefreshFingerprint oldFingerprint = new RefreshFingerprint("fingerprint-old");
        RefreshCredential credential = new RefreshCredential(
                new UserId(42L), new SessionVersion("session-old"), NOW.plusSeconds(3600), oldFingerprint);
        when(credentialService.authenticate(new RefreshToken("refresh-old"))).thenReturn(Optional.of(credential));
        when(credentialService.refresh(eq(credential), any(Instant.class)))
                .thenThrow(new AuthException("用户认证失败"));
        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenCmd("refresh-old")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("用户认证失败");
    }

    @Test
    void accessAuthenticationRequiresOnlineMatchingSessionVersion() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        SessionService credentialService = mock(SessionService.class);
        SessionAppService service = new SessionAppService(
                sessionRepository, mock(UserService.class), credentialService,
                mock(SessionConnectionAdapter.class), executingLockTemplate());
        AccessCredential credential = new AccessCredential(
                new UserId(42L), new SessionVersion("session-v1"), NOW.plusSeconds(3600));
        Session session = new Session(new UserId(42L));
        session.signIn(
                new SessionVersion("session-v2"), new RefreshFingerprint("fingerprint"),
                NOW.plusSeconds(3600),
                NOW);
        when(credentialService.authenticate(new AccessToken("access-old"))).thenReturn(Optional.of(credential));
        when(sessionRepository.find(new UserId(42L))).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.authenticate(new AccessTokenParams("access-old")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(10001);
    }

    @Test
    void secondSignInSavesNewSessionBeforeSchedulingOldSessionClose() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserService userService = mock(UserService.class);
        SessionService credentialService = mock(SessionService.class);
        SessionConnectionAdapter connectionAdapter = mock(SessionConnectionAdapter.class);
        User user = new User(
                new UserId(42L), new UserEmail("user@example.com"), new UserName("user"),
                new UserPassword("encrypted-password"));
        CredentialPair pair = credentials(
                "access-new", NOW.plusSeconds(7200), "refresh-new", NOW.plusSeconds(3600),
                new RefreshFingerprint("fingerprint-new"));
        when(userService.authenticate(
                new UserEmail("user@example.com"),
                new com.co.kc.imchat.service.account.domain.user.model.UserRawPassword("password")))
                .thenReturn(user);
        when(credentialService.establish(eq(new UserId(42L)), any(Instant.class)))
                .thenReturn(new SessionEstablishment(pair, new SessionVersion("session-old")));
        SessionAppService service = new SessionAppService(
                sessionRepository, userService, credentialService,
                connectionAdapter, executingLockTemplate());

        service.signIn(new UserSignInCmd("user@example.com", "password"));

        verify(credentialService).establish(eq(new UserId(42L)), any(Instant.class));
        verify(connectionAdapter).closeConnections(
                new UserId(42L), new SessionVersion("session-old"));
    }

    private static CredentialPair credentials(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            RefreshFingerprint fingerprint
    ) {
        return new CredentialPair(
                new IssuedAccessToken(new AccessToken(accessToken), accessExpiresAt),
                new IssuedRefreshToken(new RefreshToken(refreshToken), refreshExpiresAt, fingerprint));
    }

    private static DistributedLockTemplate executingLockTemplate() {
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        doAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call())
                .when(lockTemplate)
                .execute(any(Callable.class), any(String.class), any(String.class), any(LockOptions.class));
        return lockTemplate;
    }
}
