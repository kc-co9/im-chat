package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询管理员 IAM 内部角色分配。 */
public record AdministratorRoleAssignmentQuery(Long administratorId) {
    public AdministratorRoleAssignmentQuery {
        AssertUtils.argNotNull("administratorId must not be null", administratorId);
    }
}
