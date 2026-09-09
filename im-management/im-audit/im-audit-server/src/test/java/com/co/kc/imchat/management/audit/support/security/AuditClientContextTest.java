package com.co.kc.imchat.management.audit.support.security;

import com.co.kc.imchat.management.iam.sdk.security.IamApplicationPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditClientContextTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTypedProducerFromSecurityThreadContext() {
        IamApplicationPrincipal principal = new IamApplicationPrincipal(
                "imAdmin",
                "im-admin-audit",
                Map.of(
                        "appKey", "imAdmin",
                        "client_id", "im-admin-audit"),
                List.of());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        "N/A",
                        List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(AuditClientContext.get()).isEqualTo(principal);
    }

    @Test
    void rejectsMissingProducerIdentity() {
        assertThatThrownBy(AuditClientContext::get)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
