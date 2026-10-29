package com.co.kc.imchat.management.iam.infrastructure.security.oauth;

import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.repository.OAuthRegisteredClientRepository;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientRegistrationQuery;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthRegisteredClientRepositoryTest {

    @Test
    void mapsBrowserClientToPkceClientWithOpaqueRotatingTokens() {
        OAuthClient browserClient = browserClient();
        OAuthRegisteredClientRepository repository = repository(browserClient);

        RegisteredClient client = repository.findByClientId("im-admin-client");

        assertThat(client.getId()).isEqualTo("im-admin-client");
        assertThat(client.getClientSecret()).isEqualTo("bcrypt-client-secret");
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getRedirectUris())
                .containsExactly("https://admin.example.com/login/callback");
        assertThat(client.getTokenSettings().getAccessTokenFormat())
                .isEqualTo(OAuth2TokenFormat.REFERENCE);
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive())
                .isEqualTo(Duration.ofHours(8));
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void mapsMachineClientToClientCredentialsOnly() {
        OAuthClient machineClient = machineClient();
        OAuthRegisteredClientRepository repository = repository(machineClient);

        RegisteredClient client = repository.findByClientId("im-admin-audit");

        assertThat(client.getId()).isEqualTo("im-admin-audit");
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getScopes()).containsExactly("audit:ingest");
        assertThat(client.getRedirectUris()).isEmpty();
        assertThat(client.getPostLogoutRedirectUris()).isEmpty();
        assertThat(client.getClientSettings().isRequireProofKey()).isFalse();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(15));
    }

    private OAuthRegisteredClientRepository repository(OAuthClient client) {
        OAuthClientAppService appService = mock(OAuthClientAppService.class);
        when(appService.queryRegistration(
                new OAuthClientRegistrationQuery(client.getClientId().value())))
                .thenReturn(Optional.of(new OAuthClientRegistrationDTO(
                        client.getClientId().value(),
                        client.getClientSecret().value(),
                        client.getName().value(),
                        client.getGrantTypes(),
                        client.getScopes().stream().map(OAuthScope::value).collect(
                                Collectors.toUnmodifiableSet()),
                        client.getRedirectUris().stream().map(RedirectUri::stringValue).collect(
                                Collectors.toUnmodifiableSet()),
                        client.getPostLogoutRedirectUris().stream()
                                .map(RedirectUri::stringValue)
                                .collect(Collectors.toUnmodifiableSet()))));
        return new OAuthRegisteredClientRepository(appService);
    }

    private OAuthClient browserClient() {
        RedirectUri redirectUri = new RedirectUri(
                URI.create("https://admin.example.com/login/callback"));
        return OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-client"))
                .appId(new AppId(1L))
                .audienceAppId(new AppId(1L))
                .name(new OAuthClientName("Admin browser"))
                .clientSecret(new OAuthClientSecret("bcrypt-client-secret"))
                .grantTypes(Set.of(
                        OAuthGrantType.AUTHORIZATION_CODE,
                        OAuthGrantType.REFRESH_TOKEN))
                .scopes(Set.of(new OAuthScope("openid")))
                .redirectUris(Set.of(redirectUri))
                .postLogoutRedirectUris(Set.of(new RedirectUri(
                        URI.create("https://admin.example.com/"))))
                .status(OAuthClientStatus.ACTIVE)
                .build();
    }

    private OAuthClient machineClient() {
        return OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-audit"))
                .appId(new AppId(1L))
                .audienceAppId(new AppId(2L))
                .name(new OAuthClientName("Admin audit producer"))
                .clientSecret(new OAuthClientSecret("bcrypt-machine-secret"))
                .grantTypes(Set.of(OAuthGrantType.CLIENT_CREDENTIALS))
                .scopes(Set.of(new OAuthScope("audit:ingest")))
                .redirectUris(Set.of())
                .postLogoutRedirectUris(Set.of())
                .status(OAuthClientStatus.ACTIVE)
                .build();
    }
}
