package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRoleService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationRoleDomainTransformer;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 基于 MySQL 的 IAM 应用角色仓储。 */
@RequiredArgsConstructor
public class MysqlApplicationRoleRepository implements ApplicationRoleRepository {
    private final DbIamApplicationRoleService roleService;
    private final DbIamApplicationRolePermissionService rolePermissionService;
    private final ApplicationRoleDomainTransformer transformer;

    @Override
    public boolean contains(AppId appId, ApplicationRoleCode code) {
        return roleService.count(Wrappers.lambdaQuery(DbIamApplicationRole.class)
                .eq(DbIamApplicationRole::getAppId, appId.value())
                .eq(DbIamApplicationRole::getCode, code.value())) > 0;
    }

    @Override
    public Optional<ApplicationRole> find(AppId appId, ApplicationRoleCode code) {
        DbIamApplicationRole role = roleService.getOne(Wrappers.lambdaQuery(DbIamApplicationRole.class)
                .eq(DbIamApplicationRole::getAppId, appId.value())
                .eq(DbIamApplicationRole::getCode, code.value())
                .last("LIMIT 1"), false);
        if (role == null) {
            return Optional.empty();
        }
        Set<ApplicationPermissionId> permissionIds = rolePermissionService.list(
                        Wrappers.lambdaQuery(DbIamApplicationRolePermission.class)
                                .eq(DbIamApplicationRolePermission::getRoleId, role.getRoleId()))
                .stream()
                .map(DbIamApplicationRolePermission::getPermissionId)
                .map(ApplicationPermissionId::new)
                .collect(Collectors.toUnmodifiableSet());
        return Optional.of(roleFrom(role, permissionIds));
    }

    @Override
    public Optional<ApplicationRole> find(ApplicationRoleId roleId) {
        return roleService.getFirst(Wrappers.lambdaQuery(DbIamApplicationRole.class)
                        .eq(DbIamApplicationRole::getRoleId, roleId.value()))
                .map(this::roleFrom);
    }

    @Override
    public List<ApplicationRole> findAll(Set<ApplicationRoleId> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleService.list(Wrappers.lambdaQuery(DbIamApplicationRole.class)
                        .in(DbIamApplicationRole::getRoleId,
                                roleIds.stream().map(ApplicationRoleId::value).toList()))
                .stream()
                .map(this::roleFrom)
                .toList();
    }

    @Override
    public PagingResult<ApplicationRole> page(AppId appId, Paging paging) {
        IPage<DbIamApplicationRole> page = roleService.page(
                new Page<>(paging.pageNo(), paging.pageSize()),
                roleService.getQueryWrapper()
                        .eq(DbIamApplicationRole::getAppId, appId.value())
                        .orderByDesc(DbIamApplicationRole::getId));
        return PagingResult.<ApplicationRole>newBuilder()
                .paging(paging)
                .records(page.getRecords().stream().map(this::roleFrom).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    public void save(ApplicationRole role) {
        DbIamApplicationRole dbRole = transformer.dbRoleFrom(role);
        if (role.getPkId() == null) {
            roleService.save(dbRole);
            role.setPkId(dbRole.getId());
        } else {
            roleService.updateById(dbRole);
        }
        rolePermissionService.remove(Wrappers.lambdaQuery(DbIamApplicationRolePermission.class)
                .eq(DbIamApplicationRolePermission::getRoleId, role.getId().value()));
        role.getPermissionIds().forEach(permissionId -> {
            DbIamApplicationRolePermission link = new DbIamApplicationRolePermission();
            link.setRoleId(role.getId().value());
            link.setPermissionId(permissionId.value());
            rolePermissionService.save(link);
        });
    }

    private ApplicationRole roleFrom(DbIamApplicationRole role) {
        Set<ApplicationPermissionId> permissionIds = rolePermissionService.list(
                        Wrappers.lambdaQuery(DbIamApplicationRolePermission.class)
                                .eq(DbIamApplicationRolePermission::getRoleId, role.getRoleId()))
                .stream()
                .map(DbIamApplicationRolePermission::getPermissionId)
                .map(ApplicationPermissionId::new)
                .collect(Collectors.toUnmodifiableSet());
        return roleFrom(role, permissionIds);
    }

    private ApplicationRole roleFrom(DbIamApplicationRole entity, Set<ApplicationPermissionId> permissionIds) {
        ApplicationRole role = transformer.roleFrom(entity, permissionIds);
        role.setPkId(entity.getId());
        return role;
    }
}
