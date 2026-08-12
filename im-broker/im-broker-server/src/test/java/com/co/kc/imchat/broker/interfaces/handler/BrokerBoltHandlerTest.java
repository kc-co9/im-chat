package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayHeartbeatHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayRegisterHandler;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayUnregisterHandler;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltService;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.model.BrokerEvent;
import com.co.kc.imchat.broker.support.event.model.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrokerBoltHandlerTest {

    @Test
    void gatewayRegisterHandlerStoresGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        GatewayRegisterHandler handler = new GatewayRegisterHandler(gatewayRegistry, new NoopBrokerEventPublisher());
        GatewayRegisterParams command = new GatewayRegisterParams("gw-1", "10.0.0.8", 12201);

        Object response = handler.handle(JsonUtils.toJson(command));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.REGISTER_GATEWAY.operation());
        assertThat(gatewayRegistry.find("gw-1")).isPresent();
    }

    @Test
    void gatewayUnregisterHandlerRemovesGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        gatewayRegistry.register("gw-1", "10.0.0.8", 12201);
        GatewayUnregisterHandler handler = new GatewayUnregisterHandler(gatewayRegistry,
                new InMemoryConnectionRegistry(), new NoopBrokerEventPublisher());
        GatewayUnregisterParams command = new GatewayUnregisterParams("gw-1");

        Object response = handler.handle(JsonUtils.toJson(command));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.UNREGISTER_GATEWAY.operation());
        assertThat(gatewayRegistry.find("gw-1")).isEmpty();
    }

    @Test
    void gatewayUnregisterHandlerRemovesGatewayConnections() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        RecordingBrokerEventPublisher eventPublisher = new RecordingBrokerEventPublisher();
        gatewayRegistry.register("gw-1", "10.0.0.8", 12201);
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(2L, "gw-1");
        connectionRegistry.register(1L, "gw-2");
        GatewayUnregisterHandler handler = new GatewayUnregisterHandler(gatewayRegistry,
                connectionRegistry, eventPublisher);

        handler.handle(JsonUtils.toJson(new GatewayUnregisterParams("gw-1")));

        assertThat(connectionRegistry.find(1L))
                .extracting("gatewayId")
                .containsExactly("gw-2");
        assertThat(connectionRegistry.find(2L)).isEmpty();
        assertThat(eventPublisher.events())
                .filteredOn(ConnectionSyncedEvent.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    ConnectionSyncedEvent syncedEvent = (ConnectionSyncedEvent) event;
                    assertThat(syncedEvent.gatewayId()).isEqualTo("gw-1");
                    assertThat(syncedEvent.userIds()).isEmpty();
                    assertThat(syncedEvent.removedLocations())
                            .extracting("userId")
                            .containsExactlyInAnyOrder(1L, 2L);
                });
    }

    @Test
    void gatewayHeartbeatHandlerRefreshesGatewayEndpoint() throws Exception {
        InMemoryGatewayRegistry gatewayRegistry = new InMemoryGatewayRegistry();
        gatewayRegistry.register("gw-1", "10.0.0.8", 12201);
        GatewayEndpointDTO before = gatewayRegistry.find("gw-1").orElseThrow();
        GatewayHeartbeatHandler handler = new GatewayHeartbeatHandler(gatewayRegistry, new NoopBrokerEventPublisher());

        Object response = handler.handle(JsonUtils.toJson(new GatewayHeartbeatParams("gw-1")));

        assertThat(response).isNull();
        assertThat(handler.service()).isEqualTo(BrokerBoltService.GATEWAY.service());
        assertThat(handler.operation()).isEqualTo(BrokerBoltOperation.HEARTBEAT_GATEWAY.operation());
        assertThat(gatewayRegistry.find("gw-1"))
                .get()
                .extracting(endpoint -> endpoint.lastSeenAt().isAfter(before.lastSeenAt()))
                .isEqualTo(true);
    }

    @Test
    void frameProcessHandlerReturnsBrokerFrameResponse() throws Exception {
        RecordingFrameProcessHandler handler = new RecordingFrameProcessHandler();
        handler.processFrameResponse = BrokerFrameWriteResult.ok();
        BrokerFrameWriteParams command = BrokerFrameWriteParams.inbound(1L, "conn-1",
                new FrameRequest("1", "message.private.send", "1", "trace", Map.of()));

        Object response = handler.handle(JsonUtils.toJson(command));

        assertThat(response).isEqualTo(BrokerFrameWriteResult.ok());
    }

    private static class RecordingFrameProcessHandler extends FrameProcessHandler {
        private BrokerFrameWriteResult processFrameResponse;

        RecordingFrameProcessHandler() {
            super(new InMemoryConnectionRegistry(),
                    new InMemoryGatewayRegistry(),
                    unusedRemoteService(MessageService.class),
                    new GatewayClient(unusedBoltInvoker(), 1),
                    mock(BrokerConnectionService.class),
                    mock(BrokerPeerClient.class));
        }

        @Override
        protected BrokerFrameWriteResult process(BrokerFrameWriteParams command) {
            return processFrameResponse;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedRemoteService(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("unused remote service");
                });
    }

    private static BoltInvoker unusedBoltInvoker() {
        return new BoltInvoker() {
            @Override
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                throw new UnsupportedOperationException("unused bolt invoker");
            }
        };
    }

    private static class RecordingBrokerEventPublisher implements BrokerEventPublisher {
        private final List<BrokerEvent> events = new ArrayList<>();

        @Override
        public void publish(BrokerEvent event) {
            events.add(event);
        }

        @Override
        public void publish(List<BrokerEvent> eventList) {
            events.addAll(eventList);
        }

        private List<BrokerEvent> events() {
            return events;
        }
    }
}
