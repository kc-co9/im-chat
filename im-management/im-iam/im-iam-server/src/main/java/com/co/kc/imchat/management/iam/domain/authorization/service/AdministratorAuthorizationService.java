package com.co.kc.imchat.management.iam.domain.authorization.service;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionStatus;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 IAM 当前账号、应用角色和权限目录实时解析有效权限。
 */
@RequiredArgsConstructor
public class AdministratorAuthorizationService {
    private final IamAdministratorRoleRepository iamAdministratorRoleRepository;
    private final IamRoleRepository iamRoleRepository;
    private final ApplicationAdministratorRoleRepository applicationAdministratorRoleRepository;
    private final ApplicationRoleRepository roleRepository;
    private final ApplicationPermissionRepository permissionRepository;

    /**
     * 解析管理员访问 IAM 自身管理端点时的内部权限。
     */
    public Set<IamPermissionCode> getPermissions(AdministratorId administratorId) {
        Set<IamRoleId> roleIds = iamAdministratorRoleRepository.findRoles(administratorId);
        if (CollectionUtils.isEmpty(roleIds)) {
            return Set.of();
        }
        List<IamRole> roles = iamRoleRepository.findAll(roleIds);
        return CollectionUtils.emptyIfNull(roles)
                .stream()
                .filter(IamRole::isActive)
                .map(IamRole::getPermissions)
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<ApplicationPermissionCode> getPermissions(
            AdministratorId administratorId,
            AppId appId
    ) {
        Set<ApplicationPermissionId> permissionIds = roleRepository.findAll(
                        applicationAdministratorRoleRepository.findRoles(administratorId))
                .stream()
                .filter(role -> appId.equals(role.getAppId()))
                .filter(role -> role.getStatus() == ApplicationRoleStatus.ACTIVE)
                .flatMap(role -> role.getPermissionIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        return permissionRepository.find(appId).stream()
                .filter(permission -> permission.getStatus() == ApplicationPermissionStatus.ACTIVE)
                .filter(permission -> permissionIds.contains(permission.getId()))
                .map(ApplicationPermission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

}
