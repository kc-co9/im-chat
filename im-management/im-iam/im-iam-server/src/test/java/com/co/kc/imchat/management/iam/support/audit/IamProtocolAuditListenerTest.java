package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IamProtocolAuditListenerTest {

    @Test
    void publishesIssuanceRefreshRevocationAndLogoutWithoutTokenValues() {
        IamAuditPublisher publisher = mock(IamAuditPublisher.class);
        IamProtocolAuditListener listener = new IamProtocolAuditListener(publisher);
        TestingAuthenticationToken client = new TestingAuthenticationToken(
                "imAdminClient", "client-secret");

        listener.authenticationSucceeded(new AuthenticationSuccessEvent(
                new OAuth2ClientCredentialsAuthenticationToken(client, Set.of(), Map.of())));
        listener.authenticationSucceeded(new AuthenticationSuccessEvent(
                new OAuth2RefreshTokenAuthenticationToken(
                        "raw-refresh-token", client, Set.of(), Map.of())));
        listener.authenticationSucceeded(new AuthenticationSuccessEvent(
                new OAuth2TokenRevocationAuthenticationToken(
                        "raw-access-token", client, "access_token")));
        listener.authenticationSucceeded(new AuthenticationSuccessEvent(
                new OidcLogoutAuthenticationToken(
                        "raw-id-token", client, null, "imAdminClient", "/", null)));

        verify(publisher).publish(
                "imAdminClient", "TOKEN_ISSUANCE", "TOKEN_ENDPOINT", "/oauth2/token",
                AuditOutcome.SUCCESS, null, "TOKEN_ISSUANCE 成功");
        verify(publisher).publish(
                "imAdminClient", "TOKEN_REFRESH", "TOKEN_ENDPOINT", "/oauth2/token",
                AuditOutcome.SUCCESS, null, "TOKEN_REFRESH 成功");
        verify(publisher).publish(
                "imAdminClient", "TOKEN_REVOCATION", "TOKEN_ENDPOINT", "/oauth2/revoke",
                AuditOutcome.SUCCESS, null, "TOKEN_REVOCATION 成功");
        verify(publisher).publish(
                "imAdminClient", "PLATFORM_LOGOUT", "OIDC_SESSION", "/connect/logout",
                AuditOutcome.SUCCESS, null, "PLATFORM_LOGOUT 成功");
    }

    @Test
    void publishesProtocolAuthenticationFailureWithoutCredentialValues() {
        IamAuditPublisher publisher = mock(IamAuditPublisher.class);
        IamProtocolAuditListener listener = new IamProtocolAuditListener(publisher);
        TestingAuthenticationToken client = new TestingAuthenticationToken(
                "imAdminClient", "client-secret");
        OAuth2ClientCredentialsAuthenticationToken authentication =
                new OAuth2ClientCredentialsAuthenticationToken(client, Set.of(), Map.of());

        listener.authenticationFailed(new AuthenticationFailureBadCredentialsEvent(
                authentication,
                new BadCredentialsException("invalid client secret")));

        verify(publisher).publish(
                "imAdminClient", "TOKEN_ISSUANCE", "TOKEN_ENDPOINT", "/oauth2/token",
                AuditOutcome.FAILURE, "PROTOCOL_AUTHENTICATION_FAILED", "TOKEN_ISSUANCE 失败");
    }
}
