package com.co.kc.imchat.management.ops.security;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityContext;
import com.co.kc.imchat.management.ops.ImOpsApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionException;
import jakarta.servlet.http.Cookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.Optional;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionCache;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ImOpsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.audit.enabled=false",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret=",
        "im.ops.cli.client-ids=im-ops-cli",
        "im.ops.cli.scopes=ops.read"
})
@AutoConfigureMockMvc
@Import(OpsCliSecurityIntegrationTest.ProbeConfiguration.class)
class OpsCliSecurityIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private jakarta.servlet.ServletContext servletContext;

    @Test
    void servletContainerRegistersNeitherIamFilterOutsideSecurityChains() {
        org.assertj.core.api.Assertions.assertThat(servletContext.getServerInfo()).startsWith("Apache Tomcat/");
        org.assertj.core.api.Assertions.assertThat(servletContext.getFilterRegistrations())
                .containsKey("springSecurityFilterChain");
        org.assertj.core.api.Assertions.assertThat(servletContext.getFilterRegistrations().values())
                .extracting(jakarta.servlet.FilterRegistration::getClassName)
                .doesNotContain(
                        "com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter",
                        "com.co.kc.imchat.management.iam.sdk.security.IamAdministratorBearerFilter");
    }
    @MockitoBean
    private IamIntrospectionClient introspectionClient;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private IamApplicationSessionRepository sessions;
    @MockitoBean
    private IamIntrospectionCache cache;

    @Test
    void validAdministratorBearerReachesCliWithOriginalAdministratorIdentity() throws Exception {
        when(introspectionClient.introspect("cli-token")).thenReturn(new IamIntrospectionResult(
                true, 10L, "root", "imOps", "im-ops-cli", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), Instant.parse("2099-01-01T00:00:00Z"),
                "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer cli-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "audience", "machine", "type-missing", "scope", "expired", "inactive"})
    void rejectsUntrustedBearerBeforeCliExecution(String invalid) throws Exception {
        when(introspectionClient.introspect("invalid-token")).thenReturn(new IamIntrospectionResult(
                !invalid.equals("inactive"), 10L, "root", "imOps",
                invalid.equals("client") ? "another-cli" : "im-ops-cli",
                Set.of(invalid.equals("audience") ? "imAdmin" : "imOps"),
                Set.of("ops:broker:raft:write"),
                Instant.parse(invalid.equals("expired") ? "2000-01-01T00:00:00Z" : "2099-01-01T00:00:00Z"),
                invalid.equals("machine") ? "MACHINE" : invalid.equals("type-missing") ? null : "ADMINISTRATOR",
                Set.of(invalid.equals("scope") ? "another.scope" : "ops.read")));

        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void iamUnavailableFailsClosedWithoutBffFallback() throws Exception {
        when(introspectionClient.introspect("unavailable-token"))
                .thenThrow(new IamIntrospectionException("unavailable", true, null));

        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer unavailable-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cli", "/api/cli/security-probe"})
    void cliNeverAcceptsBffCookieAsIdentity(String path) throws Exception {
        mvc.perform(get(path).cookie(new Cookie("IM_OPS_IAM_SESSION", "session")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic not-a-bearer", "Bearer", "Bearer invalid token"})
    void callerHeadersCannotReplaceValidBearer(String authorization) throws Exception {
        mvc.perform(get("/api/cli/security-probe").header("Authorization", authorization)
                        .header("principal_type", "ADMINISTRATOR")
                        .header("scope", "ops.read").header("authorities", "ops:broker:raft:write"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(introspectionClient, sessions, cache);
    }

    @Test
    void contextPathDoesNotChangeCliAuthenticationBoundary() throws Exception {
        mvc.perform(get("/ops/api/cli/security-probe").contextPath("/ops")
                        .cookie(new Cookie("IM_OPS_IAM_SESSION", "session")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(introspectionClient, sessions, cache);
    }

    @Test
    void bearerDoesNotAuthenticateBrowserApi() throws Exception {
        mvc.perform(get("/api/overview").header("Authorization", "Bearer cli-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":10001")));
    }

    @Test
    void validCookieStillAuthenticatesBffFallbackChain() throws Exception {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(sessions.find("web-session")).thenReturn(Optional.of(new IamApplicationSession(
                "web-session", new IamTokenSet("web-token", expiresAt, "refresh", expiresAt),
                "csrf", Instant.EPOCH, Instant.EPOCH)));
        when(introspectionClient.introspect("web-token")).thenReturn(new IamIntrospectionResult(
                true, 11L, "web-admin", "imOps", "im-ops-client", Set.of("imOps"),
                Set.of("ops:broker:raft:write"), expiresAt, "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(get("/api/security-probe").cookie(new Cookie("IM_OPS_IAM_SESSION", "web-session")))
                .andExpect(status().isOk()).andExpect(content().string("11"));
    }

    @Test
    void cliPostUsesBearerWithoutCsrfWhileBrowserPostRemainsProtected() throws Exception {
        when(introspectionClient.introspect("write-token")).thenReturn(new IamIntrospectionResult(
                true, 10L, "root", "imOps", "im-ops-cli", Set.of("imOps"), Set.of(),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/cli/security-probe").header("Authorization", "Bearer write-token"))
                .andExpect(status().isOk()).andExpect(content().string("10"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/cli/security-probe").cookie(new Cookie("IM_OPS_IAM_SESSION", "session")))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/security-probe").header("Authorization", "Bearer write-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":10002")));
    }

    @Test
    void bffRejectsCookieForDifferentWebClientDespiteMatchingAudience() throws Exception {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(sessions.find("other-client-session")).thenReturn(Optional.of(new IamApplicationSession(
                "other-client-session", new IamTokenSet("other-client-token", expiresAt, "refresh", expiresAt),
                "csrf", Instant.EPOCH, Instant.EPOCH)));
        when(introspectionClient.introspect("other-client-token")).thenReturn(new IamIntrospectionResult(
                true, 11L, "web-admin", "imOps", "another-web-client", Set.of("imOps"),
                Set.of(), expiresAt, "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(get("/api/security-probe")
                        .cookie(new Cookie("IM_OPS_IAM_SESSION", "other-client-session")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":10001")));
    }

    @Test
    void bffWriteStillRequiresSessionBoundCsrf() throws Exception {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(sessions.find("web-session")).thenReturn(Optional.of(new IamApplicationSession(
                "web-session", new IamTokenSet("web-token", expiresAt, "refresh", expiresAt),
                "csrf", Instant.EPOCH, Instant.EPOCH)));
        when(introspectionClient.introspect("web-token")).thenReturn(new IamIntrospectionResult(
                true, 11L, "web-admin", "imOps", "im-ops-client", Set.of("imOps"),
                Set.of(), expiresAt, "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/security-probe").cookie(new Cookie("IM_OPS_IAM_SESSION", "web-session")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":10002")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/security-probe").cookie(new Cookie("IM_OPS_IAM_SESSION", "web-session"))
                        .header("X-XSRF-TOKEN", java.util.Base64.getUrlEncoder().encodeToString(
                                new byte[] {0, 0, 0, 0, 'c', 's', 'r', 'f'})))
                .andExpect(status().isOk()).andExpect(content().string("11"));
    }

    @Test
    void cliRechecksEveryRequestAndObservesRevocationWithoutCache() throws Exception {
        IamIntrospectionResult active = new IamIntrospectionResult(
                true, 10L, "root", "imOps", "im-ops-cli", Set.of("imOps"), Set.of(),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR", Set.of("ops.read"));
        when(introspectionClient.introspect("revoked-token"))
                .thenReturn(active, IamIntrospectionResult.inactive());

        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer revoked-token"))
                .andExpect(status().isOk()).andExpect(content().string("10"));
        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer revoked-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cache, sessions);
    }

    @Test
    void cliRejectsBearerMixedWithBffCookieBeforeIntrospection() throws Exception {
        mvc.perform(get("/api/cli/security-probe").header("Authorization", "Bearer cli-token")
                        .cookie(new Cookie("IM_OPS_IAM_SESSION", "session")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(introspectionClient, sessions, cache);
    }

    @Test
    void authenticatedCliWithoutAuthorityReceivesHttpForbidden() throws Exception {
        when(introspectionClient.introspect("read-token")).thenReturn(new IamIntrospectionResult(
                true, 10L, "root", "imOps", "im-ops-cli", Set.of("imOps"), Set.of(),
                Instant.parse("2099-01-01T00:00:00Z"), "ADMINISTRATOR", Set.of("ops.read")));

        mvc.perform(get("/api/cli/secured-probe").header("Authorization", "Bearer read-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/cli/secured-probe")
        @PreAuthorize("hasAuthority('ops:broker:raft:write')")
        String securedIdentity() {
            return "must-not-execute";
        }

        @org.springframework.web.bind.annotation.PostMapping({"/api/security-probe", "/api/cli/security-probe"})
        String browserWriteIdentity() {
            return IamSecurityContext.currentPrincipal().orElseThrow().administratorId().toString();
        }

        @GetMapping({"/api/cli/security-probe", "/api/security-probe"})
        String identity() {
            return IamSecurityContext.currentPrincipal().orElseThrow().administratorId().toString();
        }
    }
}
