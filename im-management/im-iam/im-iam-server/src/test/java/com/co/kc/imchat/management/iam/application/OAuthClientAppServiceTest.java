package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.application.service.OAuthClientSecretService;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientRegisterCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientAccessUpdateCmd;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAdministratorTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthApplicationTokenClaimsDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthAdministratorTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthApplicationTokenClaimsQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientRegistrationQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OAuthClientAppServiceTest {

    @Test
    void pagesClientsWithResolvedAudienceAndCompleteNonSensitiveDetails() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        AppId ownerAppId = new AppId(1001L);
        AppId audienceAppId = new AppId(2001L);
        Paging paging = new Paging(1, 20);
        OAuthClient client = OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-web"))
                .appId(ownerAppId)
                .audienceAppId(audienceAppId)
                .name(new OAuthClientName("IM Admin Browser"))
                .clientSecret(new OAuthClientSecret("encoded-client-secret"))
                .grantTypes(Set.of(
                        OAuthGrantType.AUTHORIZATION_CODE,
                        OAuthGrantType.REFRESH_TOKEN))
                .scopes(Set.of(new OAuthScope("openid"), new OAuthScope("profile")))
                .redirectUris(Set.of(new RedirectUri(
                        URI.create("https://admin.example.com/login/callback"))))
                .postLogoutRedirectUris(Set.of(new RedirectUri(
                        URI.create("https://admin.example.com/"))))
                .status(OAuthClientStatus.ACTIVE)
                .build();
        OAuthClient secondClient = OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-machine"))
                .appId(ownerAppId)
                .audienceAppId(audienceAppId)
                .name(new OAuthClientName("IM Admin Machine"))
                .clientSecret(new OAuthClientSecret("second-encoded-client-secret"))
                .grantTypes(Set.of(OAuthGrantType.CLIENT_CREDENTIALS))
                .scopes(Set.of(new OAuthScope("audit:read")))
                .redirectUris(Set.of())
                .postLogoutRedirectUris(Set.of())
                .status(OAuthClientStatus.ACTIVE)
                .build();
        when(apps.find(ownerAppId)).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(clients.page(ownerAppId, paging))
                .thenReturn(new PagingResult<>(paging, List.of(client, secondClient), 2L));
        when(apps.find(Set.of(audienceAppId)))
                .thenReturn(List.of(app(2001L, "imAudit")));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        PagingResult<OAuthClientListDTO> result = service.page(
                new OAuthClientPageQuery(ownerAppId.value(), paging));

        assertThat(result.paging()).isEqualTo(paging);
        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.records()).containsExactly(
                new OAuthClientListDTO(
                        "im-admin-web",
                        1001L,
                        2001L,
                        "imAudit",
                        "IM Admin Browser",
                        Set.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN),
                        Set.of("openid", "profile"),
                        Set.of("https://admin.example.com/login/callback"),
                        Set.of("https://admin.example.com/"),
                        OAuthClientStatus.ACTIVE),
                new OAuthClientListDTO(
                        "im-admin-machine",
                        1001L,
                        2001L,
                        "imAudit",
                        "IM Admin Machine",
                        Set.of(OAuthGrantType.CLIENT_CREDENTIALS),
                        Set.of("audit:read"),
                        Set.of(),
                        Set.of(),
                        OAuthClientStatus.ACTIVE));
        assertThat(result.toString())
                .doesNotContain("encoded-client-secret", "second-encoded-client-secret");
        verify(clients).page(ownerAppId, paging);
        verify(apps).find(Set.of(audienceAppId));
    }

    @Test
    void rejectsClientPageForUnknownOwnerApplication() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        AppId ownerAppId = new AppId(1001L);
        Paging paging = new Paging(1, 20);
        when(apps.find(ownerAppId)).thenReturn(Optional.empty());
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        assertThatThrownBy(() -> service.page(
                new OAuthClientPageQuery(ownerAppId.value(), paging)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("所属应用不存在");
        verifyNoInteractions(clients);
    }

    @Test
    void queriesActiveOAuthClientRegistrationWithoutExposingEncodedSecret() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthClient client = mock(OAuthClient.class);
        OAuthClientId clientId = new OAuthClientId("im-admin-audit");
        AppId ownerAppId = new AppId(1001L);
        AppId audienceAppId = new AppId(2001L);
        when(clients.find(clientId)).thenReturn(Optional.of(client));
        when(client.isActive()).thenReturn(true);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientSecret()).thenReturn(
                new OAuthClientSecret("encoded-client-secret"));
        when(client.getName()).thenReturn(new OAuthClientName("Audit Producer"));
        when(client.getAppId()).thenReturn(ownerAppId);
        when(client.getAudienceAppId()).thenReturn(audienceAppId);
        when(client.getGrantTypes()).thenReturn(Set.of(OAuthGrantType.CLIENT_CREDENTIALS));
        when(client.getScopes()).thenReturn(Set.of(new OAuthScope("audit:ingest")));
        when(client.getRedirectUris()).thenReturn(Set.of());
        when(client.getPostLogoutRedirectUris()).thenReturn(Set.of());
        when(apps.find(ownerAppId)).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(apps.find(audienceAppId)).thenReturn(Optional.of(app(2001L, "imAudit")));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        OAuthClientRegistrationDTO registration = service.queryRegistration(
                new OAuthClientRegistrationQuery(clientId.value())).orElseThrow();

        assertThat(registration.clientId()).isEqualTo(clientId.value());
        assertThat(registration.scopes()).containsExactly("audit:ingest");
        assertThat(registration.toString()).doesNotContain("encoded-client-secret");
    }

    @Test
    void resolvesMachineTokenClaimsFromOAuthClient() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthClient client = mock(OAuthClient.class);
        OAuthClientId clientId = new OAuthClientId("im-admin-audit");
        AppId ownerAppId = new AppId(1001L);
        AppId audienceAppId = new AppId(2001L);
        when(clients.find(clientId)).thenReturn(Optional.of(client));
        when(client.getClientId()).thenReturn(clientId);
        when(client.getAppId()).thenReturn(ownerAppId);
        when(client.getAudienceAppId()).thenReturn(audienceAppId);
        when(apps.find(ownerAppId)).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(apps.find(audienceAppId)).thenReturn(Optional.of(app(2001L, "imAudit")));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        OAuthApplicationTokenClaimsDTO claims = service.getApplicationTokenClaims(
                new OAuthApplicationTokenClaimsQuery(clientId.value()));

        assertThat(claims.subject()).isEqualTo(clientId.value());
        assertThat(claims.sourceAppKey()).isEqualTo("imAdmin");
        assertThat(claims.audienceAppKey()).isEqualTo("imAudit");
    }

    @Test
    void resolvesAdministratorTokenClaimsFromCurrentPermissions() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        AdministratorRepository administrators = mock(AdministratorRepository.class);
        AdministratorAuthorizationService authorizationService =
                mock(AdministratorAuthorizationService.class);
        OAuthClient client = mock(OAuthClient.class);
        Administrator administrator = mock(Administrator.class);
        OAuthClientId clientId = new OAuthClientId("im-admin-web");
        AppId appId = new AppId(1001L);
        AdministratorId administratorId = new AdministratorId(10L);
        when(clients.find(clientId)).thenReturn(Optional.of(client));
        when(client.getAppId()).thenReturn(appId);
        when(client.getAudienceAppId()).thenReturn(appId);
        when(apps.find(appId)).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(administrators.find(administratorId)).thenReturn(Optional.of(administrator));
        when(administrator.isActive()).thenReturn(true);
        when(administrator.getId()).thenReturn(administratorId);
        when(administrator.getUsername()).thenReturn(new AdministratorUsername("root"));
        when(authorizationService.getPermissions(administratorId, appId))
                .thenReturn(Set.of(
                        new ApplicationPermissionCode("user:write"),
                        new ApplicationPermissionCode("user:read")));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                administrators,
                authorizationService,
                mock());

        OAuthAdministratorTokenClaimsDTO claims =
                service.getAdministratorTokenClaims(
                        new OAuthAdministratorTokenClaimsQuery(
                                clientId.value(),
                                administratorId.value()));

        assertThat(claims.subject()).isEqualTo("10");
        assertThat(claims.username()).isEqualTo("root");
        assertThat(claims.authorities()).containsExactly("user:read", "user:write");
    }

    @Test
    void registersMachineClientUnderResolvedAppId() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthClientSecretService codec = mock(OAuthClientSecretService.class);
        when(apps.find(new AppKey("imAdmin"))).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(apps.find(new AppId(2001L))).thenReturn(Optional.of(app(2001L, "imAudit")));
        when(codec.encode(new OAuthRawClientSecret("raw-client-secret-12345")))
                .thenReturn(new OAuthClientSecret("encoded-client-secret"));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                codec,
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        service.register(new OAuthClientRegisterCmd(
                "imAdmin",
                2001L,
                "Audit Producer",
                "im-audit-producer",
                "raw-client-secret-12345",
                Set.of(OAuthGrantType.CLIENT_CREDENTIALS),
                Set.of("audit:ingest"),
                Set.of(),
                Set.of()));

        ArgumentCaptor<OAuthClient> captor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(clients).save(captor.capture());
        assertThat(captor.getValue().getAppId()).isEqualTo(new AppId(1001L));
        assertThat(captor.getValue().getAudienceAppId()).isEqualTo(new AppId(2001L));
        assertThat(captor.getValue().getClientId())
                .isEqualTo(new OAuthClientId("im-audit-producer"));
        assertThat(captor.getValue().getGrantTypes())
                .containsExactly(OAuthGrantType.CLIENT_CREDENTIALS);
        assertThat(captor.getValue().getRedirectUris()).isEmpty();
    }

    @Test
    void registersBrowserClientUnderResolvedAppId() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthClientSecretService codec = mock(OAuthClientSecretService.class);
        when(apps.find(new AppKey("imAdmin"))).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(apps.find(new AppId(1001L))).thenReturn(Optional.of(app(1001L, "imAdmin")));
        when(codec.encode(new OAuthRawClientSecret("raw-client-secret-12345")))
                .thenReturn(new OAuthClientSecret("encoded-client-secret"));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                codec,
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                mock());

        service.register(new OAuthClientRegisterCmd(
                "imAdmin",
                1001L,
                "IM Admin Browser",
                "im-admin-web",
                "raw-client-secret-12345",
                Set.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN),
                Set.of("openid", "profile"),
                Set.of("https://admin.example.com/login/callback"),
                Set.of("https://admin.example.com/")));

        ArgumentCaptor<OAuthClient> captor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(clients).save(captor.capture());
        assertThat(captor.getValue().getGrantTypes()).containsExactlyInAnyOrder(
                OAuthGrantType.AUTHORIZATION_CODE,
                OAuthGrantType.REFRESH_TOKEN);
        assertThat(captor.getValue().getRedirectUris())
                .extracting(uri -> uri.value().toString())
                .containsExactly("https://admin.example.com/login/callback");
        assertThat(captor.getValue().getPostLogoutRedirectUris())
                .extracting(uri -> uri.value().toString())
                .containsExactly("https://admin.example.com/");
    }

    @Test
    void updatesClientAccessAndRevokesAuthorizationsIssuedToTheClient() {
        ApplicationRepository apps = mock(ApplicationRepository.class);
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        OAuthSessionRepository sessions = mock(OAuthSessionRepository.class);
        OAuthClientId clientId = new OAuthClientId("im-admin-web");
        OAuthClient client = OAuthClient.builder()
                .clientId(clientId)
                .appId(new AppId(1001L))
                .audienceAppId(new AppId(1001L))
                .name(new OAuthClientName("IM Admin Browser"))
                .clientSecret(new OAuthClientSecret("encoded-client-secret"))
                .grantTypes(Set.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN))
                .scopes(Set.of(new OAuthScope("openid")))
                .redirectUris(Set.of(new RedirectUri(URI.create("https://admin.example.com/callback"))))
                .postLogoutRedirectUris(Set.of(new RedirectUri(URI.create("https://admin.example.com/"))))
                .status(OAuthClientStatus.ACTIVE)
                .build();
        when(clients.find(clientId)).thenReturn(Optional.of(client));
        OAuthClientAppService service = new OAuthClientAppService(
                apps,
                clients,
                mock(OAuthClientSecretService.class),
                mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class),
                sessions);

        service.updateAccess(new OAuthClientAccessUpdateCmd(
                clientId.value(),
                Set.of("openid", "profile"),
                Set.of("https://admin.example.com/updated-callback"),
                Set.of("https://admin.example.com/signed-out")));

        assertThat(client.getScopes()).containsExactlyInAnyOrder(
                new OAuthScope("openid"),
                new OAuthScope("profile"));
        assertThat(client.getRedirectUris()).containsExactly(
                new RedirectUri(URI.create("https://admin.example.com/updated-callback")));
        verify(clients).save(client);
        verify(sessions).revoke(eq(clientId), any(Instant.class));
    }

    private Application app(Long appId, String appKey) {
        return new Application(new AppId(appId), new AppKey(appKey),
                new AppName("审计服务"), AppStatus.ACTIVE);
    }
}
