package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.management.iam.model.enums.ApplicationStatusEnum;

/** IAM 接入应用 HTTP 响应。 */
public record ApplicationResponse(
        Long appId,
        String appKey,
        String name,
        ApplicationStatusEnum status
) {
}
