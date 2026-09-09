package com.co.kc.imchat.management.audit.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamBffRequestPolicy;
import com.co.kc.imchat.management.iam.sdk.security.IamOpaqueTokenAuthenticationConverter;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/** Audit HTTP API 的 IAM 安全 Bean。 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class AuditSecurityBeans {

    @Bean
    public OpaqueTokenIntrospector auditOpaqueTokenIntrospector(
            IamProperties properties
    ) {
        IamProperties.WebClient webClient = properties.application().clients().web();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.http().connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.http().readTimeout());
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(
                encodeClientCredential(webClient.clientId()),
                encodeClientCredential(webClient.clientSecret())));
        String introspectionUri = properties.issuer()
                .resolve("/oauth2/introspect")
                .toString();
        return new SpringOpaqueTokenIntrospector(introspectionUri, restTemplate);
    }

    private String encodeClientCredential(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain auditIngestionSecurityFilterChain(
            HttpSecurity http,
            IamProperties iamProperties
    ) throws Exception {
        http.securityMatcher("/internal/**")
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("SCOPE_audit:ingest"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .opaqueToken(opaque -> opaque
                                .authenticationConverter(
                                        new IamOpaqueTokenAuthenticationConverter(
                                                iamProperties.application().key()))));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain auditBrowserSecurityFilterChain(
            HttpSecurity http,
            IamSecurityFilter securityFilter,
            IamCsrfTokenRepository csrfTokenRepository,
            IamSecurityExceptionHandler securityExceptionHandler
    ) throws Exception {
        return http
                .sessionManagement(session -> session
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
                .build();
    }
}
