package com.co.kc.imchat.management.audit.security;

import com.co.kc.imchat.management.audit.infrastructure.config.beans.AuditSecurityBeans;
import com.co.kc.imchat.management.iam.sdk.ImIamSdkAutoConfiguration;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionCache;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.security.IamApplicationPrincipal;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditIamSecurityIntegrationTest.SecurityApplication.class,
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
        "im.iam.application.key=imAudit",
        "im.iam.application.clients.web.client-id=im-audit-client",
        "im.iam.application.clients.web.client-secret=test-secret",
        "im.iam.application.clients.web.redirect-uri=https://audit.example.com/iam/callback",
        "im.iam.application.clients.web.post-logout-redirect-uri=https://audit.example.com/",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret=",
        "im.iam.application.session.cookie-name=IM_AUDIT_IAM_SESSION",
        "im.iam.application.session.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "server.address=127.0.0.1", "server.port=0", "management.server.port=0"
})
class AuditIamSecurityIntegrationTest {
    @LocalServerPort
    private int port;
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
    @MockitoBean
    private OpaqueTokenIntrospector auditOpaqueTokenIntrospector;

    @BeforeEach
    void targetBoundLoopbackAddress() {
        http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://127.0.0.1:" + port));
    }

    @Test
    void cookieAuthenticatesAuditBrowserChainDespiteDisabledServletFilterRegistration() {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(sessions.find("audit-session")).thenReturn(Optional.of(new IamApplicationSession(
                "audit-session", new IamTokenSet("audit-token", expiresAt, "refresh", expiresAt),
                "csrf", Instant.EPOCH, Instant.EPOCH)));
        when(introspectionClient.introspect("audit-token")).thenReturn(new IamIntrospectionResult(
                true, 42L, "auditor", "imAudit", "im-audit-client", Set.of("imAudit"),
                Set.of("audit:read"), expiresAt, "ADMINISTRATOR", Set.of()));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "IM_AUDIT_IAM_SESSION=audit-session");

        ResponseEntity<String> response = http.exchange("/api/security-probe", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("42");
        assertThat(servletContext.getFilterRegistrations()).containsKey("springSecurityFilterChain");
        assertThat(servletContext.getFilterRegistrations().values())
                .extracting(FilterRegistration::getClassName)
                .doesNotContain(IamSecurityFilter.class.getName());
        verifyNoInteractions(auditOpaqueTokenIntrospector);
    }

    @Test
    void internalPostUsesMachineBearerWithoutCsrfOrBrowserSessionRestoration() {
        when(auditOpaqueTokenIntrospector.introspect("ingest-token"))
                .thenReturn(new DefaultOAuth2AuthenticatedPrincipal("imAdmin", Map.of(
                        "appKey", "imAdmin", "client_id", "im-admin-audit",
                        "aud", List.of("imAudit"), "iat", Instant.EPOCH,
                        "exp", Instant.parse("2099-01-01T00:00:00Z")),
                        List.of(new SimpleGrantedAuthority("SCOPE_audit:ingest"))));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("ingest-token");
        /* 机器链不能因过期浏览器 Cookie 调用 BFF Filter。 */
        headers.add(HttpHeaders.COOKIE, "IM_AUDIT_IAM_SESSION=stale-browser-session");

        ResponseEntity<String> response = http.exchange("/internal/security-probe", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("imAdmin:im-admin-audit");
        verifyNoInteractions(sessions, introspectionClient, cache);
    }

    @Test
    void browserCookieCannotAuthenticateInternalPost() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "IM_AUDIT_IAM_SESSION=audit-session");

        ResponseEntity<String> response = http.exchange("/internal/security-probe", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(sessions, introspectionClient, cache, auditOpaqueTokenIntrospector);
    }

    @Test
    void machineBearerCannotAuthenticateAuditBrowserApi() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("ingest-token");

        ResponseEntity<String> response = http.exchange("/api/security-probe", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getBody()).contains("\"code\":10001");
        verifyNoInteractions(sessions, introspectionClient, cache, auditOpaqueTokenIntrospector);
    }

    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @Import(AuditSecurityBeans.class)
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
        @PreAuthorize("hasAuthority('audit:read')")
        String browserIdentity() {
            return IamSecurityContext.currentPrincipal().orElseThrow().administratorId().toString();
        }

        @PostMapping("/internal/security-probe")
        String machineIdentity(Authentication authentication) {
            IamApplicationPrincipal principal = (IamApplicationPrincipal) authentication.getPrincipal();
            return principal.appKey() + ":" + principal.clientId();
        }
    }
}
