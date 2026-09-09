package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.application.AdministratorAppService;
import com.co.kc.imchat.management.iam.application.ApplicationAppService;
import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.application.ApplicationRoleAppService;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.ADMINISTRATOR_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringJUnitConfig(IamManagementAuthorizationTest.Config.class)
class IamManagementAuthorizationTest {
    @jakarta.annotation.Resource
    private AdministratorController controller;

    @jakarta.annotation.Resource
    private AdministratorAppService administratorAppService;

    @Test
    @WithMockUser(authorities = "IAM_USER")
    void authenticatedAdministratorWithoutManagementPermissionIsDenied() {
        assertThatThrownBy(() -> controller.administrators(1, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = ADMINISTRATOR_READ)
    void administratorReadPermissionAllowsAdministratorQuery() {
        when(administratorAppService.page(any())).thenReturn(
                new PagingResult<>(new Paging(1, 20), List.of(), 0L));

        assertThat(controller.administrators(1, 20).records()).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(AdministratorController.class)
    static class Config {
        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        AdministratorAppService administratorAppService() {
            return mock(AdministratorAppService.class);
        }

        @Bean
        ApplicationAppService applicationAppService() {
            return mock(ApplicationAppService.class);
        }

        @Bean
        ApplicationRoleAppService roleAppService() {
            return mock(ApplicationRoleAppService.class);
        }

        @Bean
        ApplicationPermissionAppService permissionAppService() {
            return mock(ApplicationPermissionAppService.class);
        }

        @Bean
        OAuthClientAppService oauthClientAppService() {
            return mock(OAuthClientAppService.class);
        }
    }
}
