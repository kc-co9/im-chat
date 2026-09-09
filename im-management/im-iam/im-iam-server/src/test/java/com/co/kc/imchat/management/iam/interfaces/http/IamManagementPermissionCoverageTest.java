package com.co.kc.imchat.management.iam.interfaces.http;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamManagementPermissionCoverageTest {

    @Test
    void everyManagementEndpointDeclaresASpecificPermission() {
        List<Method> endpoints = List.of(
                        AdministratorController.class,
                        ApplicationController.class,
                        OAuthClientController.class,
                        ApplicationRoleController.class,
                        ApplicationPermissionController.class,
                        OAuthSessionController.class,
                        ApplicationPermissionCatalogController.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(
                        method, RequestMapping.class))
                .toList();

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints).allSatisfy(method -> {
            PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                    method, PreAuthorize.class);
            assertThat(authorization)
                    .as("%s must declare its minimum permission", method)
                    .isNotNull();
            assertThat(authorization.value())
                    .as("%s must not authorize with the identity marker", method)
                    .contains("hasAuthority")
                    .doesNotContain("IAM_USER");
        });
    }
}
