package com.co.kc.imchat.broker.interfaces.listener;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.application.gossip.BrokerGossipAppService;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.interfaces.listener.broker.BrokerRegistryEventListener;
import com.co.kc.imchat.broker.interfaces.listener.connection.ConnectionRegistryEventListener;
import com.co.kc.imchat.broker.interfaces.listener.gateway.GatewayRegistryEventListener;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRemovedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRemovedEvent;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class RegistryEventListenersTest {
    @Test
    void springEventsStillUpdateObservationsAndRemovalFacts() {
        BrokerRegistry brokers = mock(BrokerRegistry.class);
        GatewayRegistry gateways = mock(GatewayRegistry.class);
        BrokerGossipAdapter store = mock(BrokerGossipAdapter.class);
        when(brokers.find("broker-a")).thenReturn(Optional.empty());
        when(gateways.find(new GatewayId("gateway-a"))).thenReturn(Optional.empty());
        LocalDateTime occurredOn = LocalDateTime.of(2026, 10, 6, 0, 0);
        List<ConnectionRegistration> removed = List.of(ConnectionRegistration.active(
                new ConnectionRouteId(new UserId(2L), new GatewayId("gateway-a")), new RouteRevision(1L), Instant.MAX));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BrokerGossipAppService.class,
                    () -> new BrokerGossipAppService(brokers, gateways, store));
            context.register(BrokerRegistryEventListener.class, GatewayRegistryEventListener.class,
                    ConnectionRegistryEventListener.class);
            context.refresh();

            context.publishEvent(new BrokerRegisteredEvent("broker-a", "host-a", 18020, occurredOn));
            context.publishEvent(new BrokerHeartbeatEvent("broker-a", occurredOn));
            context.publishEvent(new BrokerRemovedEvent("broker-a", occurredOn));
            context.publishEvent(new GatewayRegisteredEvent("gateway-a", "host-b", 19000, "identity-a", occurredOn));
            context.publishEvent(new GatewayHeartbeatEvent("gateway-a", occurredOn));
            context.publishEvent(new GatewayRemovedEvent("gateway-a", occurredOn));
            context.publishEvent(new ConnectionRegisteredEvent(1L, "gateway-a", occurredOn));
            context.publishEvent(new ConnectionRemovedEvent(1L, "gateway-a", occurredOn));
            context.publishEvent(new ConnectionSyncedEvent("gateway-a", List.of(1L), removed, occurredOn));

            verify(store).putBrokerState("broker-a", "host-a", 18020);
            verify(brokers).find("broker-a");
            verify(store).removeBrokerState("broker-a");
            verify(store).putGatewayState("gateway-a", "host-b", 19000, "identity-a");
            verify(gateways).find(new GatewayId("gateway-a"));
            verify(store).removeGatewayState("gateway-a");
            verify(store).putConnectionState(1L, "gateway-a");
            verify(store).removeConnectionState(1L, "gateway-a");
            verify(store).updateConnectionSnapshot("gateway-a", List.of(1L), removed);
            verifyNoMoreInteractions(store);
        }
    }

    @Test
    void observationFailureDoesNotPreventLaterEventsFromBeingHandled() {
        BrokerGossipAdapter store = mock(BrokerGossipAdapter.class);
        BrokerGossipAppService service = new BrokerGossipAppService(
                mock(BrokerRegistry.class), mock(GatewayRegistry.class), store);
        BrokerRegistryEventListener listener = new BrokerRegistryEventListener(service);
        doThrow(new IllegalStateException("observation unavailable"))
                .when(store).putBrokerState("broker-a", "host-a", 18020);

        assertThatCode(() -> listener.onBrokerRegistered(new BrokerRegisteredEvent("broker-a", "host-a", 18020)))
                .doesNotThrowAnyException();
        listener.onBrokerRemoved(new BrokerRemovedEvent("broker-a"));

        verify(store).removeBrokerState("broker-a");
    }
}
