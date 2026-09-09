package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.infrastructure.security.token.OAuthTokenClaimsCustomizer;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAdministratorTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthApplicationTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAdministratorTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthApplicationTokenClaimsQuery;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsSet;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthTokenClaimsCustomizerTest {

    @Test
    void writesOwnerApplicationAndTargetAudienceForMachineToken() {
        OAuthClientAppService clientAppService = mock(OAuthClientAppService.class);
        when(clientAppService.getApplicationTokenClaims(
                new OAuthApplicationTokenClaimsQuery("im-admin-audit")))
                .thenReturn(new OAuthApplicationTokenClaimsDTO(
                        "im-admin-audit", "imAdmin", "imAudit"));
        OAuthTokenClaimsCustomizer customizer =
                new OAuthTokenClaimsCustomizer(clientAppService);
        OAuth2TokenClaimsSet.Builder claims = OAuth2TokenClaimsSet.builder();
        OAuth2TokenClaimsContext context = mock(OAuth2TokenClaimsContext.class);
        RegisteredClient registeredClient = RegisteredClient.withId("im-admin-audit")
                .clientId("im-admin-audit")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        when(context.getRegisteredClient()).thenReturn(registeredClient);
        when(context.getAuthorizationGrantType())
                .thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        Authentication principal = mock(Authentication.class);
        when(principal.getName()).thenReturn("im-admin-audit");
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claims);

        customizer.customizeAccessToken(context);

        assertThat(claims.build().getClaims())
                .containsEntry("appKey", "imAdmin")
                .containsEntry("aud", List.of("imAudit"));
    }

    @Test
    void writesAdministratorIdentityAndPermissionSnapshot() {
        OAuthClientAppService clientAppService = mock(OAuthClientAppService.class);
        when(clientAppService.getAdministratorTokenClaims(
                new OAuthAdministratorTokenClaimsQuery("im-admin-web", 10L)))
                .thenReturn(new OAuthAdministratorTokenClaimsDTO(
                        "10",
                        "root",
                        "imAdmin",
                        "imAdmin",
                        List.of("user:read", "user:write")));
        OAuthTokenClaimsCustomizer customizer =
                new OAuthTokenClaimsCustomizer(clientAppService);
        OAuth2TokenClaimsSet.Builder claims = OAuth2TokenClaimsSet.builder();
        OAuth2TokenClaimsContext context = mock(OAuth2TokenClaimsContext.class);
        Authentication principal = mock(Authentication.class);
        RegisteredClient registeredClient = RegisteredClient.withId("im-admin-web")
                .clientId("im-admin-web")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://admin.example.com/login/oauth2/code/iam")
                .build();
        when(context.getRegisteredClient()).thenReturn(registeredClient);
        when(context.getAuthorizationGrantType())
                .thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("10");
        when(context.getClaims()).thenReturn(claims);

        customizer.customizeAccessToken(context);

        assertThat(claims.build().getClaims())
                .containsEntry("sub", "10")
                .containsEntry("username", "root")
                .containsEntry("appKey", "imAdmin")
                .containsEntry("aud", List.of("imAdmin"))
                .containsEntry("authorities", List.of("user:read", "user:write"));
    }
}
