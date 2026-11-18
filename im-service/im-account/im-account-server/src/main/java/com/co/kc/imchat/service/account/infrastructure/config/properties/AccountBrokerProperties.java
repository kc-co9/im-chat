package com.co.kc.imchat.service.account.infrastructure.config.properties;

import jakarta.validation.constraints.Positive;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("im.account.broker")
public class AccountBrokerProperties {
    private List<BoltTarget> bootstrapTargets = List.of();

    @Positive
    private int timeoutMillis = 3000;
}
