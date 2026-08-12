package com.co.kc.imchat.gateway.http.security.config;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.model.io.HttpResult;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.http.security.authentication.AuthenticationConverter;
import com.co.kc.imchat.gateway.http.security.authentication.AuthenticationManager;
import com.co.kc.imchat.gateway.http.security.authentication.AuthenticationSuccessHandler;
import com.co.kc.imchat.gateway.http.security.filter.UserContextHeaderSanitizingFilter;
import com.co.kc.imchat.plugin.session.token.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * HTTP 网关安全配置。
 * <p>
 * 使用 Spring Security 标准认证链路完成 token 解析、认证和用户上下文透传。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveAuthenticationManager authenticationManager(List<TokenService> tokenServices) {
        return new AuthenticationManager(tokenServices);
    }

    @Bean
    public ServerAuthenticationConverter authenticationConverter() {
        return new AuthenticationConverter();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new AuthenticationSuccessHandler();
    }

    @Bean
    public UserContextHeaderSanitizingFilter userContextHeaderSanitizingFilter() {
        return new UserContextHeaderSanitizingFilter();
    }

    @Bean
    public AuthenticationWebFilter authenticationWebFilter(
            ReactiveAuthenticationManager authenticationManager,
            ServerAuthenticationConverter authenticationConverter,
            AuthenticationSuccessHandler successHandler,
            ServerAuthenticationEntryPoint authenticationEntryPoint) {
        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(authenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(authenticationConverter);
        authenticationWebFilter.setAuthenticationSuccessHandler(successHandler);
        authenticationWebFilter.setAuthenticationFailureHandler(
                new ServerAuthenticationEntryPointFailureHandler(authenticationEntryPoint));
        return authenticationWebFilter;
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> Mono.empty();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            UserContextHeaderSanitizingFilter headerSanitizingFilter,
            AuthenticationWebFilter authenticationWebFilter,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(headers -> headers
                        .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable)
                        .frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .addFilterAt(headerSanitizingFilter, SecurityWebFiltersOrder.FIRST)
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(
                                "/favicon.ico",
                                "/doc.html",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/v3/api-docs/swagger-config",
                                "/v3/api-docs/**",
                                "/*/v3/api-docs/**",
                                "/actuator/**").permitAll()
                        .pathMatchers("/user/signUp", "/user/signIn").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> writeError(exchange, HttpErrorCode.AUTH_FAIL);
    }

    @Bean
    public ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, ex) -> writeError(exchange, HttpErrorCode.AUTH_DENY);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpErrorCode errorCode) {
        byte[] body = JsonUtils.toJson(HttpResult.error(errorCode)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
