package com.co.kc.imchat.management.iam.sdk.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 默认应用与显式双链应用共用的 BFF 安全配置，保持 Session、CSRF 与原错误策略。
 */
public class IamBffSecurityConfigurer {
    /** BFF 原有身份过滤器。 */
    private final IamSecurityFilter securityFilter;
    /** BFF CSRF Token 仓储。 */
    private final IamCsrfTokenRepository csrfTokenRepository;
    /** 统一认证失败和拒绝访问处理器。 */
    private final IamSecurityExceptionHandler exceptionHandler;

    /** 注入 BFF 原有安全组件，保持配置器本身不承载认证业务。 */
    public IamBffSecurityConfigurer(
            IamSecurityFilter securityFilter,
            IamCsrfTokenRepository csrfTokenRepository,
            IamSecurityExceptionHandler exceptionHandler
    ) {
        this.securityFilter = securityFilter;
        this.csrfTokenRepository = csrfTokenRepository;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * 在宿主指定的安全链中装配原 BFF 策略，链顺序及 matcher 由宿主负责。
     */
    public SecurityFilterChain build(HttpSecurity http) throws Exception {
        // 步骤一：固定无状态会话、CSRF 和公开/认证路径策略。
        http.sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(IamBffRequestPolicy.publicPathPatterns()).permitAll()
                        .requestMatchers(IamBffRequestPolicy.authenticatedPathPatterns()).authenticated()
                        .anyRequest().denyAll())
                // 步骤二：先建立项目统一的 BFF 身份，再交给 Spring 授权处理。
                .addFilterBefore(securityFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(exceptionHandler)
                        .accessDeniedHandler(exceptionHandler))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
