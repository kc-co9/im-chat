package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.List;

/** 应用权限目录全量同步请求。 */
public record PermissionCatalogSyncRequest(List<PermissionDefinitionRequest> permissions) {
    public PermissionCatalogSyncRequest {
        AssertUtils.allArgNotNull(
                "permission catalog request must not contain null values",
                permissions);
        permissions = List.copyOf(permissions);
    }
}
