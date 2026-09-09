package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 注册应用名称。 */
public record AppName(String value) {
    public AppName {
        AssertUtils.domainPropNotBlank("app name must not be blank", value);
    }
}
