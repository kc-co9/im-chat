package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamAuthorizedSessionServiceTest {

    @Test
    void consumesPkceStateOnceAndStoresTokensOnlyInServerSession() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamAuthorizationRequestRepository requests =
                mock(IamAuthorizationRequestRepository.class);
        IamApplicationSessionRepository sessions =
                mock(IamApplicationSessionRepository.class);
        IamAuthorizationRequest request = new IamAuthorizationRequest("verifier", "/#/audits");
        when(requests.consume("state")).thenReturn(Optional.of(request), Optional.empty());
        when(client.exchange("code", "verifier")).thenReturn(tokens(900));
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, requests, sessions);

        IamAuthorizationCompletion completion =
                service.completeAuthorization("state", "code");
        IamApplicationSession session = completion.session();

        assertThat(session.tokens().accessToken()).isEqualTo("access-token-900");
        assertThat(completion.continuePath()).isEqualTo("/#/audits");
        verify(sessions).save(session);
        assertThatThrownBy(() -> service.completeAuthorization("state", "code"))
                .isInstanceOf(IamOAuthException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void storesOnlySafeSameOriginContinuationPaths() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamAuthorizationRequestRepository requests =
                mock(IamAuthorizationRequestRepository.class);
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, requests, mock(IamApplicationSessionRepository.class));

        service.beginAuthorization("https://evil.example/path");

        ArgumentCaptor<IamAuthorizationRequest> request =
                ArgumentCaptor.forClass(IamAuthorizationRequest.class);
        verify(requests).save(anyString(), request.capture());
        assertThat(request.getValue().continuePath()).isEqualTo("/");
    }

    @Test
    void rotatesRefreshTokenBeforeAccessTokenExpires() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamAuthorizationRequestRepository requests =
                mock(IamAuthorizationRequestRepository.class);
        IamApplicationSessionRepository sessions =
                mock(IamApplicationSessionRepository.class);
        IamApplicationSession old = new IamApplicationSession(
                "session-id",
                tokens(10),
                "csrf-token",
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(60));
        when(sessions.find("session-id")).thenReturn(Optional.of(old));
        when(client.refresh("refresh-token-10")).thenReturn(tokens(900));
        when(sessions.replace(org.mockito.ArgumentMatchers.eq(old),
                org.mockito.ArgumentMatchers.any(IamApplicationSession.class))).thenReturn(true);
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, requests, sessions);

        IamApplicationSession refreshed = service.current("session-id").orElseThrow();

        assertThat(refreshed.tokens().refreshToken()).isEqualTo("refresh-token-900");
        ArgumentCaptor<IamApplicationSession> saved =
                ArgumentCaptor.forClass(IamApplicationSession.class);
        verify(sessions).replace(org.mockito.ArgumentMatchers.eq(old), saved.capture());
        assertThat(saved.getValue().tokens().refreshToken())
                .isEqualTo("refresh-token-900");
    }

    @Test
    void usesTheWinningSessionWhenAnotherRequestAlreadyRotatedTheToken() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamApplicationSession old = new IamApplicationSession(
                "session-id", tokens(10), "csrf-token",
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(60));
        IamApplicationSession winner = old.rotate(tokens(900), Instant.now());
        when(sessions.find("session-id")).thenReturn(Optional.of(old), Optional.of(winner));
        when(client.refresh("refresh-token-10")).thenReturn(tokens(600));
        when(sessions.replace(org.mockito.ArgumentMatchers.eq(old),
                org.mockito.ArgumentMatchers.any(IamApplicationSession.class))).thenReturn(false);
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, mock(IamAuthorizationRequestRepository.class), sessions);

        IamApplicationSession current = service.current("session-id").orElseThrow();

        assertThat(current.tokens().refreshToken()).isEqualTo("refresh-token-900");
    }

    @Test
    void removesTheApplicationSessionWhenIamExplicitlyRejectsRefresh() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamApplicationSession current = new IamApplicationSession(
                "session-id", tokens(10), "csrf-token",
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(60));
        when(sessions.find("session-id")).thenReturn(Optional.of(current));
        when(client.refresh("refresh-token-10"))
                .thenThrow(new IamInvalidRefreshTokenException(null));
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, mock(IamAuthorizationRequestRepository.class), sessions);

        assertThatThrownBy(() -> service.current("session-id"))
                .isInstanceOf(IamInvalidRefreshTokenException.class);
        verify(sessions).remove("session-id");
    }

    @Test
    void currentApplicationLogoutRevokesBothTokensAndDeletesLocalSession() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamApplicationSession current = new IamApplicationSession(
                "session-id", tokens(900), "csrf-token",
                Instant.now(), Instant.now());
        when(sessions.find("session-id")).thenReturn(Optional.of(current));
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, mock(IamAuthorizationRequestRepository.class), sessions);

        service.logout("session-id");

        verify(client).revoke(current.tokens().accessToken());
        verify(client).revoke(current.tokens().refreshToken());
        verify(sessions).remove("session-id");
    }

    @Test
    void platformLogoutRevokesCurrentApplicationBeforeReturningIamLogoutUri() {
        IamAuthorizationClient client = mock(IamAuthorizationClient.class);
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamApplicationSession current = new IamApplicationSession(
                "session-id", tokens(900), "csrf-token",
                Instant.now(), Instant.now());
        when(sessions.find("session-id")).thenReturn(Optional.of(current));
        when(client.platformLogoutUri()).thenReturn(URI.create(
                "https://iam.example.com/connect/logout"));
        IamAuthorizedSessionService service = new IamAuthorizedSessionService(
                client, mock(IamAuthorizationRequestRepository.class), sessions);

        URI logoutUri = service.platformLogoutUri("session-id");

        assertThat(logoutUri).isEqualTo(
                URI.create("https://iam.example.com/connect/logout"));
        verify(client).revoke(current.tokens().accessToken());
        verify(client).revoke(current.tokens().refreshToken());
        verify(sessions).remove("session-id");
    }

    private static IamTokenSet tokens(long accessSeconds) {
        Instant now = Instant.now();
        return new IamTokenSet(
                "access-token-" + accessSeconds,
                now.plusSeconds(accessSeconds),
                "refresh-token-" + accessSeconds,
                now.plusSeconds(28_800));
    }
}
