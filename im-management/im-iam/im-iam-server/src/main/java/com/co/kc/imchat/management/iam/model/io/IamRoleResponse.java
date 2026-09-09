package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** IAM 内部角色 HTTP 响应。 */
public record IamRoleResponse(
        Long id,
        String code,
        String name,
        String type,
        String status,
        Set<String> permissions
) {
    public IamRoleResponse {
        permissions = Set.copyOf(permissions);
    }
}
