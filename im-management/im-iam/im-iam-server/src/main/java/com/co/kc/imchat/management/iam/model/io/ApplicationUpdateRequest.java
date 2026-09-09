package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.model.enums.ApplicationStatusEnum;

/** 更新 IAM 接入应用请求。 */
public record ApplicationUpdateRequest(
        Long appId,
        String name,
        ApplicationStatusEnum status
) {
    public ApplicationUpdateRequest {
        AssertUtils.argNotNull("appId must not be null", appId);
        AssertUtils.argNotBlank("application name must not be blank", name);
        AssertUtils.argNotNull("application status must not be null", status);
    }
}
