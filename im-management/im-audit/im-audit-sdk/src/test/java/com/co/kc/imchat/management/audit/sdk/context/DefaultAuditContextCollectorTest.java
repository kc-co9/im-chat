package com.co.kc.imchat.management.audit.sdk.context;

import com.co.kc.imchat.management.audit.sdk.model.AuditContext;

import com.co.kc.imchat.plugin.web.contants.WebConstants;
import com.co.kc.imchat.plugin.web.context.HttpRequestContext;
import com.co.kc.imchat.plugin.web.context.HttpRequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAuditContextCollectorTest {

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        HttpRequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void collectsAuthenticatedRequestMetadataAndTrace() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin",
                        "N/A",
                        java.util.List.of()));
        HttpRequestContextHolder.set(new HttpRequestContext("127.0.0.1", "JUnit"));
        MDC.put(WebConstants.TRACE_ID, "trace-1");

        AuditContext context = new DefaultAuditContextCollector().collect();

        assertThat(context.actor().type()).isEqualTo("AUTHENTICATED");
        assertThat(context.actor().id()).isNull();
        assertThat(context.actor().name()).isEqualTo("admin");
        assertThat(context.client().address()).isEqualTo("127.0.0.1");
        assertThat(context.client().userAgent()).isEqualTo("JUnit");
        assertThat(context.traceId()).isEqualTo("trace-1");
    }

    @Test
    void usesSystemActorOutsideAuthenticatedHttpRequest() {
        AuditContext context = new DefaultAuditContextCollector().collect();

        assertThat(context.actor().type()).isEqualTo("SYSTEM");
        assertThat(context.actor().name()).isEqualTo("system");
        assertThat(context.client()).isNull();
        assertThat(context.traceId()).isNull();
    }
}
