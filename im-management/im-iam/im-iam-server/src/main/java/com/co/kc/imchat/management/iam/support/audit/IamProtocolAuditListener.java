package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

/** 显式发布 OAuth2/OIDC 成功与失败事件，不读取或保存 Token 和客户端凭据。 */
@RequiredArgsConstructor
public class IamProtocolAuditListener {
    private final IamAuditPublisher auditPublisher;

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        ProtocolEvent protocol = protocol(event.getAuthentication());
        if (protocol != null) {
            publish(event.getAuthentication(), protocol, AuditOutcome.SUCCESS, null);
        }
    }

    @EventListener
    public void authenticationFailed(AbstractAuthenticationFailureEvent event) {
        ProtocolEvent protocol = protocol(event.getAuthentication());
        if (protocol != null) {
            publish(
                    event.getAuthentication(),
                    protocol,
                    AuditOutcome.FAILURE,
                    "PROTOCOL_AUTHENTICATION_FAILED");
        }
    }

    private ProtocolEvent protocol(Authentication authentication) {
        if (authentication instanceof OAuth2AuthorizationCodeAuthenticationToken
                || authentication instanceof OAuth2ClientCredentialsAuthenticationToken) {
            return new ProtocolEvent("TOKEN_ISSUANCE", "TOKEN_ENDPOINT", "/oauth2/token");
        }
        if (authentication instanceof OAuth2RefreshTokenAuthenticationToken) {
            return new ProtocolEvent("TOKEN_REFRESH", "TOKEN_ENDPOINT", "/oauth2/token");
        }
        if (authentication instanceof OAuth2TokenRevocationAuthenticationToken) {
            return new ProtocolEvent("TOKEN_REVOCATION", "TOKEN_ENDPOINT", "/oauth2/revoke");
        }
        if (authentication instanceof OidcLogoutAuthenticationToken) {
            return new ProtocolEvent("PLATFORM_LOGOUT", "OIDC_SESSION", "/connect/logout");
        }
        return null;
    }

    private void publish(
            Authentication authentication,
            ProtocolEvent protocol,
            AuditOutcome outcome,
            String errorCode
    ) {
        auditPublisher.publish(
                principalName(authentication),
                protocol.action(),
                protocol.targetType(),
                protocol.targetId(),
                outcome,
                errorCode,
                protocol.action() + (outcome == AuditOutcome.SUCCESS ? " 成功" : " 失败"));
    }

    private String principalName(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Authentication principal) {
            return principal.getName();
        }
        return authentication.getName();
    }

    private record ProtocolEvent(String action, String targetType, String targetId) {
    }
}
