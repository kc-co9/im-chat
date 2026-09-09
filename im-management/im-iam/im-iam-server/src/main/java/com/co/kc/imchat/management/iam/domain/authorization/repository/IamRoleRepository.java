package com.co.kc.imchat.management.iam.domain.authorization.repository;

import com.co.kc.imchat.management.iam.domain.authorization.model.IamRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.IamRoleType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** IAM 内部角色仓储。 */
public interface IamRoleRepository {
    List<IamRole> findAll();

    Optional<IamRole> find(IamRoleType type);

    List<IamRole> findAll(Set<IamRoleId> roleIds);

    void save(IamRole role);
}
