package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

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
        return roleService.count(roleService.getQueryWrapper()
                .eq(DbIamApplicationRole::getAppId, appId.value())
                .eq(DbIamApplicationRole::getCode, code.value())) > 0;
    }

    @Override
    public Optional<ApplicationRole> find(AppId appId, ApplicationRoleCode code) {
        return roleService.getFirst(roleService.getQueryWrapper()
                        .eq(DbIamApplicationRole::getAppId, appId.value())
                        .eq(DbIamApplicationRole::getCode, code.value()))
                .map(this::loadRole);
    }

    private ApplicationRole loadRole(DbIamApplicationRole role) {
        Set<ApplicationPermissionId> permissionIds = rolePermissionService.list(
                        rolePermissionService.getQueryWrapper()
                                .eq(DbIamApplicationRolePermission::getRoleId, role.getRoleId()))
                .stream()
                .map(DbIamApplicationRolePermission::getPermissionId)
                .map(ApplicationPermissionId::new)
                .collect(Collectors.toUnmodifiableSet());
        return transformer.roleFrom(role, permissionIds);
    }

    @Override
    public Optional<ApplicationRole> find(ApplicationRoleId roleId) {
        return roleService.getFirst(roleService.getQueryWrapper()
                        .eq(DbIamApplicationRole::getRoleId, roleId.value()))
                .map(this::loadRole);
    }

    @Override
    public List<ApplicationRole> findAll(Set<ApplicationRoleId> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleService.list(roleService.getQueryWrapper()
                        .in(DbIamApplicationRole::getRoleId,
                                roleIds.stream().map(ApplicationRoleId::value).toList()))
                .stream()
                .map(this::loadRole)
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
                .records(page.getRecords().stream().map(this::loadRole).toList())
                .total(page.getTotal())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ApplicationRole role) {
        DbIamApplicationRole dbRole = transformer.dbRoleFrom(role);
        boolean insert = role.getPkId() == null;
        boolean persisted = insert ? roleService.save(dbRole) : roleService.updateById(dbRole);
        if (!persisted) {
            if (insert) {
                throw new DataAccessResourceFailureException(
                        "Application role was not inserted: " + role.getId().value());
            }
            throw new OptimisticLockingFailureException(
                    "Application role was modified concurrently: " + role.getId().value());
        }
        rolePermissionService.remove(rolePermissionService.getQueryWrapper()
                .eq(DbIamApplicationRolePermission::getRoleId, role.getId().value()));
        List<DbIamApplicationRolePermission> links = role.getPermissionIds().stream().map(permissionId -> {
            DbIamApplicationRolePermission link = new DbIamApplicationRolePermission();
            link.setRoleId(role.getId().value());
            link.setPermissionId(permissionId.value());
            return link;
        }).toList();
        if (!links.isEmpty() && !rolePermissionService.saveBatch(links)) {
            throw new DataAccessResourceFailureException(
                    "Application role permissions were not inserted: " + role.getId().value());
        }
        if (insert) {
            role.setPkId(dbRole.getId());
        }
        role.setRowVersion(dbRole.getVersion());
    }

}
