package com.co.kc.imchat.management.iam.sdk.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiresPermissionTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizesUsingPermissionAnnotationValue() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(MethodSecurityConfiguration.class)) {
            SecuredOperation operation = context.getBean(SecuredOperation.class);

            authenticate("user:read");

            assertThat(operation.read()).isEqualTo("allowed");
        }
    }

    @Test
    void deniesWhenPermissionIsMissing() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(MethodSecurityConfiguration.class)) {
            SecuredOperation operation = context.getBean(SecuredOperation.class);

            authenticate("user:update");

            assertThatThrownBy(operation::read)
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "", authority));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {

        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        SecuredOperation securedOperation() {
            return new SecuredOperation();
        }
    }

    static class SecuredOperation {

        @RequiresPermission("user:read")
        String read() {
            return "allowed";
        }
    }
}
