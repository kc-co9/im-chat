package com.co.kc.imchat.management.iam.model.io;

/** IAM 权限 HTTP 响应。 */
public record ApplicationPermissionResponse(
        Long id,
        String code,
        String name,
        String description
) {
}
