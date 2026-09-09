package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.exception.TransitionException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientTest {

    @Test
    void keepsAnImmutableExactScopeSetAndSupportsSecretRotation() {
        Set<OAuthScope> scopes = new LinkedHashSet<>(Set.of(
                new OAuthScope("audit:ingest")));
        OAuthClient client = machineClient(scopes);
        scopes.add(new OAuthScope("iam:administrator:write"));

        client.rotateSecret(new OAuthClientSecret("new-secret-digest"));

        assertThat(client.getScopes()).containsExactly(new OAuthScope("audit:ingest"));
        assertThat(client.getClientSecret())
                .isEqualTo(new OAuthClientSecret("new-secret-digest"));
    }

    @Test
    void disabledClientCannotBeDisabledAgain() {
        OAuthClient client = machineClient(Set.of(new OAuthScope("audit:ingest")));

        assertThat(client.isActive()).isTrue();
        assertThat(client.belongsTo(new AppId(1L))).isTrue();
        assertThat(client.belongsTo(new AppId(2L))).isFalse();

        client.disable();

        assertThat(client.isActive()).isFalse();
        assertThat(client.getStatus()).isEqualTo(OAuthClientStatus.DISABLED);
        assertThatThrownBy(client::disable).isInstanceOf(TransitionException.class);
    }

    @Test
    void browserClientOwnsRedirectsAndAuthorizationCodeGrants() {
        OAuthClient client = OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-web"))
                .appId(new AppId(1L))
                .audienceAppId(new AppId(1L))
                .name(new OAuthClientName("Admin Web"))
                .clientSecret(new OAuthClientSecret("secret-digest"))
                .grantTypes(Set.of(OAuthGrantType.AUTHORIZATION_CODE,
                        OAuthGrantType.REFRESH_TOKEN))
                .scopes(Set.of(new OAuthScope("openid")))
                .redirectUris(Set.of(new RedirectUri(java.net.URI.create(
                        "https://admin.example.com/login/callback"))))
                .postLogoutRedirectUris(Set.of(new RedirectUri(java.net.URI.create(
                        "https://admin.example.com/"))))
                .status(OAuthClientStatus.ACTIVE)
                .build();

        assertThat(client.allowsRedirect(new RedirectUri(java.net.URI.create(
                "https://admin.example.com/login/callback")))).isTrue();
        assertThat(client.getGrantTypes()).containsExactlyInAnyOrder(
                OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN);
    }

    @Test
    void rejectsMixedBrowserAndMachineGrantTypes() {
        assertThatThrownBy(() -> clientBuilder()
                .grantTypes(Set.of(
                        OAuthGrantType.AUTHORIZATION_CODE,
                        OAuthGrantType.CLIENT_CREDENTIALS))
                .redirectUris(Set.of(new RedirectUri(java.net.URI.create(
                        "https://admin.example.com/login/callback"))))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRefreshTokenWithoutAuthorizationCode() {
        assertThatThrownBy(() -> clientBuilder()
                .grantTypes(Set.of(OAuthGrantType.REFRESH_TOKEN))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsPreviousAccessConfigurationWhenRevisionIsInvalid() {
        OAuthClient client = machineClient(Set.of(new OAuthScope("audit:ingest")));

        assertThatThrownBy(() -> client.reviseAccess(
                Set.of(new OAuthScope("audit:read")),
                Set.of(new RedirectUri(java.net.URI.create("https://audit.example.com/callback"))),
                Set.of()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(client.getScopes()).containsExactly(new OAuthScope("audit:ingest"));
        assertThat(client.getRedirectUris()).isEmpty();
    }

    private OAuthClient.Builder clientBuilder() {
        return OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-web"))
                .appId(new AppId(1L))
                .audienceAppId(new AppId(1L))
                .name(new OAuthClientName("Admin Web"))
                .clientSecret(new OAuthClientSecret("secret-digest"))
                .scopes(Set.of(new OAuthScope("openid")))
                .redirectUris(Set.of())
                .postLogoutRedirectUris(Set.of())
                .status(OAuthClientStatus.ACTIVE);
    }

    private OAuthClient machineClient(Set<OAuthScope> scopes) {
        return OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-audit"))
                .appId(new AppId(1L))
                .audienceAppId(new AppId(2L))
                .name(new OAuthClientName("Admin audit producer"))
                .clientSecret(new OAuthClientSecret("secret-digest"))
                .grantTypes(Set.of(OAuthGrantType.CLIENT_CREDENTIALS))
                .scopes(scopes)
                .redirectUris(Set.of())
                .postLogoutRedirectUris(Set.of())
                .status(OAuthClientStatus.ACTIVE)
                .build();
    }
}
