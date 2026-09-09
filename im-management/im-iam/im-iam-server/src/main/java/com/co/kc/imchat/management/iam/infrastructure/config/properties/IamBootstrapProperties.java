package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 首个 IAM 超级管理员初始化配置。 */
@ConfigurationProperties(prefix = "im.iam.bootstrap")
public record IamBootstrapProperties(
        @DefaultValue("false") Boolean enabled,
        String username,
        String email,
        String password
) {
    public IamBootstrapProperties {
        AssertUtils.argNotNull("IAM bootstrap enabled must not be null", enabled);
        if (enabled) {
            AssertUtils.argNotBlank("IAM bootstrap username must not be blank", username);
            AssertUtils.argNotBlank("IAM bootstrap email must not be blank", email);
            AssertUtils.argNotBlank("IAM bootstrap password must not be blank", password);
        }
    }

}
