package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter.OAuthAuthorizationServiceAdapter;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamTokenPersistenceTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-08-26T08:00:00Z");

    @Test
    void persistsOnlyTokenDigestsAndRestoresThePresentedAccessToken() {
        DbIamOAuthAuthorizationService authorizationService = mock(DbIamOAuthAuthorizationService.class);
        RegisteredClientRepository clients = clients();
        AdministratorRepository administrators = administrators();
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        Clock clock = Clock.fixed(ISSUED_AT.plusSeconds(60), ZoneOffset.UTC);
        OAuthAuthorizationRepository authorizationRepository =
                new MysqlOAuthAuthorizationRepository(authorizationService);
        OAuthAuthorizationAppService appService = new OAuthAuthorizationAppService(
                authorizationRepository,
                oauthClients("im-admin-client"),
                new OAuthAuthorizationService(administrators));
        OAuthAuthorizationServiceAdapter service = new OAuthAuthorizationServiceAdapter(
                appService,
                clients,
                new OAuthAuthorizationTransformer(tokenDigester, clock),
                tokenDigester);

        service.save(authorization());

        ArgumentCaptor<DbIamOAuthAuthorization> captor =
                ArgumentCaptor.forClass(DbIamOAuthAuthorization.class);
        verify(authorizationService).save(captor.capture());
        DbIamOAuthAuthorization stored = captor.getValue();
        stored.setId(1L);
        assertThat(stored.getAuthorizationCodeDigest()).hasSize(64).doesNotContain("raw-code");
        assertThat(stored.getAccessTokenDigest()).hasSize(64).doesNotContain("raw-access-token");
        assertThat(stored.getRefreshTokenDigest()).hasSize(64).doesNotContain("raw-refresh-token");
        assertThat(stored.toString())
                .doesNotContain("raw-code", "raw-access-token", "raw-refresh-token");

        when(authorizationService.getByTokenDigest(any(), any()))
                .thenReturn(Optional.of(stored));
        OAuth2Authorization restored = service.findByToken(
                "raw-access-token", OAuth2TokenType.ACCESS_TOKEN);

        assertThat(restored.getAccessToken().getToken().getTokenValue())
                .isEqualTo("raw-access-token");
        assertThat(restored.getRefreshToken().getToken().getTokenValue())
                .isEqualTo(stored.getRefreshTokenDigest());
        assertThat(restored.<OAuth2AuthorizationRequest>getAttribute(
                        OAuth2AuthorizationRequest.class.getName()).getRedirectUri())
                .isEqualTo("https://admin.example.com/login/oauth2/code/iam");
    }

    @Test
    void persistsMachineAuthorizationWithoutBrowserOrAdministratorIdentity() {
        DbIamOAuthAuthorizationService authorizationService = mock(DbIamOAuthAuthorizationService.class);
        RegisteredClient machineClient = machineClient();
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        when(clients.findById("im-admin-audit")).thenReturn(machineClient);
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        Clock clock = Clock.fixed(ISSUED_AT.plusSeconds(60), ZoneOffset.UTC);
        OAuthClientRepository oauthClientRepository = oauthClients("im-admin-audit");
        OAuthAuthorizationRepository authorizationRepository =
                new MysqlOAuthAuthorizationRepository(authorizationService);
        OAuthAuthorizationAppService appService = new OAuthAuthorizationAppService(
                authorizationRepository,
                oauthClientRepository,
                new OAuthAuthorizationService(
                        mock(AdministratorRepository.class)));
        OAuthAuthorizationServiceAdapter service = new OAuthAuthorizationServiceAdapter(
                appService,
                clients,
                new OAuthAuthorizationTransformer(tokenDigester, clock),
                tokenDigester);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "machine-access-token",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(900),
                Set.of("audit:ingest"));
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(machineClient)
                .id("machine-authorization-1")
                .principalName("im-admin-audit")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("audit:ingest"))
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("appKey", "imAdmin", "clientId", "im-admin-audit")))
                .build();

        service.save(authorization);

        ArgumentCaptor<DbIamOAuthAuthorization> captor =
                ArgumentCaptor.forClass(DbIamOAuthAuthorization.class);
        verify(authorizationService).save(captor.capture());
        DbIamOAuthAuthorization stored = captor.getValue();
        assertThat(stored.getOauthClientId()).isEqualTo("im-admin-audit");
        assertThat(stored.getAppId()).isEqualTo(1L);
        assertThat(stored.getPrincipalType()).isEqualTo("CLIENT");
        assertThat(stored.getAuthorizationUri()).isNull();
        assertThat(stored.getRedirectUri()).isNull();
    }

    @Test
    void persistsAuthorizationCodeConsumptionFromFinalSpringState() {
        DbIamOAuthAuthorizationService authorizationService = mock(DbIamOAuthAuthorizationService.class);
        OAuthAuthorizationServiceAdapter service = service(authorizationService);
        DbIamOAuthAuthorization stored = storedAuthorization(service, authorizationService);
        reset(authorizationService);
        when(authorizationService.getByAuthorizationId(stored.getAuthorizationId()))
                .thenReturn(Optional.of(stored));
        OAuth2Authorization source = authorization();
        OAuth2AuthorizationCode code = source.getToken(OAuth2AuthorizationCode.class).getToken();
        OAuth2Authorization exchanged = OAuth2Authorization.from(source)
                .token(code, metadata -> metadata.put(
                        OAuth2Authorization.Token.INVALIDATED_METADATA_NAME,
                        true))
                .build();

        service.save(exchanged);

        ArgumentCaptor<DbIamOAuthAuthorization> saved =
                ArgumentCaptor.forClass(DbIamOAuthAuthorization.class);
        verify(authorizationService).updateById(saved.capture());
        assertThat(saved.getValue().getAuthorizationCodeUsedAt())
                .isEqualTo(ISSUED_AT.plusSeconds(60));
    }

    @Test
    void returnsConsumedAuthorizationCodeForSpringReuseValidation() {
        DbIamOAuthAuthorizationService authorizationService = mock(DbIamOAuthAuthorizationService.class);
        OAuthAuthorizationServiceAdapter service = service(authorizationService);
        DbIamOAuthAuthorization stored = storedAuthorization(service, authorizationService);
        stored.setAuthorizationCodeUsedAt(ISSUED_AT.plusSeconds(30));
        reset(authorizationService);
        when(authorizationService.getByTokenDigest(anyString(), any()))
                .thenReturn(Optional.of(stored));
        OAuth2Authorization authorization = service.findByToken(
                "raw-code",
                new OAuth2TokenType("code"));

        assertThat(authorization).isNotNull();
        assertThat(authorization.getToken(OAuth2AuthorizationCode.class).isActive())
                .isFalse();
        verify(authorizationService, never()).updateById(any());
    }

    private OAuthAuthorizationServiceAdapter service(
            DbIamOAuthAuthorizationService authorizationService
    ) {
        Sha256OAuthTokenDigester tokenDigester = new Sha256OAuthTokenDigester();
        Clock clock = Clock.fixed(ISSUED_AT.plusSeconds(60), ZoneOffset.UTC);
        OAuthAuthorizationRepository authorizationRepository =
                new MysqlOAuthAuthorizationRepository(authorizationService);
        OAuthAuthorizationAppService appService = new OAuthAuthorizationAppService(
                authorizationRepository,
                oauthClients("im-admin-client"),
                new OAuthAuthorizationService(administrators()));
        return new OAuthAuthorizationServiceAdapter(
                appService,
                clients(),
                new OAuthAuthorizationTransformer(tokenDigester, clock),
                tokenDigester);
    }

    private DbIamOAuthAuthorization storedAuthorization(
            OAuthAuthorizationServiceAdapter service,
            DbIamOAuthAuthorizationService authorizationService
    ) {
        service.save(authorization());
        ArgumentCaptor<DbIamOAuthAuthorization> captor =
                ArgumentCaptor.forClass(DbIamOAuthAuthorization.class);
        verify(authorizationService).save(captor.capture());
        DbIamOAuthAuthorization stored = captor.getValue();
        stored.setId(1L);
        return stored;
    }

    private static OAuth2Authorization authorization() {
        RegisteredClient client = registeredClient();
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://iam.example.com/oauth2/authorize")
                .clientId(client.getClientId())
                .redirectUri("https://admin.example.com/login/oauth2/code/iam")
                .scopes(Set.of("openid", "admin.user.read"))
                .state("state-value")
                .additionalParameters(Map.of(
                        "code_challenge", "challenge-value",
                        "code_challenge_method", "S256"))
                .build();
        UsernamePasswordAuthenticationToken principal =
                UsernamePasswordAuthenticationToken.authenticated(
                        "10", "N/A", Set.of());
        OAuth2AuthorizationCode code = new OAuth2AuthorizationCode(
                "raw-code", ISSUED_AT, ISSUED_AT.plusSeconds(300));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "raw-access-token",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(900),
                Set.of("openid", "admin.user.read"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "raw-refresh-token", ISSUED_AT, ISSUED_AT.plusSeconds(28_800));
        return OAuth2Authorization.withRegisteredClient(client)
                .id("authorization-1")
                .principalName("10")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "admin.user.read"))
                .attribute(OAuth2AuthorizationRequest.class.getName(), request)
                .attribute(Principal.class.getName(), principal)
                .token(code)
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", "10", "appKey", "imAdmin")))
                .refreshToken(refreshToken)
                .build();
    }

    private static RegisteredClientRepository clients() {
        RegisteredClient client = registeredClient();
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        when(repository.findById("im-admin-client")).thenReturn(client);
        return repository;
    }

    private static OAuthClientRepository oauthClients(String clientId) {
        OAuthClient client = mock(OAuthClient.class);
        when(client.getClientId()).thenReturn(new OAuthClientId(clientId));
        when(client.getAppId()).thenReturn(new AppId(1L));
        when(client.isActive()).thenReturn(true);
        OAuthClientRepository repository = mock(OAuthClientRepository.class);
        when(repository.find(new OAuthClientId(clientId))).thenReturn(Optional.of(client));
        return repository;
    }

    private static AdministratorRepository administrators() {
        Administrator administrator = mock(Administrator.class);
        when(administrator.getId()).thenReturn(new AdministratorId(10L));
        when(administrator.isActive()).thenReturn(true);
        AdministratorRepository repository = mock(AdministratorRepository.class);
        when(repository.find(new AdministratorId(10L)))
                .thenReturn(Optional.of(administrator));
        return repository;
    }

    private static RegisteredClient registeredClient() {
        return RegisteredClient.withId("im-admin-client")
                .clientId("im-admin-client")
                .clientSecret("{noop}client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://admin.example.com/login/oauth2/code/iam")
                .scope("openid")
                .scope("admin.user.read")
                .build();
    }

    private static RegisteredClient machineClient() {
        return RegisteredClient.withId("im-admin-audit")
                .clientId("im-admin-audit")
                .clientSecret("{noop}machine-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("audit:ingest")
                .build();
    }
}
