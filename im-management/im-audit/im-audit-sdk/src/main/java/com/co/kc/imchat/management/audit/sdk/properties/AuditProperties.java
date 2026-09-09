package com.co.kc.imchat.management.audit.sdk.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 审计 SDK 基础配置。 */
@ConfigurationProperties(prefix = "im.audit")
public record AuditProperties(
        /* 是否启用审计投递。 */
        @DefaultValue("false") Boolean enabled,
        /* 投递方式。 */
        @DefaultValue("KAFKA") AuditTransportType transport,
        /* Kafka 投递配置。 */
        @DefaultValue AuditKafkaProperties kafka,
        /* HTTP 投递配置。 */
        @DefaultValue AuditHttpProperties http,
        /* IAM 机器身份配置。 */
        @DefaultValue AuditIamProperties iam
) {
    public AuditProperties {
        AssertUtils.argNotNull("audit enabled must not be null", enabled);
        AssertUtils.argNotNull("audit transport must not be null", transport);
        AssertUtils.allArgNotNull(
                "audit transport properties must not be null",
                kafka,
                http,
                iam);
        if (Boolean.TRUE.equals(enabled)) {
            if (transport == AuditTransportType.HTTP) {
                http.validateForUse();
            }
            if (transport == AuditTransportType.HTTP) {
                iam.validateForUse();
            }
        }
    }
}
