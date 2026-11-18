package com.co.kc.imchat.management.iam.transformer.infrastructure;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRegisteredClientTransformerTest {

    @Test
    void publicRegistrationUsesOnlyNoneWithPkceAndFiveMinuteOpaqueToken() {
        RegisteredClient client = OAuthRegisteredClientTransformer.INSTANCE.registeredClientFrom(
                new OAuthClientRegistrationDTO("im-ops-cli", OAuthClientType.PUBLIC, null, "Ops CLI",
                        Set.of(OAuthGrantType.AUTHORIZATION_CODE), Set.of("ops:broker:read"),
                        Set.of("http://127.0.0.1:8765/oauth/callback"), Set.of()));
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
    }

    @Test
    void confidentialRegistrationRetainsBasicAuthenticationAndLifetime() {
        RegisteredClient client = OAuthRegisteredClientTransformer.INSTANCE.registeredClientFrom(
                new OAuthClientRegistrationDTO("audit-client", OAuthClientType.CONFIDENTIAL, "digest", "Audit",
                        Set.of(OAuthGrantType.CLIENT_CREDENTIALS), Set.of("audit:ingest"), Set.of(), Set.of()));
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getClientSecret()).isEqualTo("digest");
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getTokenSettings().getAccessTokenFormat()).isEqualTo(OAuth2TokenFormat.REFERENCE);
    }

    @Test
    void mapsEveryOAuthGrantTypeToSpring() {
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.AUTHORIZATION_CODE))
                .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.REFRESH_TOKEN))
                .isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(OAuthRegisteredClientTransformer.INSTANCE
                .authorizationGrantTypeFrom(OAuthGrantType.CLIENT_CREDENTIALS))
                .isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
    }
}
