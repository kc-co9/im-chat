package com.co.kc.imchat.management.iam.interfaces.security;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthRegisteredClientTransformer;
import com.co.kc.imchat.management.iam.infrastructure.config.beans.IamSecurityBeans;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import com.co.kc.imchat.management.iam.infrastructure.security.session.IamSsoSessionFilter;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAdministratorTokenClaimsDTO;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.mock.web.MockHttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.co.kc.imchat.plugin.identity.ImIdentityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IamPublicClientProtocolTest.TestApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.datasource.sharding.enabled=false"
})
@AutoConfigureMockMvc
class IamPublicClientProtocolTest {
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    private static final String CLIENT_ID = "browser-client";
    private static final String CLIENT_SECRET = "browser-secret";
    private static final String REDIRECT_URI = "http://127.0.0.1:8765/oauth/callback";
    private static final String VERIFIER = "0123456789012345678901234567890123456789012345678901234567890123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsDifferentLoopbackPortExactly() throws Exception {
        mockMvc.perform(get(authorizationUri("http://127.0.0.1:8766/oauth/callback")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPlainPkce() throws Exception {
        mockMvc.perform(get(authorizationUri(REDIRECT_URI).replace("code_challenge_method=S256", "code_challenge_method=plain")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?error=invalid_request*"));
    }

    @Test
    void issuesOpaqueFiveMinuteTokenWithoutRefreshAndConsumesCodeOnce() throws Exception {
        String code = authorizationCode();
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .param("client_id", CLIENT_ID)
                        .param("grant_type", "authorization_code")
                        .param("code", code).param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn();
        assertThat(((Number) json(token).get("expires_in")).longValue()).isBetween(295L, 300L);
        assertThat(OAuthRegisteredClientTransformer.INSTANCE.registeredClientFrom(
                new OAuthClientRegistrationDTO(CLIENT_ID, OAuthClientType.PUBLIC, null, "Ops CLI",
                        Set.of(OAuthGrantType.AUTHORIZATION_CODE), Set.of("ops:broker:read"),
                        Set.of(REDIRECT_URI), Set.of())).getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(((String) json(token).get("access_token")).split("\\.")).hasSize(1);
        mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicClientRevokesItsAccessToken() throws Exception {
        String code = authorizationCode();
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .param("client_id", CLIENT_ID).param("grant_type", "authorization_code")
                        .param("code", code).param("redirect_uri", REDIRECT_URI).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andReturn();
        String accessToken = (String) json(token).get("access_token");
        mockMvc.perform(post("/oauth2/introspect").with(httpBasic("introspection-client", CLIENT_SECRET))
                        .param("token", accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.client_id").value(CLIENT_ID))
                .andExpect(jsonPath("$.sub").value("7"))
                .andExpect(jsonPath("$.scope").value("ops:broker:read"))
                .andExpect(jsonPath("$.exp").isNumber())
                .andExpect(jsonPath("$.aud[0]").value("imOps"))
                .andExpect(jsonPath("$.principal_type").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.authorities[0]").value("ops:broker:read"));
        mockMvc.perform(post("/oauth2/revoke").param("client_id", CLIENT_ID).param("token", accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/oauth2/introspect").with(httpBasic("introspection-client", CLIENT_SECRET))
                        .param("token", accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void rejectsMissingChallengeAndVerifierAndForbiddenGrants() throws Exception {
        mockMvc.perform(get(authorizationUri(REDIRECT_URI).replace("code_challenge=", "ignored_challenge=")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?error=invalid_request*"));
        String code = authorizationCode();
        mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "refresh_token").param("refresh_token", "unknown"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingChallengeMethodAndWrongVerifier() throws Exception {
        mockMvc.perform(get(authorizationUri(REDIRECT_URI).replace("&code_challenge_method=S256", "")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?error=invalid_request*"));
        String code = authorizationCode();
        mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", VERIFIER + "wrong"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void unknownRevocationIsIdempotentAndOwnershipIsEnforced() throws Exception {
        mockMvc.perform(post("/oauth2/revoke").param("client_id", CLIENT_ID).param("token", "unknown"))
                .andExpect(status().isOk());
        String code = authorizationCode();
        MvcResult token = mockMvc.perform(post("/oauth2/token").accept(MediaType.APPLICATION_JSON).param("client_id", CLIENT_ID)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT_URI).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(post("/oauth2/revoke").param("client_id", "other-public")
                        .param("token", (String) json(token).get("access_token")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_client"));
    }

    private String authorizationCode() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "administrator").param("password", "password").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        MvcResult result = mockMvc.perform(get(authorizationUri(REDIRECT_URI))
                        .session((MockHttpSession) login.getRequest().getSession()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String code = query(result.getResponse().getRedirectedUrl()).getFirst("code");
        assertThat(code).isNotBlank();
        return code;
    }

    private String authorizationUri(String redirectUri) throws Exception {
        return authorizationUri(CLIENT_ID, redirectUri);
    }

    private String authorizationUri(String clientId, String redirectUri) throws Exception {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "ops:broker:read")
                .queryParam("state", "state-value")
                .queryParam("code_challenge", challenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    private String challenge() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private MultiValueMap<String, String> query(String redirect) {
        return UriComponentsBuilder.fromUri(URI.create(redirect)).build().getQueryParams();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(MvcResult result) throws Exception {
        return new ObjectMapper().readValue(
                result.getResponse().getContentAsByteArray(),
                Map.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            RedissonAutoConfigurationV2.class,
            ImIdentityAutoConfiguration.class
    })
    @Import(SecurityConfiguration.class)
    static class TestApplication {
    }

    @Configuration
    static class SecurityConfiguration {
        @Bean
        RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
            RegisteredClient client = OAuthRegisteredClientTransformer.INSTANCE
                    .registeredClientFrom(new OAuthClientRegistrationDTO(
                            CLIENT_ID, OAuthClientType.PUBLIC,
                            null, "Ops CLI", Set.of(OAuthGrantType.AUTHORIZATION_CODE),
                            Set.of("ops:broker:read"), Set.of(REDIRECT_URI), Set.of()));
            RegisteredClient other = RegisteredClient.from(client).id("other-public").clientId("other-public").build();
            RegisteredClient resource = RegisteredClient.withId("introspection-client")
                    .clientId("introspection-client").clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("ops:broker:read").build();
            return new InMemoryRegisteredClientRepository(client, other, resource);
        }

        @Bean
        OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
            OAuthClientAppService clients = mock(OAuthClientAppService.class);
            when(clients.getAdministratorTokenClaims(any())).thenReturn(new OAuthAdministratorTokenClaimsDTO(
                    "7", "administrator", "imOps", "imOps", List.of("ops:broker:read")));
            return new IamSecurityBeans().tokenGenerator(jwkSource, clients);
        }

        @Bean
        OAuth2AuthorizationService authorizationService() {
            return new InMemoryOAuth2AuthorizationService();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            return username -> User.withUsername("7")
                    .password(passwordEncoder.encode("password"))
                    .roles("IAM_USER")
                    .build();
        }

        @Bean
        JWKSource<SecurityContext> jwkSource() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
            JWKSet jwkSet = new JWKSet(rsaKey);
            return (selector, context) -> selector.select(jwkSet);
        }

        @Bean
        AuthorizationServerSettings authorizationServerSettings() {
            return AuthorizationServerSettings.builder().build();
        }

        @Bean
        @Order(1)
        SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                RegisteredClientRepository clientRepository, AuthorizationServerSettings settings) throws Exception {
            IamAuthorizationProperties properties = new IamAuthorizationProperties(URI.create("http://127.0.0.1"),
                    "classpath:unused-test-keystore", "unused", "unused", "unused", null, null);
            return new IamSecurityBeans().oauthSecurityFilterChain(http,
                    new IamSsoSessionFilter(properties), clientRepository, settings);
        }

        @Bean
        @Order(2)
        SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .formLogin(Customizer.withDefaults());
            return http.build();
        }
    }
}
