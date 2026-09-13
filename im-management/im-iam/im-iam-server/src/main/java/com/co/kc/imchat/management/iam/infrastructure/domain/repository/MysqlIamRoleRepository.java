package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamInternalRoleType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalRoleService;
import com.co.kc.imchat.management.iam.transformer.domain.IamRoleDomainTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

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
                .map(IamRoleDomainTransformer.INSTANCE::roleFrom)
                .toList();
    }

    @Override
    public Optional<IamRole> find(IamRoleType type) {
        return roleService.getFirst(roleService.getQueryWrapper()
                        .eq(DbIamInternalRole::getType, DbIamInternalRoleType.valueOf(type.name())))
                .map(IamRoleDomainTransformer.INSTANCE::roleFrom);
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
                .map(IamRoleDomainTransformer.INSTANCE::roleFrom)
                .toList();
    }

    @Override
    public void save(IamRole role) {
        DbIamInternalRole entity = IamRoleDomainTransformer.INSTANCE.dbRoleFrom(role);
        boolean persisted = roleService.saveOrUpdate(entity);
        if (!persisted) {
            if (role.getPkId() != null) {
                throw new OptimisticLockingFailureException(
                        "IAM role was modified concurrently: " + role.getId().value());
            }
            throw new DataAccessResourceFailureException(
                    "IAM role was not inserted: " + role.getId().value());
        }
        if (persisted) {
            if (role.getPkId() == null) {
                role.setPkId(entity.getId());
            }
            role.setRowVersion(entity.getVersion());
        }
    }

}
