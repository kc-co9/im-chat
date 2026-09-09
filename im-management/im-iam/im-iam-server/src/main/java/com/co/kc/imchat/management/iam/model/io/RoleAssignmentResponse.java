package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 管理员角色分配 HTTP 响应。 */
public record RoleAssignmentResponse(Set<Long> roleIds) {
    public RoleAssignmentResponse {
        roleIds = Set.copyOf(roleIds);
    }
}
