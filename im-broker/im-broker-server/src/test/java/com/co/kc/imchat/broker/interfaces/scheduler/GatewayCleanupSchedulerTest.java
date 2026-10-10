package com.co.kc.imchat.broker.interfaces.scheduler;

import com.co.kc.imchat.broker.application.local.LocalGatewayCleanupAppService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayCleanupSchedulerTest {

    @Test
    void delegatesExpiredGatewayCleanupToApplication() {
        LocalGatewayCleanupAppService service = mock(LocalGatewayCleanupAppService.class);
        GatewayCleanupScheduler scheduler = new GatewayCleanupScheduler(service);

        scheduler.cleanup();

        verify(service).cleanupExpired(any());
    }
}
