package com.co.kc.imchat.management.audit.sdk.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * 审计 SDK 使用的 IAM 机器身份配置。
 */
public record AuditIamProperties(
        /* IAM OAuth2 Token 地址。 */
        URI tokenUri,
        /* 审计生产者机器 clientId。 */
        String clientId,
        /* 审计生产者机器 clientSecret。 */
        String clientSecret,
        /* IAM Token 请求超时。 */
        @DefaultValue("3s") Duration timeout,
        /* Access Token 到期前的刷新提前量。 */
        @DefaultValue("30s") Duration tokenRefreshSkew
) {
    public AuditIamProperties {
        AssertUtils.allArgNotNull(
                "audit IAM duration properties must not be null",
                timeout,
                tokenRefreshSkew);
        AssertUtils.argTrue("audit IAM timeout must be positive",
                !timeout.isNegative() && !timeout.isZero());
        AssertUtils.argTrue("audit IAM token refresh skew must not be negative",
                !tokenRefreshSkew.isNegative());
    }

    /** 校验使用 IAM 机器身份时必须配置的属性。 */
    public void validateForUse() {
        AssertUtils.argNotNull("audit IAM token URI must not be null", tokenUri);
        AssertUtils.argNotBlank("audit IAM client id must not be blank", clientId);
        AssertUtils.argNotBlank("audit IAM client secret must not be blank", clientSecret);
    }

    @Override
    public String toString() {
        return "AuditIamProperties[tokenUri=" + tokenUri
                + ", clientId=" + clientId
                + ", clientSecret=***"
                + ", timeout=" + timeout
                + ", tokenRefreshSkew=" + tokenRefreshSkew + "]";
    }
}
