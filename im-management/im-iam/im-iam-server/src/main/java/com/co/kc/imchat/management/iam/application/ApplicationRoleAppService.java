package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.session.repository.OAuthSessionRepository;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationAdministratorRoleService;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleCreateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleAssignmentChangeCmd;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationRoleDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.RoleAssignmentDTO;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationRolePageQuery;
import com.co.kc.imchat.management.iam.model.cqrs.query.ApplicationRoleAssignmentQuery;
import com.co.kc.imchat.management.iam.transformer.application.ApplicationRoleAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.time.Instant;

/**
 * IAM 应用角色管理服务。
 */
@RequiredArgsConstructor
public class ApplicationRoleAppService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationPermissionService permissionService;
    private final ApplicationRoleRepository roleRepository;
    private final SnowflakeId snowflakeId;
    private final ApplicationAdministratorRoleRepository administratorRoleRepository;
    private final OAuthSessionRepository oauthSessionRepository;
    private final ApplicationAdministratorRoleService applicationRoleService;

    public PagingResult<ApplicationRoleDTO> page(ApplicationRolePageQuery query) {
        Application application = applicationRepository.find(new AppId(query.appId()))
                .orElseThrow(() -> new NotFoundException("应用不存在"));
        return roleRepository.page(application.getAppId(), query.paging())
                .map(ApplicationRoleAppTransformer.INSTANCE::applicationRoleDtoFrom);
    }

    public RoleAssignmentDTO getAdministratorRoles(ApplicationRoleAssignmentQuery query) {
        AdministratorId administratorId = new AdministratorId(query.administratorId());
        AppId appId = new AppId(query.appId());
        Set<Long> roleIds = applicationRoleService.getRoleIds(administratorId, appId).stream()
                .map(ApplicationRoleId::value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RoleAssignmentDTO(roleIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeAdministratorRoles(ApplicationRoleAssignmentChangeCmd command) {
        AdministratorId administratorId = new AdministratorId(command.administratorId());
        AppId appId = new AppId(command.appId());
        Set<ApplicationRoleId> roleIds = FunctionUtils.mappingSet(
                command.roleIds(),
                ApplicationRoleId::new);

        applicationRoleService.replace(administratorId, appId, roleIds);
        oauthSessionRepository.revoke(administratorId, Instant.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(ApplicationRoleCreateCmd command) {
        AppId appId = new AppId(command.appId());
        ApplicationRoleCode code = new ApplicationRoleCode(command.code());
        ApplicationRoleName name = new ApplicationRoleName(command.name());
        Set<ApplicationPermissionId> permissionIds = FunctionUtils.mappingSet(
                command.permissionIds(),
                ApplicationPermissionId::new);

        Application application = applicationRepository.find(appId)
                .orElseThrow(() -> new NotFoundException("应用不存在"));
        if (roleRepository.contains(application.getAppId(), code)) {
            throw new RepeatException("角色编码已存在");
        }

        List<ApplicationPermission> permissions = permissionService.findAssignable(application, permissionIds);

        ApplicationRole role = ApplicationRole.builder()
                .id(new ApplicationRoleId(snowflakeId.next()))
                .appId(application.getAppId())
                .code(code)
                .name(name)
                .type(ApplicationRoleType.CUSTOM)
                .status(ApplicationRoleStatus.ACTIVE)
                .permissionIds(FunctionUtils.mappingSet(permissions, ApplicationPermission::getId))
                .build();
        roleRepository.save(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ApplicationRoleUpdateCmd command) {
        ApplicationRoleId roleId = new ApplicationRoleId(command.roleId());
        Set<ApplicationPermissionId> permissionIds = FunctionUtils.mappingSet(
                command.permissionIds(),
                ApplicationPermissionId::new);

        ApplicationRole role = roleRepository.find(roleId)
                .orElseThrow(() -> new NotFoundException("角色不存在"));
        Application application = applicationRepository.find(role.getAppId())
                .orElseThrow(() -> new NotFoundException("应用不存在"));
        List<ApplicationPermission> permissions = permissionService.findAssignable(application, permissionIds);

        role.reviseName(new ApplicationRoleName(command.name()));
        role.changePermissions(Set.copyOf(permissions));
        roleRepository.save(role);
        Instant revokedAt = Instant.now();
        administratorRoleRepository.findAdministrators(roleId)
                .forEach(administratorId -> oauthSessionRepository.revoke(
                        administratorId,
                        revokedAt));
    }
}
