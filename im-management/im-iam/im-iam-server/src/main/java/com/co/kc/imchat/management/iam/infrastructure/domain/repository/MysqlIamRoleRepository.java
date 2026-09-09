package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamPermissionCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleStatus;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleName;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleStatus;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalRoleService;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 基于 MySQL 的 IAM 内部角色仓储。 */
@RequiredArgsConstructor
public class MysqlIamRoleRepository implements IamRoleRepository {
    private final DbIamInternalRoleService roleService;

    @Override
    public List<IamRole> findAll() {
        return roleService.list(roleService.getQueryWrapper()
                        .orderByAsc(DbIamInternalRole::getCode)
                        .orderByAsc(DbIamInternalRole::getRoleId))
                .stream()
                .map(this::roleFrom)
                .toList();
    }

    @Override
    public Optional<IamRole> find(IamRoleType type) {
        return roleService.getFirst(roleService.getQueryWrapper()
                        .eq(DbIamInternalRole::getType, DbIamInternalRoleType.valueOf(type.name())))
                .map(this::roleFrom);
    }

    @Override
    public List<IamRole> findAll(Set<IamRoleId> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = roleIds.stream()
                .map(IamRoleId::value)
                .collect(Collectors.toUnmodifiableSet());
        return roleService.list(roleService.getQueryWrapper()
                        .in(DbIamInternalRole::getRoleId, ids))
                .stream()
                .map(this::roleFrom)
                .toList();
    }

    @Override
    public void save(IamRole role) {
        DbIamInternalRole entity = role.getPkId() == null
                ? new DbIamInternalRole()
                : roleService.getById(role.getPkId());
        entity.setRoleId(role.getId().value());
        entity.setCode(role.getCode().value());
        entity.setName(role.getName().value());
        entity.setType(DbIamInternalRoleType.valueOf(role.getType().name()));
        entity.setStatus(DbIamInternalRoleStatus.valueOf(role.getStatus().name()));
        entity.setPermissions(role.getPermissions().stream()
                .map(IamPermissionCode::value)
                .sorted()
                .collect(Collectors.joining(",")));
        roleService.saveOrUpdate(entity);
        if (role.getPkId() == null) {
            role.setPkId(entity.getId());
        }
    }

    private IamRole roleFrom(DbIamInternalRole entity) {
        Set<IamPermissionCode> permissions = entity.getPermissions() == null
                || entity.getPermissions().isBlank()
                ? Set.of()
                : Arrays.stream(entity.getPermissions().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(IamPermissionCode::new)
                        .collect(Collectors.toUnmodifiableSet());
        IamRole role = new IamRole(
                new IamRoleId(entity.getRoleId()),
                new IamRoleCode(entity.getCode()),
                new IamRoleName(entity.getName()),
                IamRoleType.valueOf(entity.getType().name()),
                IamRoleStatus.valueOf(entity.getStatus().name()),
                permissions);
        role.setPkId(entity.getId());
        return role;
    }
}
