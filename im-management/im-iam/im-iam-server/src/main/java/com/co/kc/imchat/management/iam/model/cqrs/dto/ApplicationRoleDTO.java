package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleStatus;

import java.util.Set;

/** IAM 接入应用角色查询结果。 */
public record ApplicationRoleDTO(
        /* 角色业务标识。 */
        Long id,
        /* 角色编码。 */
        String code,
        /* 角色名称。 */
        String name,
        /* 角色状态。 */
        ApplicationRoleStatus status,
        /* 当前角色拥有的权限业务标识。 */
        Set<Long> permissionIds
) {
    public ApplicationRoleDTO {
        permissionIds = Set.copyOf(permissionIds);
    }
}
