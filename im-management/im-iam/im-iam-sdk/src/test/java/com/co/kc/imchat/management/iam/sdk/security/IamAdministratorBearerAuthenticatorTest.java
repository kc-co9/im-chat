package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamAdministratorBearerAuthenticatorTest {

    @Test
    void preservesTrustedAdministratorClientAndScopesForAuthorization() {
        IamIntrospectionResult identity = identity(Set.of("ops:broker:read"));
        IamAdministratorBearerAuthenticator authenticator = new IamAdministratorBearerAuthenticator(
                token -> identity, "imOps", Set.of("im-ops-cli"), Set.of("ops:broker:read"));

        UsernamePasswordAuthenticationToken authentication = authenticator.authenticate("opaque-token");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(identity.principal());
        assertThat(authentication.getDetails()).isEqualTo(identity);
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactly("ops:broker:read");
    }

    @Test
    void missingCliTrustConfigurationDeniesOtherwiseValidAdministrator() {
        IamAdministratorBearerAuthenticator authenticator = new IamAdministratorBearerAuthenticator(
                token -> identity(Set.of("ops:broker:read")), "imOps", Set.of(), Set.of());

        assertThatThrownBy(() -> authenticator.authenticate("opaque-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void emptyScopesCannotAuthenticateEvenWhenAdministratorHasAuthority() {
        IamAdministratorBearerAuthenticator authenticator = new IamAdministratorBearerAuthenticator(
                token -> identity(Set.of()), "imOps", Set.of("im-ops-cli"), Set.of("ops:broker:read"));

        assertThatThrownBy(() -> authenticator.authenticate("opaque-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    private IamIntrospectionResult identity(Set<String> scopes) {
        return new IamIntrospectionResult(true, 10L, "root", "imOps", "im-ops-cli",
                Set.of("imOps"), Set.of("ops:broker:read"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", scopes);
    }
}
