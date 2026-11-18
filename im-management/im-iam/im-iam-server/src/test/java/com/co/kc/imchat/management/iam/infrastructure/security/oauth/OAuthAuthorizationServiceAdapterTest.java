package com.co.kc.imchat.management.iam.infrastructure.security.oauth;

import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationCode;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationRequest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OidcIdentityToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthRefreshToken;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter.OAuthAuthorizationServiceAdapter;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.transformer.application.OAuthAuthorizationAppTransformer;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.attributes.OAuthAuthorizationAttributes;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthAuthorizationServiceAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final String RAW_ACCESS_TOKEN = "raw-access-token";

    @Test
    void delegatesPersistenceThroughApplicationService() {
        Class<?>[] dependencies = Arrays.stream(OAuthAuthorizationServiceAdapter.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toArray(Class<?>[]::new);

        assertThat(dependencies).contains(OAuthAuthorizationAppService.class);
        assertThat(dependencies)
                .noneMatch(type -> type.getPackageName().contains(".infrastructure.mybatis"))
                .noneMatch(type -> type.getPackageName().contains(".domain.")
                        && type.getPackageName().endsWith(".repository"));
    }

    @Test
    void mapsApplicationSaveFailureToInvalidGrant() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        doThrow(new AuthException("OAuth 客户端无效"))
                .when(appService).save(any());
        OAuthAuthorizationServiceAdapter service = service(
                appService, clientRepository, tokenDigester);

        assertThatThrownBy(() -> service.save(oauthAuthorization()))
                .isInstanceOfSatisfying(
                        org.springframework.security.oauth2.core.OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode())
                                .isEqualTo(org.springframework.security.oauth2.core.OAuth2ErrorCodes.INVALID_GRANT));
    }

    @Test
    void delegatesFinalAuthorizationStateToSaveUseCase() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        OAuthAuthorizationServiceAdapter service = service(
                appService,
                mock(RegisteredClientRepository.class),
                tokenDigester);

        service.save(oauthAuthorization());

        ArgumentCaptor<OAuthAuthorizationSaveCmd> saved = ArgumentCaptor.forClass(OAuthAuthorizationSaveCmd.class);
        verify(appService).save(saved.capture());
        assertThat(saved.getValue().authorizationId()).isEqualTo("authorization-1");
        assertThat(saved.getValue().expectedRowVersion()).isNull();
    }

    @Test
    void doesNotResolveRevokedAuthorizationByAccessToken() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        OAuthAuthorization stored = revokedAuthorization(tokenDigester);
        RegisteredClient client = oauthClient(stored.getClientId().value());
        when(appService.queryByToken(any())).thenReturn(Optional.empty());
        when(clientRepository.findById(client.getId())).thenReturn(client);
        OAuthAuthorizationServiceAdapter service = service(
                appService, clientRepository, tokenDigester);

        OAuth2Authorization authorization = service.findByToken(
                RAW_ACCESS_TOKEN,
                OAuth2TokenType.ACCESS_TOKEN);

        assertThat(authorization).isNull();
    }

    @Test
    void restoresRevokedAuthorizationWithInactiveAccessToken() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        OAuthAuthorization stored = revokedAuthorization(tokenDigester);
        RegisteredClient client = oauthClient(stored.getClientId().value());
        when(appService.queryById(any())).thenReturn(Optional.of(
                OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(stored)));
        when(clientRepository.findById(client.getId())).thenReturn(client);
        OAuthAuthorizationServiceAdapter service = service(
                appService, clientRepository, tokenDigester);

        OAuth2Authorization authorization = service.findById(stored.getId().value());

        assertThat(authorization).isNotNull();
        assertThat(authorization.getAccessToken().isActive()).isFalse();
        assertThat(authorization.getRefreshToken().isActive()).isFalse();
        assertThat(authorization.getToken(OAuth2AuthorizationCode.class).isActive()).isFalse();
        assertThat(authorization.getToken(OidcIdToken.class).isActive()).isFalse();
    }

    @Test
    void originalReadVersionSurvivesFrameworkCopyAndSuccessfulSaveWithoutRebase() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();
        OAuthAuthorization stored = revokedAuthorization(digester);
        stored.setRowVersion(9L);
        OAuthAuthorizationDTO snapshot = OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(stored);
        assertThat(snapshot.rowVersion()).isEqualTo(9L);
        when(appService.queryById(any())).thenReturn(Optional.of(snapshot));
        RegisteredClient client = oauthClient(stored.getClientId().value());
        when(clients.findById(client.getId())).thenReturn(client);
        OAuthAuthorizationServiceAdapter adapter = service(appService, clients, digester);

        OAuth2Authorization read = adapter.findById(stored.getId().value());
        stored.setRowVersion(10L);
        OAuth2Authorization copied = OAuth2Authorization.from(read).build();
        adapter.save(copied);

        ArgumentCaptor<OAuthAuthorizationSaveCmd> saved = ArgumentCaptor.forClass(OAuthAuthorizationSaveCmd.class);
        verify(appService).save(saved.capture());
        assertThat(saved.getValue().expectedRowVersion()).isEqualTo(9L);
        assertThat(saved.getValue().status()).isEqualTo(OAuthAuthorizationStatus.REVOKED);
        assertThat((Long) read.getAttribute(OAuthAuthorizationAttributes.ROW_VERSION)).isEqualTo(9L);
        assertThat((Long) copied.getAttribute(OAuthAuthorizationAttributes.ROW_VERSION)).isEqualTo(9L);
        assertThat(snapshot.rowVersion()).isEqualTo(9L);
    }

    @Test
    void unknownTokenTypeRestoresOnlyTheCredentialMatchingTheRawToken() {
        OAuthAuthorizationAppService appService = mock(OAuthAuthorizationAppService.class);
        RegisteredClientRepository clientRepository = mock(RegisteredClientRepository.class);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        Instant issuedAt = Instant.parse("2020-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        Set<OAuthScope> scopes = Set.of(new OAuthScope("openid"));
        OAuthAuthorization stored = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-1"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("audit-producer"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.ADMINISTRATOR, "1"))
                .grantType(OAuthGrantType.AUTHORIZATION_CODE)
                .request(new OAuthAuthorizationRequest(
                        "https://iam.example.com/oauth2/authorize",
                        "https://audit.example.com/iam/callback",
                        "state",
                        "challenge",
                        "S256",
                        scopes))
                .authorizationCode(new OAuthAuthorizationCode(
                        digest(tokenDigester, "consumed-authorization-code"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt),
                        NOW.minusSeconds(60)))
                .accessToken(new OAuthAccessToken(
                        digest(tokenDigester, RAW_ACCESS_TOKEN),
                        new OAuthCredentialPeriod(issuedAt, expiresAt),
                        Map.of(
                                "iat", issuedAt.toString(),
                                "exp", expiresAt.toString(),
                                "nbf", issuedAt.toString(),
                                "appKey", "imAudit")))
                .refreshToken(new OAuthRefreshToken(
                        digest(tokenDigester, "raw-refresh-token"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt)))
                .idToken(new OidcIdentityToken(
                        digest(tokenDigester, "raw-id-token"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt),
                        java.util.Map.of("sub", "1")))
                .scopes(scopes)
                .status(OAuthAuthorizationStatus.ACTIVE)
                .build();
        stored.setRowVersion(7L);
        RegisteredClient client = browserClient(stored.getClientId().value());
        when(appService.queryByToken(any())).thenReturn(Optional.of(
                OAuthAuthorizationAppTransformer.INSTANCE.oauthAuthorizationDtoFrom(stored)));
        when(clientRepository.findById(client.getId())).thenReturn(client);
        OAuthAuthorizationServiceAdapter service = service(
                appService, clientRepository, tokenDigester);

        OAuth2Authorization authorization = service.findByToken(RAW_ACCESS_TOKEN, null);

        assertThat(authorization).isNotNull();
        assertThat(authorization.getToken(RAW_ACCESS_TOKEN).getToken())
                .isInstanceOf(OAuth2AccessToken.class);
        assertThat(authorization.getToken(RAW_ACCESS_TOKEN).isActive()).isTrue();
        assertThat(authorization.getAccessToken().getClaims())
                .containsEntry("iat", issuedAt)
                .containsEntry("exp", expiresAt)
                .containsEntry("nbf", issuedAt)
                .containsEntry("appKey", "imAudit");
        assertThat(authorization.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue())
                .isNotEqualTo(RAW_ACCESS_TOKEN);
        assertThat((Long) authorization.getAttribute(OAuthAuthorizationAttributes.ROW_VERSION)).isEqualTo(7L);
    }

    private OAuthAuthorizationServiceAdapter service(
            OAuthAuthorizationAppService appService,
            RegisteredClientRepository clientRepository,
            Sha256OAuthTokenDigester tokenDigester
    ) {
        return new OAuthAuthorizationServiceAdapter(
                appService,
                clientRepository,
                new OAuthAuthorizationTransformer(
                        tokenDigester,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                tokenDigester);
    }

    @Test
    void invalidatedAccessTokenMapsToRevokedAuthorization() {
        OAuth2Authorization authorization = OAuth2Authorization.from(oauthAuthorization())
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "revoked-access", NOW.minusSeconds(60), NOW.plusSeconds(300)),
                        metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true))
                .build();
        OAuthAuthorizationSaveCmd command = new OAuthAuthorizationTransformer(
                new Sha256OAuthTokenDigester(), Clock.fixed(NOW, ZoneOffset.UTC))
                .saveCommandFrom(authorization);

        assertThat(command.status()).isEqualTo(OAuthAuthorizationStatus.REVOKED);
        assertThat(command.revokedAt()).isEqualTo(NOW);
    }

    @Test
    void consumedCodeDoesNotRevokeAuthorization() {
        OAuth2Authorization authorization = OAuth2Authorization.from(oauthAuthorization())
                .token(new OAuth2AuthorizationCode("consumed-code", NOW.minusSeconds(60), NOW.plusSeconds(60)),
                        metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true))
                .build();
        OAuthAuthorizationSaveCmd command = new OAuthAuthorizationTransformer(
                new Sha256OAuthTokenDigester(), Clock.fixed(NOW, ZoneOffset.UTC))
                .saveCommandFrom(authorization);

        assertThat(command.status()).isEqualTo(OAuthAuthorizationStatus.ACTIVE);
        assertThat(command.revokedAt()).isNull();
        assertThat(command.authorizationCode().usedAt()).isEqualTo(NOW);
    }

    private OAuthAuthorization revokedAuthorization(
            Sha256OAuthTokenDigester tokenDigester
    ) {
        Instant issuedAt = Instant.parse("2020-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        OAuthAuthorization authorization = OAuthAuthorization.builder()
                .id(new OAuthAuthorizationId("authorization-1"))
                .appId(new AppId(1L))
                .clientId(new OAuthClientId("audit-producer"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "service-client"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS)
                .authorizationCode(new OAuthAuthorizationCode(
                        digest(tokenDigester, "raw-authorization-code"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt), null))
                .accessToken(new OAuthAccessToken(
                        digest(tokenDigester, RAW_ACCESS_TOKEN),
                        new OAuthCredentialPeriod(issuedAt, expiresAt), null))
                .refreshToken(new OAuthRefreshToken(
                        digest(tokenDigester, "raw-refresh-token"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt)))
                .idToken(new OidcIdentityToken(
                        digest(tokenDigester, "raw-id-token"),
                        new OAuthCredentialPeriod(issuedAt, expiresAt),
                        java.util.Map.of("sub", "service-client")))
                .scopes(Set.of(new OAuthScope("audit:ingest")))
                .status(OAuthAuthorizationStatus.REVOKED)
                .revokedAt(NOW.minusSeconds(60))
                .build();
        authorization.setRowVersion(3L);
        return authorization;
    }

    private OAuth2Authorization oauthAuthorization() {
        RegisteredClient client = oauthClient("audit-producer");
        return OAuth2Authorization.withRegisteredClient(client)
                .id("authorization-1")
                .principalName("service-client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("audit:ingest"))
                .build();
    }

    private OAuthCredentialDigest digest(
            Sha256OAuthTokenDigester tokenDigester,
            String value
    ) {
        return new OAuthCredentialDigest(tokenDigester.digest(value));
    }

    private RegisteredClient oauthClient(String oauthClientId) {
        return RegisteredClient.withId(oauthClientId)
                .clientId("audit-producer")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("audit:ingest")
                .build();
    }

    private RegisteredClient browserClient(String oauthClientId) {
        return RegisteredClient.withId(oauthClientId)
                .clientId("audit-producer")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://audit.example.com/iam/callback")
                .scope("openid")
                .build();
    }
}
