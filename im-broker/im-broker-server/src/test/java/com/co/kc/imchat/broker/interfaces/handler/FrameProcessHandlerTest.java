package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.memory.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.registry.gateway.GatewayRegistry;
import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.co.kc.imchat.service.message.facade.params.GroupMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageSendParams;
import com.co.kc.imchat.service.message.facade.params.NotificationAckParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("realtime-behavior")
class FrameProcessHandlerTest {
    private static final String BROKER_1_HOST = "10.0.0.1";
    private static final String BROKER_2_HOST = "10.0.0.2";
    private static final int BROKER_PORT = 12200;
    private static final String BROKER_1_ID = brokerId(BROKER_1_HOST, BROKER_PORT);
    private static final String BROKER_2_ID = brokerId(BROKER_2_HOST, BROKER_PORT);

    @Test
    void forwardsGatewayFrameToMessageService() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        RecordingMessageService messageService = new RecordingMessageService();
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 1L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", "hello"))));

        assertTrue(response.processed());
        assertEquals(new PrivateMessageSendParams(1L, 101L, "token-1", "TEXT", "hello"),
                messageService.privateMessageSendRequest);
    }

    @Test
    void usesConnectionUserIdInsteadOfFrameBodyUserId() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        RecordingMessageService messageService = new RecordingMessageService();
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(7L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 999L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", "hello"))));

        assertTrue(response.processed());
        assertEquals(new PrivateMessageSendParams(7L, 101L, "token-1", "TEXT", "hello"),
                messageService.privateMessageSendRequest);
    }

    @Test
    void forwardsGatewayCommandWithBusinessFieldsMissing() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        RecordingMessageService messageService = new RecordingMessageService();
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.read",
                Map.of("userId", 1L, "chatId", 101L))));

        assertTrue(response.processed());
        assertEquals(new PrivateMessageReadParams(1L, 101L, null), messageService.privateMessageReadRequest);
    }

    @Test
    void returnsBusinessFailureWhenMessageServiceRejectsCommand() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        RecordingMessageService messageService = new RecordingMessageService();
        messageService.privateMessageSendFailure = new BusinessException(HttpErrorCode.OPERATE_ERROR, "消息内容不能为空");
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 1L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", ""))));

        assertEquals(false, response.processed());
        assertEquals(String.valueOf(HttpErrorCode.OPERATE_ERROR.getCode()), response.code());
        assertEquals("消息内容不能为空", response.message());
    }

    @Test
    void returnsBrokerFailureWhenMessageServiceIsUnavailable() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        RecordingMessageService messageService = new RecordingMessageService();
        messageService.privateMessageSendFailure = new IllegalStateException("dubbo timeout");
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 1L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", "hello"))));

        assertEquals(false, response.processed());
        assertEquals("BROKER_UNAVAILABLE", response.code());
    }

    @Test
    void pushesMessageToAllUserConnections() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register("gw-1", "10.0.0.1", 12201);
        gatewayRegistry.register("gw-2", "10.0.0.2", 12201);
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(1L, "gw-2");
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, gatewayRegistry, new RecordingMessageService(),
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(
                        List.of("gw-1".equals(connection.gatewayId()) ? "conn-1" : "conn-2"), List.of())));

        BrokerFrameWriteResult response = process(handler,
                BrokerFrameWriteParams.outbound(1L, pushFrame("message.private.sent")));

        assertEquals(List.of(
                BrokerFrameWriteDTO.accepted("conn-1"),
                BrokerFrameWriteDTO.accepted("conn-2")), response.writeList().stream()
                .sorted(java.util.Comparator.comparing(BrokerFrameWriteDTO::connectionId))
                .toList());
        assertEquals(List.of("conn-1", "conn-2"), response.acceptedConnectionIds().stream().sorted().toList());
    }

    @Test
    void sendsUserFrameOncePerGateway() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register("gw-1", "10.0.0.1", 12201);
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(1L, "gw-1");
        List<Long> pushedUserIds = new ArrayList<>();
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, gatewayRegistry, new RecordingMessageService(), gatewayFrameClient((connection, request) -> {
            pushedUserIds.add(request.userId());
            return new GatewayFrameWriteResult(List.of("conn-1", "conn-2"), List.of());
        }));

        BrokerFrameWriteResult response = process(handler,
                BrokerFrameWriteParams.outbound(1L, pushFrame("message.private.sent")));

        assertEquals(List.of(1L), pushedUserIds);
        assertEquals(List.of(
                BrokerFrameWriteDTO.accepted("conn-1"),
                BrokerFrameWriteDTO.accepted("conn-2")), response.writeList());
        assertEquals(List.of("conn-1", "conn-2"), response.acceptedConnectionIds());
    }

    @Test
    void continuesPushingOtherConnectionsWhenOneGatewayFails() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register("gw-1", "10.0.0.1", 12201);
        gatewayRegistry.register("gw-2", "10.0.0.2", 12201);
        connectionRegistry.register(1L, "gw-1");
        connectionRegistry.register(1L, "gw-2");
        FrameProcessHandler handler = frameProcessHandler(connectionRegistry, gatewayRegistry, new RecordingMessageService(), gatewayFrameClient((connection, request) -> {
            if ("gw-1".equals(connection.gatewayId())) {
                throw new IllegalStateException("gateway unavailable");
            }
            return new GatewayFrameWriteResult(List.of("conn-2"), List.of());
        }));

        BrokerFrameWriteResult response = process(handler,
                BrokerFrameWriteParams.outbound(1L, pushFrame("message.private.sent")));

        assertEquals(List.of(
                BrokerFrameWriteDTO.accepted("conn-2")), response.writeList().stream()
                .sorted(java.util.Comparator.comparing(BrokerFrameWriteDTO::connectionId))
                .toList());
        assertEquals(List.of("conn-2"), response.acceptedConnectionIds());
        assertEquals(List.of(), response.failedConnectionIds());
    }

    @Test
    void forwardsOutboundFrameToOwnerBroker() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerEndpointDTO owner = ownerBrokerForUser(brokerRegistry, 1L);
        BrokerPeerClient brokerPeerClient = mock(BrokerPeerClient.class);
        BrokerFrameWriteParams params = BrokerFrameWriteParams.outbound(1L, pushFrame("message.private.sent"));
        BrokerFrameWriteResult expectedResult = BrokerFrameWriteResult.writeResult(1L,
                List.of(BrokerFrameWriteDTO.accepted("conn-1")));
        when(brokerPeerClient.writeFrame(owner, params)).thenReturn(expectedResult);
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(registries.connectionRegistry(),
                brokerRegistry, brokerProperties("127.0.0.1", BROKER_PORT), brokerPeerClient,
                new NoopBrokerEventPublisher());
        FrameProcessHandler handler = frameProcessHandler(registries.connectionRegistry(), registries.gatewayRegistry(),
                new RecordingMessageService(), gatewayFrameClient((connection, request) -> {
                    throw new UnsupportedOperationException("gateway should not be called");
                }), brokerConnectionService, brokerPeerClient);

        BrokerFrameWriteResult response = process(handler, params);

        assertEquals(expectedResult, response);
        verify(brokerPeerClient).writeFrame(owner, params);
    }

    private FrameRequest frame(String cmd) {
        return new FrameRequest("1", cmd, "1", "trace", Map.of());
    }

    private BrokerFrameWriteResult process(FrameProcessHandler handler, BrokerFrameWriteParams params) throws Exception {
        return (BrokerFrameWriteResult) handler.handle(JsonUtils.toJson(params));
    }

    private FrameRequest frame(String cmd, Map<String, Object> body) {
        return new FrameRequest("1", cmd, "1", "trace", body);
    }

    private FrameResponse pushFrame(String cmd) {
        return new FrameResponse("1", FrameType.PUSH, cmd, "1", "trace", null, null, Map.of());
    }

    private GatewayClient gatewayFrameClient(FrameWriteStub frameWriteStub) {
        return new GatewayClient(unusedBoltInvoker(), 1) {
            @Override
            public GatewayFrameWriteResult writeFrame(GatewayEndpointDTO connection, GatewayFrameWriteParams request) {
                return frameWriteStub.writeFrame(connection, request);
            }
        };
    }

    private FrameProcessHandler frameProcessHandler(ConnectionRegistry connectionRegistry,
                                                    GatewayRegistry gatewayRegistry,
                                                    MessageService messageService,
                                                    GatewayClient gatewayClient) {
        BrokerPeerClient brokerPeerClient = mock(BrokerPeerClient.class);
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(
                connectionRegistry, new InMemoryBrokerRegistry(),
                brokerProperties("127.0.0.1", BROKER_PORT), brokerPeerClient, new NoopBrokerEventPublisher());
        return new FrameProcessHandler(connectionRegistry, gatewayRegistry, messageService, gatewayClient,
                brokerConnectionService, brokerPeerClient);
    }

    private FrameProcessHandler frameProcessHandler(ConnectionRegistry connectionRegistry,
                                                    GatewayRegistry gatewayRegistry,
                                                    MessageService messageService,
                                                    GatewayClient gatewayClient,
                                                    BrokerConnectionService brokerConnectionService,
                                                    BrokerPeerClient brokerPeerClient) {
        return new FrameProcessHandler(connectionRegistry, gatewayRegistry, messageService, gatewayClient,
                brokerConnectionService, brokerPeerClient);
    }

    private BoltInvoker unusedBoltInvoker() {
        return new BoltInvoker() {
            @Override
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                throw new UnsupportedOperationException("unused bolt invoker");
            }
        };
    }

    private TestRegistries testRegistries() {
        return new TestRegistries(new InMemoryConnectionRegistry(), new InMemoryGatewayRegistry());
    }

    private BrokerEndpointDTO ownerBrokerForUser(InMemoryBrokerRegistry brokerRegistry, Long userId) {
        brokerRegistry.register(BROKER_1_ID, BROKER_1_HOST, BROKER_PORT);
        brokerRegistry.register(BROKER_2_ID, BROKER_2_HOST, BROKER_PORT);
        return brokerRegistry.list().stream()
                .filter(broker -> broker.brokerId().equals(expectedBrokerId(userId)))
                .findFirst()
                .orElseThrow();
    }

    private String expectedBrokerId(Long userId) {
        return Math.floorMod(String.valueOf(userId).hashCode(), 2) == 0 ? BROKER_1_ID : BROKER_2_ID;
    }

    private BrokerProperties brokerProperties(String host, int port) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost(host);
        properties.getInstance().setPort(port);
        return properties;
    }

    private static String brokerId(String host, int port) {
        return "broker-" + host + "-" + port;
    }

    private record TestRegistries(InMemoryConnectionRegistry connectionRegistry,
                                  InMemoryGatewayRegistry gatewayRegistry) {
    }

    @FunctionalInterface
    private interface FrameWriteStub {
        GatewayFrameWriteResult writeFrame(GatewayEndpointDTO connection, GatewayFrameWriteParams request);
    }

    private static class RecordingMessageService implements MessageService {
        private PrivateMessageSendParams privateMessageSendRequest;
        private PrivateMessageReadParams privateMessageReadRequest;
        private RuntimeException privateMessageSendFailure;

        @Override
        public void sendPrivateMessage(PrivateMessageSendParams request) {
            if (privateMessageSendFailure != null) {
                throw privateMessageSendFailure;
            }
            privateMessageSendRequest = request;
        }

        @Override
        public void readPrivateMessage(PrivateMessageReadParams request) {
            privateMessageReadRequest = request;
        }

        @Override
        public void revokePrivateMessage(PrivateMessageRevokeParams request) {
        }

        @Override
        public void sendGroupMessage(GroupMessageSendParams request) {
        }

        @Override
        public void readGroupMessage(GroupMessageReadParams request) {
        }

        @Override
        public void revokeGroupMessage(GroupMessageRevokeParams request) {
        }

        @Override
        public void ackNotification(NotificationAckParams request) {
        }
    }
}
