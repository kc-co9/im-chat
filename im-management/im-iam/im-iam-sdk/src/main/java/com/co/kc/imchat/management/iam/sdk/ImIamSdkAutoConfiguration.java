package com.co.kc.imchat.management.iam.sdk;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalogRegistrar;
import com.co.kc.imchat.management.iam.sdk.catalog.IamCatalogHealthIndicator;
import com.co.kc.imchat.management.iam.sdk.interfaces.http.IamBffController;
import com.co.kc.imchat.management.iam.sdk.introspection.HttpIamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.IamAvailabilityCircuit;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionCache;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionService;
import com.co.kc.imchat.management.iam.sdk.introspection.RedisIamIntrospectionCache;
import com.co.kc.imchat.management.iam.sdk.oauth.HttpIamAuthorizationClient;
import com.co.kc.imchat.management.iam.sdk.oauth.IamAuthorizationClient;
import com.co.kc.imchat.management.iam.sdk.oauth.IamAuthorizationRequestRepository;
import com.co.kc.imchat.management.iam.sdk.oauth.IamAuthorizedSessionService;
import com.co.kc.imchat.management.iam.sdk.oauth.RedisIamAuthorizationRequestRepository;
import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamBffRequestPolicy;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.iam.sdk.security.IamSessionCookie;
import com.co.kc.imchat.management.iam.sdk.session.crypto.AesGcmIamSessionCipher;
import com.co.kc.imchat.management.iam.sdk.session.crypto.IamSessionCipher;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import com.co.kc.imchat.management.iam.sdk.session.repository.RedisIamApplicationSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 管理平台 IAM 客户端自动配置入口。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "im.iam", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IamProperties.class)
@EnableMethodSecurity
public class ImIamSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public IamSessionCipher iamSessionCipher(IamProperties properties) {
        return new AesGcmIamSessionCipher(
                properties.application().session().encryptionKeyBytes());
    }

    @Bean
    @ConditionalOnMissingBean(name = "iamRestClient")
    public RestClient iamRestClient(IamProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.http().connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.http().readTimeout());
        return RestClient.builder()
                .baseUrl(properties.issuer().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public IamSessionCookie iamSessionCookie(IamProperties properties) {
        IamProperties.Session session = properties.application().session();
        return new IamSessionCookie(
                session.cookieName(),
                session.secureCookieEnabled(),
                session.sameSite(),
                session.ttl());
    }

    @Bean
    public IamApplicationSessionRepository iamApplicationSessionRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            IamSessionCipher cipher,
            IamProperties properties
    ) {
        IamProperties.Application application = properties.application();
        return new RedisIamApplicationSessionRepository(
                redisTemplate,
                objectMapper,
                cipher,
                application.key(),
                application.session().ttl());
    }

    @Bean
    public IamAuthorizationRequestRepository iamAuthorizationRequestRepository(
            StringRedisTemplate redisTemplate,
            IamSessionCipher cipher,
            IamProperties properties
    ) {
        IamProperties.Application application = properties.application();
        return new RedisIamAuthorizationRequestRepository(
                redisTemplate, application.key(), cipher);
    }

    @Bean
    public IamAuthorizationClient iamAuthorizationClient(
            @Qualifier("iamRestClient") RestClient iamRestClient,
            IamProperties properties
    ) {
        IamProperties.WebClient webClient = properties.application().clients().web();
        return new HttpIamAuthorizationClient(
                iamRestClient,
                properties.issuer(),
                webClient.clientId(),
                webClient.clientSecret(),
                webClient.redirectUri(),
                webClient.postLogoutRedirectUri(),
                properties.application().session().ttl());
    }

    @Bean
    public IamAuthorizedSessionService iamAuthorizedSessionService(
            IamAuthorizationClient authorizationClient,
            IamAuthorizationRequestRepository authorizationRequestRepository,
            IamApplicationSessionRepository sessionRepository
    ) {
        return new IamAuthorizedSessionService(
                authorizationClient, authorizationRequestRepository, sessionRepository);
    }

    @Bean
    public IamIntrospectionClient iamIntrospectionClient(
            @Qualifier("iamRestClient") RestClient iamRestClient,
            IamProperties properties
    ) {
        IamProperties.WebClient webClient = properties.application().clients().web();
        return new HttpIamIntrospectionClient(
                iamRestClient, webClient.clientId(), webClient.clientSecret());
    }

    @Bean
    public IamIntrospectionCache iamIntrospectionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            IamProperties properties
    ) {
        return new RedisIamIntrospectionCache(
                redisTemplate, objectMapper, properties.introspection().staleTtl());
    }

    @Bean
    public IamAvailabilityCircuit iamAvailabilityCircuit() {
        return new IamAvailabilityCircuit();
    }

    @Bean
    public IamIntrospectionService iamIntrospectionService(
            IamIntrospectionClient introspectionClient,
            IamIntrospectionCache introspectionCache,
            IamAvailabilityCircuit circuit,
            IamProperties properties
    ) {
        IamProperties.Application application = properties.application();
        return new IamIntrospectionService(
                introspectionClient,
                introspectionCache,
                circuit,
                application.key(),
                application.clients().web().clientId(),
                properties.introspection().staleTtl());
    }

    @Bean
    public IamCsrfTokenRepository iamCsrfTokenRepository(
            IamApplicationSessionRepository sessionRepository,
            IamSessionCookie sessionCookie,
            IamProperties properties
    ) {
        IamProperties.Session session = properties.application().session();
        return new IamCsrfTokenRepository(
                sessionRepository,
                sessionCookie,
                session.secureCookieEnabled(),
                session.sameSite(),
                session.ttl());
    }

    @Bean
    public IamSecurityFilter iamSecurityFilter(
            IamSessionCookie sessionCookie,
            IamApplicationSessionRepository sessionRepository,
            IamIntrospectionService introspectionService
    ) {
        return new IamSecurityFilter(
                sessionCookie, sessionRepository, introspectionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public IamSecurityExceptionHandler iamSecurityExceptionHandler() {
        return new IamSecurityExceptionHandler();
    }

    @Bean
    public IamBffController iamBffController(
            IamAuthorizedSessionService sessionService,
            IamSessionCookie sessionCookie,
            IamCsrfTokenRepository csrfTokenRepository
    ) {
        return new IamBffController(sessionService, sessionCookie, csrfTokenRepository);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain iamSecurityFilterChain(
            HttpSecurity http,
            IamSecurityFilter securityFilter,
            IamCsrfTokenRepository csrfTokenRepository,
            IamSecurityExceptionHandler securityExceptionHandler
    ) throws Exception {
        http.sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(IamBffRequestPolicy.publicPathPatterns())
                        .permitAll()
                        .requestMatchers(IamBffRequestPolicy.authenticatedPathPatterns())
                        .authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(securityFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @ConditionalOnBean(IamPermissionCatalog.class)
    public IamCatalogHealthIndicator iamCatalogHealthIndicator() {
        return new IamCatalogHealthIndicator();
    }

    @Bean
    @ConditionalOnBean(IamPermissionCatalog.class)
    public IamPermissionCatalogRegistrar iamPermissionCatalogRegistrar(
            @Qualifier("iamRestClient") RestClient iamRestClient,
            IamProperties properties,
            IamPermissionCatalog catalog,
            IamCatalogHealthIndicator healthIndicator
    ) {
        return new IamPermissionCatalogRegistrar(
                iamRestClient,
                properties.application().clients().catalog(),
                catalog,
                healthIndicator);
    }
}
