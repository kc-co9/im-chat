package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;

/** IAM 应用查询结果。 */
public record ApplicationDTO(
        Long appId,
        String appKey,
        String name,
        AppStatus status
) {
}
