package com.co.kc.imchat.management.iam.domain.authorization.service;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionDefinition;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 协调权限目录全量同步的领域服务。
 */
@RequiredArgsConstructor
public class ApplicationPermissionService {
    private final ApplicationPermissionRepository permissionRepository;
    private final SnowflakeId snowflakeId;

    /** 校验权限是否满足删除条件。 */
    public void ensureRemovable(ApplicationPermission permission) {
        permission.ensureRemovable();
        if (permissionRepository.hasRoleAssignments(permission.getId())) {
            throw new TransitionException("权限仍被角色引用，不能删除");
        }
    }

    /** 查询指定应用中可分配给角色的权限。 */
    public List<ApplicationPermission> findAssignable(Application application, Set<ApplicationPermissionId> permissionIds) {
        List<ApplicationPermission> permissions = permissionRepository.find(application.getAppId()).stream()
                .filter(permission -> permission.getStatus() == ApplicationPermissionStatus.ACTIVE)
                .filter(permission -> permissionIds.contains(permission.getId()))
                .toList();
        if (permissions.size() != permissionIds.size()) {
            throw new NotFoundException("应用权限不存在或已停用");
        }
        return permissions;
    }

    public List<ApplicationPermission> synchronize(Application application, List<ApplicationPermissionDefinition> newPermissions) {
        List<ApplicationPermission> oldPermissions = permissionRepository.find(application.getAppId());
        Map<ApplicationPermissionCode, ApplicationPermission> oldPermissionMap = FunctionUtils.mappingMap(oldPermissions, ApplicationPermission::getCode, Function.identity());

        List<ApplicationPermission> synchronizedPermissions = new ArrayList<>();
        for (ApplicationPermissionDefinition newPermission : newPermissions) {
            ApplicationPermission oldPermission = oldPermissionMap.remove(newPermission.code());
            if (oldPermission != null) {
                oldPermission.revise(newPermission.name(), newPermission.description());
                synchronizedPermissions.add(oldPermission);
            } else {
                synchronizedPermissions.add(
                        ApplicationPermission.builder()
                                .id(new ApplicationPermissionId(snowflakeId.next()))
                                .appId(application.getAppId())
                                .code(newPermission.code())
                                .name(newPermission.name())
                                .description(newPermission.description())
                                .status(ApplicationPermissionStatus.ACTIVE)
                                .build());
            }
        }
        for (ApplicationPermission deactivatePermission : oldPermissionMap.values()) {
            deactivatePermission.deactivate();
            synchronizedPermissions.add(deactivatePermission);
        }

        return synchronizedPermissions;
    }
}
