package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRoleTest {

    @Test
    void acceptsOnlyPermissionsOwnedByTheSameApplication() {
        AppId admin = new AppId(1L);
        ApplicationRole role = role(admin);

        role.changePermissions(Set.of(
                permission(10L, admin, "user:read"),
                permission(11L, admin, "user:write")));

        assertThat(role.getPermissionIds())
                .containsExactlyInAnyOrder(new ApplicationPermissionId(10L), new ApplicationPermissionId(11L));
    }

    @Test
    void rejectsPermissionsOwnedByAnotherApplication() {
        ApplicationRole role = role(new AppId(1L));

        assertThatThrownBy(() -> role.changePermissions(Set.of(
                permission(10L, new AppId(2L), "broker:read"))))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("角色不能关联其他应用的权限");
    }

    private static ApplicationRole role(AppId appId) {
        return role(appId, ApplicationRoleType.CUSTOM);
    }

    private static ApplicationRole role(AppId appId, ApplicationRoleType type) {
        return ApplicationRole.builder()
                .id(new ApplicationRoleId(1L))
                .appId(appId)
                .code(new ApplicationRoleCode("ADMIN"))
                .name(new ApplicationRoleName("管理员"))
                .type(type)
                .status(ApplicationRoleStatus.ACTIVE)
                .permissionIds(Set.of())
                .build();
    }

    private static ApplicationPermission permission(long id, AppId appId, String code) {
        return ApplicationPermission.builder()
                .id(new ApplicationPermissionId(id))
                .appId(appId)
                .code(new ApplicationPermissionCode(code))
                .name(new ApplicationPermissionName(code))
                .description(new ApplicationPermissionDescription(code))
                .status(ApplicationPermissionStatus.ACTIVE)
                .build();
    }
}
