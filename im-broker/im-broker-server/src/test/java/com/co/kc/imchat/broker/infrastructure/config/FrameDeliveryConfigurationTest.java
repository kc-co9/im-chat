package com.co.kc.imchat.broker.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class FrameDeliveryConfigurationTest {

    @Test
    void dispatchesGatewayWritesOnVirtualThreads() throws Exception {
        try (ExecutorService executor = new FrameDeliveryConfiguration().brokerFrameDispatchExecutor()) {
            assertThat(executor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
        }
    }
}
