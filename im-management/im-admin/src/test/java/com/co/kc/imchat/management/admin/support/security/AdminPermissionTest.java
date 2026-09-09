package com.co.kc.imchat.management.admin.support.security;

import com.co.kc.imchat.management.admin.interfaces.http.ManagedUserController;
import com.co.kc.imchat.management.iam.sdk.security.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionTest {

    @Test
    void catalogUsesSharedPermissionCodes() {
        assertThat(AdminPermission.USER_READ.getCode()).isEqualTo(AdminPermission.Code.USER_READ);
        assertThat(AdminPermission.USER_UPDATE.getCode()).isEqualTo(AdminPermission.Code.USER_UPDATE);
        assertThat(AdminPermission.USER_PASSWORD_RESET.getCode())
                .isEqualTo(AdminPermission.Code.USER_PASSWORD_RESET);
        assertThat(AdminPermission.USER_BAN.getCode()).isEqualTo(AdminPermission.Code.USER_BAN);
        assertThat(AdminPermission.USER_DELETE.getCode()).isEqualTo(AdminPermission.Code.USER_DELETE);
    }

    @Test
    void controllerUsesCatalogPermissionCodes() {
        Map<String, String> expectedPermissions = Map.of(
                "page", AdminPermission.Code.USER_READ,
                "detail", AdminPermission.Code.USER_READ,
                "update", AdminPermission.Code.USER_UPDATE,
                "resetPassword", AdminPermission.Code.USER_PASSWORD_RESET,
                "ban", AdminPermission.Code.USER_BAN,
                "unban", AdminPermission.Code.USER_BAN,
                "delete", AdminPermission.Code.USER_DELETE);

        assertThat(ManagedUserController.class.getDeclaredMethods())
                .filteredOn(method -> expectedPermissions.containsKey(method.getName()))
                .allSatisfy(method -> assertThat(requiredPermission(method))
                        .isEqualTo(expectedPermissions.get(method.getName())));
    }

    private String requiredPermission(Method method) {
        return method.getAnnotation(RequiresPermission.class).value();
    }
}
