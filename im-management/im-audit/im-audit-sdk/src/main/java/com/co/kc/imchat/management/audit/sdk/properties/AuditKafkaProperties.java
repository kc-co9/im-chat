package com.co.kc.imchat.management.audit.sdk.properties;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Kafka 审计投递配置。 */
public record AuditKafkaProperties(
        /* Spring Cloud Stream 输出 Binding 名称。 */
        @DefaultValue("auditOutput") String bindingName
) {
    public AuditKafkaProperties {
        AssertUtils.argNotBlank("audit Kafka binding name must not be blank", bindingName);
    }
}
