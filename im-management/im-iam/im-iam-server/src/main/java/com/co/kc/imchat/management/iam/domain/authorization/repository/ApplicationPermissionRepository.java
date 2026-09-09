package com.co.kc.imchat.management.iam.domain.authorization.repository;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermissionQueryCondition;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;

import java.util.List;
import java.util.Optional;

/** 应用权限目录仓储。 */
public interface ApplicationPermissionRepository {
    List<ApplicationPermission> find(AppId appId);

    Optional<ApplicationPermission> find(ApplicationPermissionId permissionId);

    PagingResult<ApplicationPermission> page(
            AppId appId,
            ApplicationPermissionQueryCondition condition,
            Paging paging);

    boolean hasRoleAssignments(ApplicationPermissionId permissionId);

    void saveAll(List<ApplicationPermission> permissions);

    void remove(ApplicationPermission permission);
}
