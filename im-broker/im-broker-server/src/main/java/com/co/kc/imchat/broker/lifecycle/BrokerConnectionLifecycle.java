package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Broker 用户连接生命周期。
 */
@Component
@RequiredArgsConstructor
public class BrokerConnectionLifecycle {
    private final BrokerConnectionService brokerConnectionService;

    @Scheduled(fixedDelayString = "${im.broker.connection.migration.fixed-delay-millis:30000}")
    public void migrateConnection() {
        brokerConnectionService.migrateConnection();
    }
}
