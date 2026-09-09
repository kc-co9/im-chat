package com.co.kc.imchat.management.iam.domain.authorization.service;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.ApplicationRoleRepository;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 校验并维护管理员在指定应用中的角色分配。 */
@RequiredArgsConstructor
public class ApplicationAdministratorRoleService {
    private final ApplicationRepository applicationRepository;
    private final AdministratorRepository administratorRepository;
    private final ApplicationRoleRepository roleRepository;
    private final ApplicationAdministratorRoleRepository assignmentRepository;

    public Set<ApplicationRoleId> getRoleIds(
            AdministratorId administratorId,
            AppId appId
    ) {
        ensureParticipants(administratorId, appId);
        return rolesInApplication(assignmentRepository.findRoles(administratorId), appId)
                .stream()
                .map(ApplicationRole::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void replace(
            AdministratorId administratorId,
            AppId appId,
            Set<ApplicationRoleId> requestedIds
    ) {
        ensureParticipants(administratorId, appId);
        List<ApplicationRole> requestedRoles = roleRepository.findAll(requestedIds);
        if (requestedRoles.size() != requestedIds.size()
                || requestedRoles.stream().anyMatch(role -> !appId.equals(role.getAppId()))) {
            throw new NotFoundException("应用角色不存在");
        }

        Set<ApplicationRoleId> currentIds = assignmentRepository.findRoles(administratorId);
        Set<ApplicationRoleId> replacement = new HashSet<>(currentIds);
        rolesInApplication(currentIds, appId)
                .forEach(role -> replacement.remove(role.getId()));
        replacement.addAll(requestedIds);
        assignmentRepository.replace(administratorId, Set.copyOf(replacement));
    }

    private void ensureParticipants(AdministratorId administratorId, AppId appId) {
        administratorRepository.find(administratorId)
                .orElseThrow(() -> new NotFoundException("IAM 管理账号不存在"));
        applicationRepository.find(appId)
                .orElseThrow(() -> new NotFoundException("应用不存在"));
    }

    private List<ApplicationRole> rolesInApplication(
            Set<ApplicationRoleId> roleIds,
            AppId appId
    ) {
        return roleRepository.findAll(roleIds).stream()
                .filter(role -> appId.equals(role.getAppId()))
                .toList();
    }
}
