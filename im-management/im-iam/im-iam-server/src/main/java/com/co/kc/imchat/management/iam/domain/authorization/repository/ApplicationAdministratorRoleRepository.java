package com.co.kc.imchat.management.iam.domain.authorization.repository;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;

import java.util.Set;

/**
 * IAM 管理员角色分配仓储。
 */
public interface ApplicationAdministratorRoleRepository {
    void assign(AdministratorId administratorId, ApplicationRoleId roleId);

    Set<ApplicationRoleId> findRoles(AdministratorId administratorId);

    Set<AdministratorId> findAdministrators(ApplicationRoleId roleId);

    Long countActive(ApplicationRoleId roleId);

    void replace(AdministratorId administratorId, Set<ApplicationRoleId> roleIds);
}
