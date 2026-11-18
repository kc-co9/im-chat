package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamAdministratorBearerFilterTest {

    @Test
    void clearsAdministratorContextWhenDownstreamFails() {
        IamAdministratorBearerAuthenticator authenticator = new IamAdministratorBearerAuthenticator(
                token -> new IamIntrospectionResult(true, 10L, "root", "imOps", "im-ops-cli",
                        Set.of("imOps"), Set.of(), Instant.parse("2099-01-01T00:00:00Z"),
                        "ADMINISTRATOR", Set.of("ops:broker:read")),
                "imOps", Set.of("im-ops-cli"), Set.of("ops:broker:read"));
        IamAdministratorBearerFilter filter = new IamAdministratorBearerFilter(authenticator,
                new IamSessionCookie("IM_OPS_IAM_SESSION", false, "Lax", Duration.ofHours(1)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/probe");
        request.addHeader("Authorization", "Bearer opaque-token");
        ServletException downstreamFailure = new ServletException("downstream failed");

        try {
            assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                    (currentRequest, response) -> {
                        assertThat(IamSecurityContext.currentPrincipal()).hasValueSatisfying(
                                principal -> assertThat(principal.administratorId()).isEqualTo(10L));
                        throw downstreamFailure;
                    })).isSameAs(downstreamFailure);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
