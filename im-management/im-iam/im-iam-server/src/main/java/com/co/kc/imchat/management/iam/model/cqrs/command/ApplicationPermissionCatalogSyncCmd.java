package com.co.kc.imchat.management.iam.model.cqrs.command;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.model.cqrs.dto.ApplicationPermissionDefinitionDTO;

import java.util.List;

/** 全量同步应用权限目录命令。 */
public record ApplicationPermissionCatalogSyncCmd(
        String clientId,
        List<ApplicationPermissionDefinitionDTO> permissions
) {
    public ApplicationPermissionCatalogSyncCmd {
        AssertUtils.argNotBlank("clientId must not be blank", clientId);
        AssertUtils.allArgNotNull(
                "permission catalog command must not contain null values",
                permissions);
        permissions = List.copyOf(permissions);
    }
}
