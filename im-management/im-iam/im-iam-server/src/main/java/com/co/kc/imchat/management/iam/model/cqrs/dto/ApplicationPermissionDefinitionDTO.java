package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 应用上报的权限定义。 */
public record ApplicationPermissionDefinitionDTO(
        String code,
        String name,
        String description
) {
    public ApplicationPermissionDefinitionDTO {
        AssertUtils.argNotBlank("permission code must not be blank", code);
        AssertUtils.argNotBlank("permission name must not be blank", name);
        AssertUtils.argNotBlank("permission description must not be blank", description);
    }
}
