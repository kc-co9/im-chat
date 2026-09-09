package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** IAM 自定义角色创建请求。 */
public record RoleCreateRequest(Long appId, String code, String name, Set<Long> permissionIds) {
}
