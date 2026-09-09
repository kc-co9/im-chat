package com.co.kc.imchat.management.iam.infrastructure.config.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** IAM 登录失败锁定配置。 */
@ConfigurationProperties(prefix = "im.iam.login")
public record IamLoginProperties(
        @DefaultValue("5") Integer failureLimit,
        @DefaultValue("15m") Duration lockDuration
) {
    public IamLoginProperties {
        AssertUtils.allArgNotNull(
                "IAM login properties must not contain null values",
                failureLimit, lockDuration);
        AssertUtils.argTrue("IAM login failure limit must be positive", failureLimit > 0);
        AssertUtils.argTrue(
                "IAM login lock duration must be positive",
                !lockDuration.isNegative() && !lockDuration.isZero());
    }
}
