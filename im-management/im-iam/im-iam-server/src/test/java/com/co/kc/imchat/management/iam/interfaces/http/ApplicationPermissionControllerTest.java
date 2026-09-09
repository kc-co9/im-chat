package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.application.ApplicationPermissionAppService;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationPermissionPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.APPLICATION_READ;
import static com.co.kc.imchat.management.iam.support.security.IamPermission.Code.PERMISSION_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ApplicationPermissionControllerTest.Config.class)
class ApplicationPermissionControllerTest {
    @jakarta.annotation.Resource
    private ApplicationPermissionController controller;

    @jakarta.annotation.Resource
    private ApplicationPermissionAppService appService;

    @Test
    void missingAuthenticationRejectsPermissionPage() {
        assertThatThrownBy(() -> controller.permissions(1001L, "user", 1, 20))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(authorities = APPLICATION_READ)
    void wrongAuthorityRejectsPermissionPage() {
        assertThatThrownBy(() -> controller.permissions(1001L, "user", 1, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = PERMISSION_READ)
    void permissionReadAuthorityAllowsExplicitKeyword() {
        ApplicationPermissionPageQuery query = new ApplicationPermissionPageQuery(
                1001L,
                "user",
                new Paging(2, 10));
        when(appService.page(query))
                .thenReturn(new PagingResult<>(query.paging(), List.of(), 0L));

        assertThat(controller.permissions(1001L, "user", 2, 10).records()).isEmpty();

        verify(appService).page(query);
    }

    @Test
    @WithMockUser(authorities = PERMISSION_READ)
    void omittedKeywordRemainsBackwardCompatible() {
        ApplicationPermissionPageQuery query = new ApplicationPermissionPageQuery(
                1001L,
                null,
                new Paging(1, 20));
        when(appService.page(query))
                .thenReturn(new PagingResult<>(query.paging(), List.of(), 0L));

        assertThat(controller.permissions(1001L, null, 1, 20).records()).isEmpty();

        verify(appService).page(query);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(ApplicationPermissionController.class)
    static class Config {
        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        ApplicationPermissionAppService applicationPermissionAppService() {
            return mock(ApplicationPermissionAppService.class);
        }
    }
}
