package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.management.iam.model.enums.ApplicationRoleStatusEnum;

import java.util.Set;

/** IAM 角色 HTTP 响应。 */
public record ApplicationRoleResponse(
        Long id,
        String code,
        String name,
        ApplicationRoleStatusEnum status,
        Set<Long> permissionIds
) {
    public ApplicationRoleResponse {
        permissionIds = Set.copyOf(permissionIds);
    }
}
