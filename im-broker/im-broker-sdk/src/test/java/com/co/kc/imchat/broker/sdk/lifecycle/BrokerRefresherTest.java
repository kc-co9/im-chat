package com.co.kc.imchat.broker.sdk.lifecycle;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BrokerRefresherTest {
    @Test
    void refreshesBrokerSnapshotAndIsolatesFailure() {
        BrokerClient brokerClient = mock(BrokerClient.class);
        BrokerRefresher refresher = new BrokerRefresher(brokerClient);

        refresher.refresh();
        verify(brokerClient).refreshClusterState();

        doThrow(new IllegalStateException("broker unavailable"))
                .when(brokerClient).refreshClusterState();
        refresher.refresh();
    }
}
