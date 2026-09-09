package com.co.kc.imchat.management.iam.domain.authorization.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** IAM 应用角色仓储。 */
public interface ApplicationRoleRepository {
    boolean contains(AppId appId, ApplicationRoleCode code);

    Optional<ApplicationRole> find(AppId appId, ApplicationRoleCode code);

    Optional<ApplicationRole> find(ApplicationRoleId roleId);

    List<ApplicationRole> findAll(Set<ApplicationRoleId> roleIds);

    PagingResult<ApplicationRole> page(AppId appId, Paging paging);

    void save(ApplicationRole role);
}
