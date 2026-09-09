package com.co.kc.imchat.management.iam.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter.OAuthAuthorizationServiceAdapter;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import com.co.kc.imchat.management.iam.infrastructure.security.token.OAuthJwkSource;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.introspector.IamOpaqueTokenIntrospector;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.repository.OAuthRegisteredClientRepository;
import com.co.kc.imchat.management.iam.infrastructure.security.token.OAuthTokenClaimsCustomizer;
import com.co.kc.imchat.management.iam.infrastructure.security.authentication.AdministratorAuthenticationProvider;
import com.co.kc.imchat.management.iam.application.AdministratorAuthenticationAppService;
import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.infrastructure.security.session.IamSsoSessionFilter;
import com.co.kc.imchat.management.iam.infrastructure.security.web.IamCsrfCookieFilter;
import com.co.kc.imchat.management.iam.infrastructure.security.web.IamLoginRedirectHandler;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * IAM OAuth2/OIDC、管理员认证与访问安全相关 Bean。
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class IamSecurityBeans {

    @Bean
    public static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            OAuthClientAppService oauthClientAppService
    ) {
        return new OAuthRegisteredClientRepository(oauthClientAppService);
    }

    @Bean
    public Sha256OAuthTokenDigester tokenDigester() {
        return new Sha256OAuthTokenDigester();
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            OAuthAuthorizationAppService appService,
            RegisteredClientRepository registeredClientRepository,
            Sha256OAuthTokenDigester tokenDigester
    ) {
        Clock clock = Clock.systemUTC();
        OAuthAuthorizationTransformer transformer = new OAuthAuthorizationTransformer(
                tokenDigester,
                clock);
        return new OAuthAuthorizationServiceAdapter(
                appService,
                registeredClientRepository,
                transformer,
                tokenDigester);
    }

    @Bean
    public AdministratorAuthenticationProvider administratorAuthenticationProvider(
            AdministratorAuthenticationAppService administratorAuthenticationAppService
    ) {
        return new AdministratorAuthenticationProvider(administratorAuthenticationAppService);
    }

    @Bean
    public PasswordEncoder oauthClientSecretPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CookieCsrfTokenRepository iamWebCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    public CsrfTokenRequestHandler iamWebCsrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    @Bean
    public WebMvcConfigurer iamLoginPageWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/login")
                        .setViewName("forward:/index.html");
            }
        };
    }

    @Bean
    public IamCsrfCookieFilter iamCsrfCookieFilter() {
        return new IamCsrfCookieFilter();
    }

    @Bean
    public IamLoginRedirectHandler iamLoginRedirectHandler() {
        return new IamLoginRedirectHandler();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            IamAuthorizationProperties properties,
            ResourceLoader resourceLoader
    ) {
        return new OAuthJwkSource(properties, resourceLoader);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuthClientAppService oauthClientAppService
    ) {
        OAuthTokenClaimsCustomizer claimsCustomizer = new OAuthTokenClaimsCustomizer(
                oauthClientAppService);
        JwtGenerator idTokenGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        idTokenGenerator.setJwtCustomizer(claimsCustomizer::customizeIdToken);

        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        accessTokenGenerator.setAccessTokenCustomizer(claimsCustomizer::customizeAccessToken);

        return new DelegatingOAuth2TokenGenerator(
                idTokenGenerator,
                accessTokenGenerator,
                new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            IamAuthorizationProperties properties
    ) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.issuer().toString())
                .oidcLogoutEndpoint("/connect/logout")
                .build();
    }

    @Bean
    public IamSsoSessionFilter iamSsoSessionFilter(IamAuthorizationProperties properties) {
        return new IamSsoSessionFilter(properties);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauthSecurityFilterChain(
            HttpSecurity http,
            IamSsoSessionFilter ssoSessionFilter
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(ssoSessionFilter, SecurityContextHolderFilter.class)
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain iamClientApiSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.securityMatcher("/api/iam/permission-catalog/**")
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .opaqueToken(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain iamWebSecurityFilterChain(
            HttpSecurity http,
            IamSsoSessionFilter ssoSessionFilter,
            AdministratorAuthenticationProvider authenticationProvider,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfTokenRequestHandler csrfTokenRequestHandler,
            IamCsrfCookieFilter csrfCookieFilter,
            IamLoginRedirectHandler loginRedirectHandler
    ) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/error",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(ssoSessionFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginRedirectHandler)
                        .failureHandler(loginRedirectHandler)
                        .permitAll());
        return http.build();
    }

    @Bean
    public IamOpaqueTokenIntrospector iamOpaqueTokenIntrospector(
            OAuth2AuthorizationService authorizationService
    ) {
        return new IamOpaqueTokenIntrospector(authorizationService);
    }
}
