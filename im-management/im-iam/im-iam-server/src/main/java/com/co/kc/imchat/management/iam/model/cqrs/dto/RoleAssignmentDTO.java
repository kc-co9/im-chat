package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.util.Set;

/** 管理员角色分配只读结果。 */
public record RoleAssignmentDTO(Set<Long> roleIds) {
    public RoleAssignmentDTO {
        roleIds = Set.copyOf(roleIds);
    }
}
