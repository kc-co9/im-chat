package com.co.kc.imchat.management.iam.domain.authorization.repository;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;

import java.util.Set;

/** IAM 管理员内部角色分配仓储。 */
public interface IamAdministratorRoleRepository {
    void assign(AdministratorId administratorId, IamRoleId roleId);

    Set<IamRoleId> findRoles(AdministratorId administratorId);

    Long countActive(IamRoleId roleId);

    void replace(AdministratorId administratorId, Set<IamRoleId> roleIds);
}
