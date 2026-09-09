package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 应用详情查询。 */
public record ApplicationGetQuery(Long appId) {
    public ApplicationGetQuery {
        AssertUtils.argNotNull("appId must not be null", appId);
    }
}
