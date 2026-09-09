package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientListDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthClientPageQuery;
import com.co.kc.imchat.management.iam.model.io.OAuthClientListResponse;
import com.co.kc.imchat.management.iam.transformer.application.OAuthClientAppTransformer;
import com.co.kc.imchat.plugin.web.ImWebAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.APPLICATION_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.CLIENT_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(OAuthClientControllerTest.Config.class)
class OAuthClientControllerTest {
    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            JacksonAutoConfiguration.class,
                            ImWebAutoConfiguration.class));

    @jakarta.annotation.Resource
    private OAuthClientController securedController;

    @jakarta.annotation.Resource
    private OAuthClientAppService securedAppService;

    @Test
    void pagesClientsWithWireLongSafeNonSensitiveResponse() {
        long ownerAppId = 9_007_199_254_740_993L;
        long audienceAppId = 9_007_199_254_740_994L;
        Paging paging = new Paging(2, 10);
        OAuthClient sourceClient = OAuthClient.builder()
                .clientId(new OAuthClientId("im-admin-web"))
                .appId(new AppId(ownerAppId))
                .audienceAppId(new AppId(audienceAppId))
                .name(new OAuthClientName("IM Admin Browser"))
                .clientSecret(new OAuthClientSecret("sentinel-encoded-secret-never-expose"))
                .grantTypes(Set.of(OAuthGrantType.AUTHORIZATION_CODE))
                .scopes(Set.of(new OAuthScope("openid")))
                .redirectUris(Set.of(new RedirectUri(
                        URI.create("https://admin.example.com/login/callback"))))
                .postLogoutRedirectUris(Set.of(new RedirectUri(
                        URI.create("https://admin.example.com/"))))
                .status(OAuthClientStatus.ACTIVE)
                .build();
        OAuthClientListDTO client = OAuthClientAppTransformer.INSTANCE
                .oauthClientListDtoFrom(sourceClient, new AppKey("imAdmin"));
        OAuthClientAppService appService = mock(OAuthClientAppService.class);
        when(appService.page(new OAuthClientPageQuery(ownerAppId, paging)))
                .thenReturn(new PagingResult<>(paging, List.of(client), 1L));
        OAuthClientController controller = new OAuthClientController(appService);

        PagingResult<OAuthClientListResponse> response = controller.oauthClients(
                ownerAppId,
                paging.pageNo(),
                paging.pageSize());

        assertThat(response.records()).hasSize(1);
        verify(appService).page(new OAuthClientPageQuery(ownerAppId, paging));
        contextRunner.run(context -> {
            String json = context.getBean(ObjectMapper.class).writeValueAsString(response);
            String expected = """
                    {"paging":{"pageNo":2,"pageSize":10},"records":[{"clientId":"im-admin-web","appId":"9007199254740993","audienceAppId":"9007199254740994","audienceAppKey":"imAdmin","name":"IM Admin Browser","grantTypes":["AUTHORIZATION_CODE"],"scopes":["openid"],"redirectUris":["https://admin.example.com/login/callback"],"postLogoutRedirectUris":["https://admin.example.com/"],"status":"ACTIVE"}],"total":"1"}
                    """.strip();
            assertThat(json).isEqualTo(expected);
            assertThat(json).doesNotContain("sentinel-encoded-secret-never-expose");
        });
    }

    @Test
    void missingAuthenticationRejectsClientPage() {
        assertThatThrownBy(() -> securedController.oauthClients(1001L, 1, 20))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(authorities = CLIENT_WRITE)
    void wrongAuthorityRejectsClientPage() {
        assertThatThrownBy(() -> securedController.oauthClients(1001L, 1, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = APPLICATION_READ)
    void applicationReadAuthorityAllowsClientPage() {
        OAuthClientPageQuery query = new OAuthClientPageQuery(1001L, new Paging(1, 20));
        when(securedAppService.page(query))
                .thenReturn(new PagingResult<>(query.paging(), List.of(), 0L));

        assertThat(securedController.oauthClients(1001L, 1, 20).records()).isEmpty();

        verify(securedAppService).page(query);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(OAuthClientController.class)
    static class Config {
        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        OAuthClientAppService oauthClientAppService() {
            return mock(OAuthClientAppService.class);
        }
    }
}
