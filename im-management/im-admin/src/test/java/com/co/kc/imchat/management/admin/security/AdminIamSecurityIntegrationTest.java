package com.co.kc.imchat.management.admin.security;

import com.co.kc.imchat.management.admin.infrastructure.config.beans.AdminSecurityBeans;
import com.co.kc.imchat.management.iam.sdk.ImIamSdkAutoConfiguration;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionCache;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AdminIamSecurityIntegrationTest.SecurityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "dubbo.application.qos-enable=false",
        "dubbo.registry.address=N/A",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "im.iam.enabled=true",
        "im.iam.issuer=https://iam.example.com",
        "im.iam.application.key=imAdmin",
        "im.iam.application.clients.web.client-id=im-admin-client",
        "im.iam.application.clients.web.client-secret=test-secret",
        "im.iam.application.clients.web.redirect-uri=https://admin.example.com/iam/callback",
        "im.iam.application.clients.web.post-logout-redirect-uri=https://admin.example.com/",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret=",
        "im.iam.application.session.cookie-name=IM_ADMIN_IAM_SESSION",
        "im.iam.application.session.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "server.port=0", "management.server.port=0"
})
class AdminIamSecurityIntegrationTest {
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ServletContext servletContext;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private IamApplicationSessionRepository sessions;
    @MockitoBean
    private IamIntrospectionClient introspectionClient;
    @MockitoBean
    private IamIntrospectionCache cache;

    @Test
    void cookieAuthenticatesThroughHostChainDespiteDisabledServletFilterRegistration() {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(sessions.find("admin-session")).thenReturn(Optional.of(new IamApplicationSession(
                "admin-session", new IamTokenSet("admin-token", expiresAt, "refresh", expiresAt),
                "csrf", Instant.EPOCH, Instant.EPOCH)));
        when(introspectionClient.introspect("admin-token")).thenReturn(new IamIntrospectionResult(
                true, 41L, "admin", "imAdmin", "im-admin-client", Set.of("imAdmin"),
                Set.of("admin-probe:read"), expiresAt, "ADMINISTRATOR", Set.of()));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "IM_ADMIN_IAM_SESSION=admin-session");

        ResponseEntity<String> response = http.exchange("/api/security-probe", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("41");
        assertThat(servletContext.getFilterRegistrations()).containsKey("springSecurityFilterChain");
        assertThat(servletContext.getFilterRegistrations().values())
                .extracting(FilterRegistration::getClassName)
                .doesNotContain(IamSecurityFilter.class.getName());
    }

    @Test
    void bearerAloneDoesNotAuthenticateAdminBrowserApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("machine-token");

        ResponseEntity<String> response = http.exchange("/api/security-probe", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getBody()).contains("\"code\":10001");
        verifyNoInteractions(sessions, introspectionClient, cache);
    }

    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @Import(AdminSecurityBeans.class)
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class,
            SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class,
            ImIamSdkAutoConfiguration.class})
    static class SecurityApplication {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/security-probe")
        @PreAuthorize("hasAuthority('admin-probe:read')")
        String identity() {
            return IamSecurityContext.currentPrincipal().orElseThrow().administratorId().toString();
        }
    }
}
