package com.co.kc.imchat.management.iam.sdk;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalogRegistrar;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionService;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.co.kc.imchat.management.iam.sdk.session.crypto.IamSessionCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
    void suppliesDefaultBffChainWhenApplicationDoesNotComposeSecurityChains() {
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
                        ImIamSdkAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
                    assertThat(chain.getFilters()).contains(context.getBean(IamSecurityFilter.class));
                    assertThat(chain.matches(new org.springframework.mock.web.MockHttpServletRequest(
                            "GET", "/api/browser-probe"))).isTrue();
                });
    }

    @Test
    void preventsBffFilterFromServletContainerAutoRegistration() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SecurityFilterChain.class, () -> mock(SecurityFilterChain.class))
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.isEnabled()).isFalse();
                    assertThat(registration.getFilter())
                            .isSameAs(context.getBean(IamSecurityFilter.class));
                });
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

    @Test
    void usesServiceUriForServerToServerIamCalls() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/oauth2/introspect", exchange -> {
            byte[] body = "{\"active\":false}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            contextRunner
                    .withBean(StringRedisTemplate.class,
                            () -> mock(StringRedisTemplate.class))
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withBean(SecurityFilterChain.class,
                            () -> mock(SecurityFilterChain.class))
                    .withPropertyValues(enabledProperties())
                    .withPropertyValues(
                            "im.iam.issuer=http://localhost:1",
                            "im.iam.service-uri=http://localhost:"
                                    + server.getAddress().getPort())
                    .run(context -> assertThat(context.getBean(IamIntrospectionClient.class)
                            .introspect("access-token").active()).isFalse());
        } finally {
            server.stop(0);
        }
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
