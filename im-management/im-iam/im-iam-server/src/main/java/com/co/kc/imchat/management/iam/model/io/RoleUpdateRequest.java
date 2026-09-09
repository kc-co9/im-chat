package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 更新 IAM 角色的 HTTP 请求。 */
public record RoleUpdateRequest(Long roleId, String name, Set<Long> permissionIds) {
}
