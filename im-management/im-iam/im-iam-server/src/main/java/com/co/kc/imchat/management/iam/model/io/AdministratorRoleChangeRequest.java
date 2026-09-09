package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/** 管理员角色变更请求。 */
public record AdministratorRoleChangeRequest(Long administratorId, Set<Long> roleIds) {
}
