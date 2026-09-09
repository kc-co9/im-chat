package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationAdministratorRoleService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 MySQL 的 IAM 管理员角色分配仓储。
 */
@RequiredArgsConstructor
public class MysqlApplicationAdministratorRoleRepository implements ApplicationAdministratorRoleRepository {
    private final DbIamApplicationAdministratorRoleService administratorRoleService;

    @Override
    public void assign(AdministratorId administratorId, ApplicationRoleId roleId) {
        if (administratorRoleService.isExist(administratorRoleService.getQueryWrapper()
                .eq(DbIamApplicationAdministratorRole::getAdministratorId, administratorId.value())
                .eq(DbIamApplicationAdministratorRole::getRoleId, roleId.value()))) {
            return;
        }
        DbIamApplicationAdministratorRole dbIamAdministratorRole = new DbIamApplicationAdministratorRole();
        dbIamAdministratorRole.setAdministratorId(administratorId.value());
        dbIamAdministratorRole.setRoleId(roleId.value());
        administratorRoleService.save(dbIamAdministratorRole);
    }

    @Override
    public Set<ApplicationRoleId> findRoles(AdministratorId administratorId) {
        List<DbIamApplicationAdministratorRole> dbIamAdministratorRoleList = administratorRoleService.list(administratorRoleService.getQueryWrapper()
                .eq(DbIamApplicationAdministratorRole::getAdministratorId, administratorId.value()));
        if (CollectionUtils.isEmpty(dbIamAdministratorRoleList)) {
            return Set.of();
        }
        return dbIamAdministratorRoleList.stream()
                .map(DbIamApplicationAdministratorRole::getRoleId)
                .map(ApplicationRoleId::new)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<AdministratorId> findAdministrators(ApplicationRoleId roleId) {
        return administratorRoleService.list(administratorRoleService.getQueryWrapper()
                        .eq(DbIamApplicationAdministratorRole::getRoleId, roleId.value()))
                .stream()
                .map(DbIamApplicationAdministratorRole::getAdministratorId)
                .map(AdministratorId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Long countActive(ApplicationRoleId roleId) {
        return administratorRoleService.getBaseMapper().countActive(roleId.value());
    }

    @Override
    public void replace(AdministratorId administratorId, Set<ApplicationRoleId> roleIds) {
        List<DbIamApplicationAdministratorRole> oldRoleList = administratorRoleService.list(administratorRoleService.getQueryWrapper()
                .eq(DbIamApplicationAdministratorRole::getAdministratorId, administratorId.value()));

        Set<Long> oldIdList = oldRoleList.stream()
                .map(DbIamApplicationAdministratorRole::getRoleId)
                .collect(Collectors.toSet());
        Set<Long> newIdList = roleIds.stream()
                .map(ApplicationRoleId::value)
                .collect(Collectors.toSet());

        List<Long> removeIdList = oldIdList.stream()
                .filter(id -> !newIdList.contains(id))
                .toList();
        if (CollectionUtils.isNotEmpty(removeIdList)) {
            administratorRoleService.remove(administratorRoleService.getQueryWrapper()
                    .eq(DbIamApplicationAdministratorRole::getAdministratorId, administratorId.value())
                    .in(DbIamApplicationAdministratorRole::getRoleId, removeIdList));
        }

        List<DbIamApplicationAdministratorRole> insertList = newIdList.stream()
                .filter(roleId -> !oldIdList.contains(roleId))
                .map(roleId -> {
                    DbIamApplicationAdministratorRole dbIamAdministratorRole = new DbIamApplicationAdministratorRole();
                    dbIamAdministratorRole.setAdministratorId(administratorId.value());
                    dbIamAdministratorRole.setRoleId(roleId);
                    return dbIamAdministratorRole;
                })
                .toList();
        if (CollectionUtils.isNotEmpty(insertList)) {
            administratorRoleService.saveBatch(insertList);
        }
    }
}
