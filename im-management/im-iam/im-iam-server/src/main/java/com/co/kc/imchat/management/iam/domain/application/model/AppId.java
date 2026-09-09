package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 注册应用聚合标识。 */
public record AppId(Long value) {
    public AppId {
        AssertUtils.domainPropNotNull("app id must not be null", value);
        AssertUtils.domainPropTrue("app id must be positive", value > 0);
    }
}
