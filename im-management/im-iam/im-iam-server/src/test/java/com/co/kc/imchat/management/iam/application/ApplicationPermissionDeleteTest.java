package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDescription;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionDeleteCmd;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationPermissionDeleteTest {

    @Test
    void rejectsDeletionWhileARoleStillReferencesThePermission() {
        ApplicationPermissionRepository permissions = mock(ApplicationPermissionRepository.class);
        ApplicationPermission permission = inactivePermission();
        when(permissions.find(new ApplicationPermissionId(10L))).thenReturn(Optional.of(permission));
        when(permissions.hasRoleAssignments(new ApplicationPermissionId(10L))).thenReturn(true);
        ApplicationPermissionAppService service = new ApplicationPermissionAppService(
                mock(ApplicationRepository.class), mock(OAuthClientRepository.class),
                permissions, new ApplicationPermissionService(permissions, mock(SnowflakeId.class)));

        assertThatThrownBy(() -> service.delete(new ApplicationPermissionDeleteCmd(10L)))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("权限仍被角色引用");

        verify(permissions, never()).remove(permission);
    }

    private static ApplicationPermission inactivePermission() {
        return ApplicationPermission.builder()
                .id(new ApplicationPermissionId(10L))
                .appId(new AppId(1L))
                .code(new ApplicationPermissionCode("unused:read"))
                .name(new ApplicationPermissionName("旧权限"))
                .description(new ApplicationPermissionDescription("已经停用"))
                .status(ApplicationPermissionStatus.INACTIVE)
                .build();
    }
}
