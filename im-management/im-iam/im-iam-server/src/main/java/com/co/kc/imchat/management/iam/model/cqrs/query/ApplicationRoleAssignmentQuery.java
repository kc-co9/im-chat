package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询管理员在指定应用中的角色分配。 */
public record ApplicationRoleAssignmentQuery(Long appId, Long administratorId) {
    public ApplicationRoleAssignmentQuery {
        AssertUtils.allArgNotNull(
                "application role assignment query must not contain null values",
                appId,
                administratorId);
    }
}
