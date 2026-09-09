package com.co.kc.imchat.management.iam.model.cqrs.dto;

/** IAM 应用可分配权限。 */
public record ApplicationPermissionDTO(
        /* 权限业务标识。 */
        Long id,
        /* 权限编码。 */
        String code,
        /* 权限名称。 */
        String name,
        /* 权限说明。 */
        String description
) {
}
