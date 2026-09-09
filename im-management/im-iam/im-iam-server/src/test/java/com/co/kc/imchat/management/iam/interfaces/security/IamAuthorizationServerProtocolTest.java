package com.co.kc.imchat.management.iam.interfaces.security;

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
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IamAuthorizationServerProtocolTest.TestApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class IamAuthorizationServerProtocolTest {
    private static final String CLIENT_ID = "browser-client";
    private static final String CLIENT_SECRET = "browser-secret";
    private static final String REDIRECT_URI = "https://client.example/callback";
    private static final String SECOND_CLIENT_ID = "monitor-client";
    private static final String SECOND_REDIRECT_URI = "https://monitor.example/callback";
    private static final String VERIFIER = "0123456789012345678901234567890123456789012345678901234567890123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOidcDiscoveryAndJwkEndpoints() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists())
                .andExpect(jsonPath("$.jwks_uri").exists());

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").exists());
    }

    @Test
    void completesPkceCodeFlowWithOpaqueTokenSingleUseAndRefreshRotation() throws Exception {
        String authorizationUri = authorizationUri(REDIRECT_URI);
        MvcResult authorizationRequest = mockMvc.perform(get(authorizationUri)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"))
                .andReturn();

        MvcResult login = mockMvc.perform(post("/login")
                        .session((org.springframework.mock.web.MockHttpSession)
                                authorizationRequest.getRequest().getSession())
                        .param("username", "administrator")
                        .param("password", "password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MvcResult authorized = mockMvc.perform(get(authorizationUri)
                        .accept(MediaType.TEXT_HTML)
                        .session((org.springframework.mock.web.MockHttpSession)
                                login.getRequest().getSession()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = query(authorized.getResponse().getRedirectedUrl()).getFirst("code");
        assertThat(code).isNotBlank();

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.refresh_token").isString())
                .andReturn();
        Map<String, Object> tokenBody = json(token);
        String accessToken = (String) tokenBody.get("access_token");
        String refreshToken = (String) tokenBody.get("refresh_token");
        assertThat(accessToken.split("\\.")).hasSize(1);

        MvcResult refreshed = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.REFRESH_TOKEN.getValue())
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").isString())
                .andReturn();
        assertThat(json(refreshed).get("refresh_token")).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.REFRESH_TOKEN.getValue())
                        .param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void rejectsUnregisteredRedirectUri() throws Exception {
        mockMvc.perform(get(authorizationUri("https://evil.example/callback")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reusesIamLoginAcrossApplicationsWithoutSharingRedirectUris() throws Exception {
        MvcResult authorizationRequest = mockMvc.perform(get(authorizationUri(REDIRECT_URI))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MvcResult login = mockMvc.perform(post("/login")
                        .session((org.springframework.mock.web.MockHttpSession)
                                authorizationRequest.getRequest().getSession())
                        .param("username", "administrator")
                        .param("password", "password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String secondAuthorizationUri = authorizationUri(
                SECOND_CLIENT_ID,
                SECOND_REDIRECT_URI);
        MvcResult secondAuthorization = mockMvc.perform(get(secondAuthorizationUri)
                        .accept(MediaType.TEXT_HTML)
                        .session((org.springframework.mock.web.MockHttpSession)
                                login.getRequest().getSession()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(secondAuthorization.getResponse().getRedirectedUrl())
                .startsWith(SECOND_REDIRECT_URI)
                .contains("code=");
        mockMvc.perform(get(authorizationUri(SECOND_CLIENT_ID, REDIRECT_URI))
                        .accept(MediaType.TEXT_HTML)
                        .session((org.springframework.mock.web.MockHttpSession)
                                login.getRequest().getSession()))
                .andExpect(status().isBadRequest());
    }

    private String authorizationUri(String redirectUri) throws Exception {
        return authorizationUri(CLIENT_ID, redirectUri);
    }

    private String authorizationUri(String clientId, String redirectUri) throws Exception {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", OidcScopes.OPENID)
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
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
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
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(CLIENT_ID)
                    .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri(REDIRECT_URI)
                    .scope(OidcScopes.OPENID)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenFormat(org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat.REFERENCE)
                            .accessTokenTimeToLive(Duration.ofMinutes(15))
                            .refreshTokenTimeToLive(Duration.ofHours(8))
                            .reuseRefreshTokens(false)
                            .build())
                    .build();
            RegisteredClient secondClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(SECOND_CLIENT_ID)
                    .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri(SECOND_REDIRECT_URI)
                    .scope(OidcScopes.OPENID)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(false)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenFormat(org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat.REFERENCE)
                            .accessTokenTimeToLive(Duration.ofMinutes(15))
                            .refreshTokenTimeToLive(Duration.ofHours(8))
                            .reuseRefreshTokens(false)
                            .build())
                    .build();
            return new InMemoryRegisteredClientRepository(client, secondClient);
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
            return username -> User.withUsername("administrator")
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
        @Order(1)
        SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
                throws Exception {
            OAuth2AuthorizationServerConfigurer authorizationServer =
                    OAuth2AuthorizationServerConfigurer.authorizationServer();
            http.securityMatcher(authorizationServer.getEndpointsMatcher())
                    .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                            new LoginUrlAuthenticationEntryPoint("/login"),
                            new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
            return http.build();
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
