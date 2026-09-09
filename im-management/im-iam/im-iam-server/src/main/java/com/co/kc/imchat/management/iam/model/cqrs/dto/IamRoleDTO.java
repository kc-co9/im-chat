package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.util.Set;

/** IAM 内部角色只读结果。 */
public record IamRoleDTO(
        Long id,
        String code,
        String name,
        String type,
        String status,
        Set<String> permissions
) {
    public IamRoleDTO {
        permissions = Set.copyOf(permissions);
    }
}
