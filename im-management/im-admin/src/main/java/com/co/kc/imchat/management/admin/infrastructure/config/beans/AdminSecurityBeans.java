package com.co.kc.imchat.management.admin.infrastructure.config.beans;

import com.co.kc.imchat.management.admin.support.security.AdminPermission;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionDefinition;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamBffRequestPolicy;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

/** Admin 的 IAM 安全链与权限目录 Bean。 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class AdminSecurityBeans {

    @Bean
    public IamPermissionCatalog adminPermissionCatalog() {
        return new AdminPermissionCatalog();
    }

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(
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

    /** Admin 向 IAM 注册的权限目录全量快照。 */
    private static class AdminPermissionCatalog implements IamPermissionCatalog {

        @Override
        public List<IamPermissionDefinition> permissions() {
            return Arrays.stream(AdminPermission.values())
                    .map(permission -> new IamPermissionDefinition(
                            permission.getCode(),
                            permission.getDisplayName(),
                            permission.getDescription()))
                    .toList();
        }
    }
}
