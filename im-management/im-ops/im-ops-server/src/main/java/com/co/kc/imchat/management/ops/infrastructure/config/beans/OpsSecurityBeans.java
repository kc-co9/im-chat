package com.co.kc.imchat.management.ops.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionDefinition;
import com.co.kc.imchat.management.ops.domain.authorization.model.OpsPermission;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import com.co.kc.imchat.management.iam.sdk.security.IamAdministratorBearerAuthenticator;
import com.co.kc.imchat.management.iam.sdk.security.IamAdministratorBearerFilter;
import com.co.kc.imchat.management.iam.sdk.security.IamBffSecurityConfigurer;
import com.co.kc.imchat.management.iam.sdk.security.IamSessionCookie;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsCliProperties;
import com.co.kc.imchat.management.ops.support.audit.OpsCliAuditContextCollector;
import com.co.kc.imchat.management.ops.support.audit.OpsCliAuditContextFilter;
import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.plugin.web.support.HttpResultWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Arrays;
import java.util.List;

/**
 * Ops IAM 权限目录与方法级授权配置。
 */
@Configuration
@EnableMethodSecurity
public class OpsSecurityBeans {

    @Bean
    public IamPermissionCatalog opsPermissionCatalog() {
        return new OpsPermissionCatalog();
    }

    @Configuration(proxyBeanMethods = false)
    public static class CliSecurityConfiguration {
        @Bean
        IamAdministratorBearerFilter opsCliBearerFilter(
                IamIntrospectionClient client,
                IamProperties iam,
                OpsCliProperties cli,
                IamSessionCookie cookie
        ) {
            return new IamAdministratorBearerFilter(
                    new IamAdministratorBearerAuthenticator(
                            client, iam.application().key(), cli.clientIds(), cli.scopes()), cookie);
        }

        @Bean
        FilterRegistrationBean<IamAdministratorBearerFilter> opsCliFilterRegistration(
                IamAdministratorBearerFilter filter
        ) {
            FilterRegistrationBean<IamAdministratorBearerFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }

        @Bean
        OpsCliAuditContextFilter opsCliAuditContextFilter(
                OpsCliAuditContextCollector collector
        ) {
            return new OpsCliAuditContextFilter(collector);
        }

        @Bean
        FilterRegistrationBean<OpsCliAuditContextFilter> opsCliAuditContextFilterRegistration(
                OpsCliAuditContextFilter filter
        ) {
            FilterRegistrationBean<OpsCliAuditContextFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }

        @Bean
        @Order(1)
        SecurityFilterChain opsCliSecurityFilterChain(
                HttpSecurity http,
                IamAdministratorBearerFilter filter,
                OpsCliAuditContextFilter auditContextFilter
        ) throws Exception {
            http.securityMatcher("/api/cli", "/api/cli/**")
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                    .addFilterAfter(auditContextFilter, AnonymousAuthenticationFilter.class)
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) -> {
                                HttpResultWriter.write(response, HttpErrorCode.AUTH_FAIL);
                                response.setStatus(401);
                            })
                            .accessDeniedHandler((request, response, exception) -> {
                                HttpResultWriter.write(response, HttpErrorCode.AUTH_DENY);
                                response.setStatus(403);
                            }));
            return http.build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain opsBffSecurityFilterChain(
                HttpSecurity http,
                IamBffSecurityConfigurer configurer
        ) throws Exception {
            return configurer.build(http);
        }
    }

    private static class OpsPermissionCatalog implements IamPermissionCatalog {
        @Override
        public List<IamPermissionDefinition> permissions() {
            return Arrays.stream(OpsPermission.values())
                    .map(permission -> new IamPermissionDefinition(
                            permission.getCode(),
                            permission.getDisplayName(),
                            permission.getDescription()))
                    .toList();
        }
    }
}
