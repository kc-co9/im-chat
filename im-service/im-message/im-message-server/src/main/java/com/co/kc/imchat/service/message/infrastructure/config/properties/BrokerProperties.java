package com.co.kc.imchat.service.message.infrastructure.config.properties;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Message 服务调用 Broker 的客户端配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties("im.message.broker.bolt")
public class BrokerProperties {
    @Positive
    private int timeoutMillis = 3000;
}
