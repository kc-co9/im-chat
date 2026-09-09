package com.co.kc.imchat.management.iam.domain.administrator.service;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 管理员生命周期和角色关系中的跨聚合业务规则。
 */
@RequiredArgsConstructor
public class AdministratorService {
    private final IamRoleRepository roleRepository;
    private final IamAdministratorRoleRepository administratorRoleRepository;

    public List<IamRole> getRoles() {
        return roleRepository.findAll();
    }

    /**
     * 确保停用或删除管理员后仍至少保留一个 IAM 超级管理员。
     */
    public void ensureRemovable(Administrator administrator) {
        AdministratorId administratorId = administrator.getId();
        Set<IamRoleId> roleIds = administratorRoleRepository.findRoles(administratorId);
        List<IamRole> roles = roleRepository.findAll(roleIds);
        if (roles.isEmpty()) {
            return;
        }

        IamRole superRole = roles.stream()
                .filter(IamRole::isSuperAdmin)
                .findFirst()
                .orElse(null);
        if (superRole == null) {
            return;
        }

        Long administratorCount = administratorRoleRepository
                .countActive(superRole.getId());
        if (administratorCount <= 1) {
            throw new TransitionException("至少保留一个 IAM 超级管理员");
        }
    }

    /**
     * 确保目标角色均可分配，且变更后仍至少保留一个 IAM 超级管理员。
     */
    public void ensureReplaceable(AdministratorId administratorId, Set<IamRoleId> roleIds) {
        List<IamRole> roles = roleRepository.findAll(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new NotFoundException("角色不存在");
        }
        if (roles.stream().anyMatch(role -> !role.isActive())) {
            throw new TransitionException("角色已停用");
        }

        IamRole superRole = roleRepository.findAll(
                        administratorRoleRepository.findRoles(administratorId)).stream()
                .filter(IamRole::isSuperAdmin)
                .findFirst()
                .orElse(null);
        if (superRole == null || roleIds.contains(superRole.getId())) {
            return;
        }

        Long administratorCount = administratorRoleRepository
                .countActive(superRole.getId());
        if (administratorCount <= 1) {
            throw new TransitionException("至少保留一个 IAM 超级管理员");
        }
    }
}
