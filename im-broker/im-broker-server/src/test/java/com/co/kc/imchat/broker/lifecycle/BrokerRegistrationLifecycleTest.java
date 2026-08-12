package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.support.event.model.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.support.event.model.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BrokerRegistrationLifecycleTest {

    @Test
    void registersBrokerWhenApplicationIsReady() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        BrokerEventPublisher eventPublisher = mock(BrokerEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);

        lifecycle.onReady();

        verify(brokerRegistry).register("broker-10.0.0.1-12200", "10.0.0.1", 12200);
        verify(eventPublisher).publish(isA(BrokerRegisteredEvent.class));
    }

    @Test
    void propagatesRegistrationFailure() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        BrokerEventPublisher eventPublisher = mock(BrokerEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);
        doThrow(new IllegalStateException("register failed"))
                .when(brokerRegistry).register("broker-10.0.0.1-12200", "10.0.0.1", 12200);

        assertThrows(IllegalStateException.class, lifecycle::onReady);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retriesHeartbeatAfterPreviousHeartbeatFailure() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        BrokerEventPublisher eventPublisher = mock(BrokerEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);
        lifecycle.onReady();
        doThrow(new IllegalStateException("heartbeat failed"))
                .doNothing()
                .when(brokerRegistry).heartbeat("broker-10.0.0.1-12200");

        lifecycle.heartbeat();
        lifecycle.heartbeat();

        verify(brokerRegistry, times(2)).heartbeat("broker-10.0.0.1-12200");
        verify(eventPublisher).publish(isA(BrokerHeartbeatEvent.class));
    }

    private BrokerRegistrationLifecycle lifecycle(
            BrokerRegistry brokerRegistry,
            BrokerEventPublisher eventPublisher) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost("10.0.0.1");
        properties.getInstance().setPort(12200);
        return new BrokerRegistrationLifecycle(brokerRegistry, eventPublisher, properties);
    }
}
