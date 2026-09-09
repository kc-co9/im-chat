package com.co.kc.imchat.management.iam.domain.application.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 管理应用稳定键，项目内部使用小驼峰命名。 */
public record AppKey(String value) {
    public AppKey {
        AssertUtils.domainPropNotBlank("app key must not be blank", value);
        AssertUtils.domainPropTrue(
                "app key must use lower camel case",
                value.matches("^[a-z][A-Za-z0-9]*$"));
    }
}
