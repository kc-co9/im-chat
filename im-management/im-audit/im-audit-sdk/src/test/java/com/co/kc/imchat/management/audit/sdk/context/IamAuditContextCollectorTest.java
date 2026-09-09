package com.co.kc.imchat.management.audit.sdk.context;

import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.iam.sdk.security.model.IamPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IamAuditContextCollectorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void collectsIamAdministratorIdentity() {
        IamPrincipal principal = new IamPrincipal(
                1001L,
                "admin",
                "imAdmin",
                Set.of("user:write"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        "N/A",
                        java.util.List.of()));

        AuditContext context = new IamAuditContextCollector().collect();

        assertThat(context.actor().type()).isEqualTo("ADMINISTRATOR");
        assertThat(context.actor().id()).isEqualTo("1001");
        assertThat(context.actor().name()).isEqualTo("admin");
    }
}
