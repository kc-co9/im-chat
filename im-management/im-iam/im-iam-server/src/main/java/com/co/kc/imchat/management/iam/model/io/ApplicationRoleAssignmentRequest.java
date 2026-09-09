package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 替换管理员应用角色的 HTTP 请求。 */
public record ApplicationRoleAssignmentRequest(
        Long appId,
        Long administratorId,
        Set<Long> roleIds
) {
}
