package com.co.kc.imchat.management.iam.sdk;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalogRegistrar;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionService;
import com.co.kc.imchat.management.iam.sdk.session.crypto.IamSessionCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ImIamSdkAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImIamSdkAutoConfiguration.class));

    @Test
    void remainsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(ImIamSdkAutoConfiguration.class));
    }

    @Test
    void doesNotEnableFromTheRemovedClientPrefix() {
        contextRunner
                .withPropertyValues("im.iam.client.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ImIamSdkAutoConfiguration.class));
    }

    @Test
    void failsStartupWhenEnabledConfigurationIsIncomplete() {
        contextRunner
                .withPropertyValues("im.iam.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enablesWhenExplicitlyConfigured() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
                .withPropertyValues(enabledProperties())
                .run(context -> assertThat(context)
                        .hasSingleBean(ImIamSdkAutoConfiguration.class)
                        .hasSingleBean(AnnotationTemplateExpressionDefaults.class)
                        .hasSingleBean(IamSessionCipher.class)
                        .hasSingleBean(IamIntrospectionService.class));
    }

    @Test
    void createsCatalogRegistrarWhenCatalogDefinitionExistsWithoutCredentials() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
                .withBean(IamPermissionCatalog.class, () -> List::of)
                .withPropertyValues(enabledProperties())
                .run(context -> assertThat(context)
                        .hasSingleBean(IamPermissionCatalogRegistrar.class));
    }

    @Test
    void createsDedicatedIamRestClientWhenApplicationDefinesAnotherRestClient() {
        contextRunner
                .withBean("brokerRestClient", RestClient.class, RestClient::create)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
                .withBean(IamPermissionCatalog.class, () -> List::of)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context.getBeansOfType(RestClient.class))
                            .containsKeys("brokerRestClient", "iamRestClient");
                    IamPermissionCatalogRegistrar registrar =
                            context.getBean(IamPermissionCatalogRegistrar.class);
                    assertThat(ReflectionTestUtils.getField(registrar, "restClient"))
                            .isSameAs(context.getBean("iamRestClient", RestClient.class));
                });
    }

    private String[] enabledProperties() {
        return new String[]{
                "im.iam.enabled=true",
                "im.iam.issuer=https://iam.example.com",
                "im.iam.application.key=imAdmin",
                "im.iam.application.clients.web.client-id=im-admin-client",
                "im.iam.application.clients.web.client-secret=test-client-secret",
                "im.iam.application.clients.web.redirect-uri="
                        + "https://admin.example.com/iam/callback",
                "im.iam.application.clients.web.post-logout-redirect-uri="
                        + "https://admin.example.com/",
                "im.iam.application.session.encryption-key="
                        + Base64.getEncoder().encodeToString(new byte[32])
        };
    }
}
