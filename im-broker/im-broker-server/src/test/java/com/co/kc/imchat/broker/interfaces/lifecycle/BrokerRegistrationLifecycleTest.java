package com.co.kc.imchat.broker.interfaces.lifecycle;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRegisteredEvent;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BrokerRegistrationLifecycleTest {

    @Test
    void registersBrokerWhenApplicationIsReady() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);

        lifecycle.onReady();

        verify(brokerRegistry).register("broker-10.0.0.1-12200", "10.0.0.1", 12200);
        verify(eventPublisher).publish(any(BrokerRegisteredEvent.class));
    }

    @Test
    void propagatesRegistrationFailure() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);
        doThrow(new IllegalStateException("register failed"))
                .when(brokerRegistry).register("broker-10.0.0.1-12200", "10.0.0.1", 12200);

        assertThrows(IllegalStateException.class, lifecycle::onReady);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retriesHeartbeatAfterPreviousHeartbeatFailure() {
        BrokerRegistry brokerRegistry = mock(BrokerRegistry.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        BrokerRegistrationLifecycle lifecycle = lifecycle(brokerRegistry, eventPublisher);
        lifecycle.onReady();
        doThrow(new IllegalStateException("heartbeat failed"))
                .doNothing()
                .when(brokerRegistry).heartbeat("broker-10.0.0.1-12200");

        lifecycle.heartbeat();
        lifecycle.heartbeat();

        verify(brokerRegistry, times(2)).heartbeat("broker-10.0.0.1-12200");
        verify(eventPublisher).publish(any(BrokerHeartbeatEvent.class));
    }

    private BrokerRegistrationLifecycle lifecycle(
            BrokerRegistry brokerRegistry,
            DomainEventPublisher eventPublisher) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost("10.0.0.1");
        properties.getInstance().setPort(12200);
        return new BrokerRegistrationLifecycle(brokerRegistry, eventPublisher,
                properties.getInstance().getId(), properties.getInstance().getHost(),
                properties.getInstance().getPort());
    }
}
