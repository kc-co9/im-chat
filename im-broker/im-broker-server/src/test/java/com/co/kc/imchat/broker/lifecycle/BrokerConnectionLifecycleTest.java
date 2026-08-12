package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BrokerConnectionLifecycleTest {

    @Test
    void scheduledMigrationDelegatesToBrokerConnectionService() {
        BrokerConnectionService brokerConnectionService = mock(BrokerConnectionService.class);
        BrokerConnectionLifecycle lifecycle = new BrokerConnectionLifecycle(brokerConnectionService);

        lifecycle.migrateConnection();

        verify(brokerConnectionService).migrateConnection();
    }
}
