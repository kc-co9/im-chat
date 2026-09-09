package com.co.kc.imchat.management.iam.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamOpaqueTokenAuthenticationConverterTest {

    @Test
    void convertsApplicationTokenForExpectedAudience() {
        IamOpaqueTokenAuthenticationConverter converter =
                new IamOpaqueTokenAuthenticationConverter("imAudit");
        DefaultOAuth2AuthenticatedPrincipal principal = principal("imAudit");

        BearerTokenAuthentication authentication =
                converter.convert("token", principal);

        assertThat(authentication.getPrincipal())
                .isEqualTo(new IamApplicationPrincipal(
                        "imAdmin",
                        "im-admin-audit",
                        principal.getAttributes(),
                        List.of()));
    }

    @Test
    void rejectsAnotherApplicationAudience() {
        IamOpaqueTokenAuthenticationConverter converter =
                new IamOpaqueTokenAuthenticationConverter("imAudit");

        assertThatThrownBy(() -> converter.convert("token", principal("imMonitor")))
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    private DefaultOAuth2AuthenticatedPrincipal principal(String audience) {
        return new DefaultOAuth2AuthenticatedPrincipal(
                Map.of(
                        "appKey", "imAdmin",
                        "client_id", "im-admin-audit",
                        "aud", List.of(audience)),
                List.of());
    }
}
