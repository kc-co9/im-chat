package com.co.kc.imchat.broker.sdk.lifecycle;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Refreshes the Broker cluster snapshot used by a Broker client.
 */
@Slf4j
@RequiredArgsConstructor
public class BrokerRefresher {
    private static final long REFRESH_DELAY_MILLIS = 30_000L;

    private final BrokerClient brokerClient;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(fixedDelay = REFRESH_DELAY_MILLIS)
    public void refresh() {
        try {
            brokerClient.refreshBrokerAddresses();
        } catch (RuntimeException exception) {
            log.warn("failed to refresh Broker addresses", exception);
        }
    }
}
