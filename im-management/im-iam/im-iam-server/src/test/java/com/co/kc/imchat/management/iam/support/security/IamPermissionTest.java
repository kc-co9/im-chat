package com.co.kc.imchat.management.iam.support.security;

import com.co.kc.imchat.management.iam.interfaces.http.AdministratorController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IamPermissionTest {

    @Test
    void managementEndpointsUseDeclaredPermissionCodes() {
        Set<String> permissionCodes = Arrays.stream(IamPermission.values())
                .map(IamPermission::getCode)
                .collect(Collectors.toSet());

        Arrays.stream(AdministratorController.class.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(
                        method,
                        RequestMapping.class))
                .map(this::permissionCode)
                .forEach(code -> assertThat(permissionCodes).contains(code));
    }

    @Test
    void everyPermissionProvidesCatalogMetadata() {
        assertThat(IamPermission.values()).allSatisfy(permission -> {
            assertThat(permission.getCode()).isNotBlank();
            assertThat(permission.getDisplayName()).isNotBlank();
            assertThat(permission.getDescription()).isNotBlank();
        });
    }

    private String permissionCode(Method method) {
        RequiresPermission permission = AnnotatedElementUtils.findMergedAnnotation(
                method,
                RequiresPermission.class);
        assertThat(permission).isNotNull();
        return permission.value();
    }
}
