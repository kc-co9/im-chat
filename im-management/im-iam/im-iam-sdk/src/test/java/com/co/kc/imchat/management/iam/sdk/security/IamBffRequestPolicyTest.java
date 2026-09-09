package com.co.kc.imchat.management.iam.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IamBffRequestPolicyTest {

    @Test
    void classifiesOnlyApprovedBrowserRequestsAsPublic() {
        assertThat(isPublic("/")).isTrue();
        assertThat(isPublic("/index.html")).isTrue();
        assertThat(isPublic("/favicon.ico")).isTrue();
        assertThat(isPublic("/assets/app.js")).isTrue();
        assertThat(isPublic("/error")).isTrue();
        assertThat(isPublic("/iam/login")).isTrue();
        assertThat(isPublic("/iam/callback")).isTrue();
        assertThat(isPublic("/iam/me")).isFalse();
        assertThat(isPublic("/iam/logout")).isFalse();
        assertThat(isPublic("/api/audits")).isFalse();
        assertThat(isPublic("/actuator/health")).isFalse();
    }

    @Test
    void exposesPublicAndAuthenticatedPatternsForSecurityChains() {
        assertThat(IamBffRequestPolicy.publicPathPatterns()).containsExactly(
                "/",
                "/index.html",
                "/favicon.ico",
                "/error",
                "/assets/**",
                "/iam/login",
                "/iam/callback");
        assertThat(IamBffRequestPolicy.authenticatedPathPatterns()).containsExactly(
                "/iam/**",
                "/api/**",
                "/v3/api-docs/**");
    }

    private boolean isPublic(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return IamBffRequestPolicy.isPublic(request);
    }
}
