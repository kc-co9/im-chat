package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamInternalAdministratorRoleService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 基于 MySQL 的 IAM 管理员内部角色分配仓储。 */
@RequiredArgsConstructor
public class MysqlIamAdministratorRoleRepository implements IamAdministratorRoleRepository {
    private final DbIamInternalAdministratorRoleService administratorRoleService;

    @Override
    public void assign(AdministratorId administratorId, IamRoleId roleId) {
        boolean exists = administratorRoleService.isExist(
                administratorRoleService.getQueryWrapper()
                        .eq(DbIamInternalAdministratorRole::getAdministratorId, administratorId.value())
                        .eq(DbIamInternalAdministratorRole::getRoleId, roleId.value()));
        if (exists) {
            return;
        }
        DbIamInternalAdministratorRole relation = new DbIamInternalAdministratorRole();
        relation.setAdministratorId(administratorId.value());
        relation.setRoleId(roleId.value());
        administratorRoleService.save(relation);
    }

    @Override
    public Set<IamRoleId> findRoles(AdministratorId administratorId) {
        return administratorRoleService.list(administratorRoleService.getQueryWrapper()
                        .eq(DbIamInternalAdministratorRole::getAdministratorId, administratorId.value()))
                .stream()
                .map(DbIamInternalAdministratorRole::getRoleId)
                .map(IamRoleId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Long countActive(IamRoleId roleId) {
        return administratorRoleService.getBaseMapper().countActive(roleId.value());
    }

    @Override
    public void replace(AdministratorId administratorId, Set<IamRoleId> roleIds) {
        List<DbIamInternalAdministratorRole> oldRoles = administratorRoleService.list(
                administratorRoleService.getQueryWrapper()
                        .eq(DbIamInternalAdministratorRole::getAdministratorId, administratorId.value()));
        Set<Long> oldIds = oldRoles.stream()
                .map(DbIamInternalAdministratorRole::getRoleId)
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> newIds = roleIds.stream()
                .map(IamRoleId::value)
                .collect(Collectors.toUnmodifiableSet());

        List<Long> removedIds = oldIds.stream()
                .filter(roleId -> !newIds.contains(roleId))
                .toList();
        if (CollectionUtils.isNotEmpty(removedIds)) {
            administratorRoleService.remove(administratorRoleService.getQueryWrapper()
                    .eq(DbIamInternalAdministratorRole::getAdministratorId, administratorId.value())
                    .in(DbIamInternalAdministratorRole::getRoleId, removedIds));
        }

        List<DbIamInternalAdministratorRole> addedRoles = newIds.stream()
                .filter(roleId -> !oldIds.contains(roleId))
                .map(roleId -> relation(administratorId, roleId))
                .toList();
        if (CollectionUtils.isNotEmpty(addedRoles)) {
            administratorRoleService.saveBatch(addedRoles);
        }
    }

    private DbIamInternalAdministratorRole relation(
            AdministratorId administratorId,
            Long roleId
    ) {
        DbIamInternalAdministratorRole relation = new DbIamInternalAdministratorRole();
        relation.setAdministratorId(administratorId.value());
        relation.setRoleId(roleId);
        return relation;
    }
}
