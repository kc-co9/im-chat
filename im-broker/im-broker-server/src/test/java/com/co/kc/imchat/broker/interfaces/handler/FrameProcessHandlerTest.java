package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;

import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.broker.domain.connection.model.ConnectionRouteId;
import com.co.kc.imchat.broker.domain.connection.model.RouteRevision;
import java.time.Instant;

import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.result.RouteBrokerResult;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.broker.application.frame.FrameDispatchAppService;
import com.co.kc.imchat.broker.support.RouteLookupStub;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.common.exception.BusinessException;
import com.co.kc.imchat.common.exception.RpcException;
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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("realtime-behavior")
class FrameProcessHandlerTest {
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
    void preservesStableRpcFailureCodeFromMessageService() throws Exception {
        TestRegistries registries = testRegistries();
        RecordingMessageService messageService = new RecordingMessageService();
        messageService.privateMessageSendFailure = new RpcException(
                HttpErrorCode.BUSY_ERROR.getCode(), HttpErrorCode.BUSY_ERROR.getMsg());
        FrameProcessHandler handler = frameProcessHandler(
                registries.connectionRegistry(), registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 1L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", "hello"))));

        assertEquals(false, response.processed());
        assertEquals(String.valueOf(HttpErrorCode.BUSY_ERROR.getCode()), response.code());
        assertEquals(HttpErrorCode.BUSY_ERROR.getMsg(), response.message());
    }

    @Test
    void unwrapsStableRpcFailureFromDubboRuntimeWrapper() throws Exception {
        TestRegistries registries = testRegistries();
        RecordingMessageService messageService = new RecordingMessageService();
        messageService.privateMessageSendFailure = new RuntimeException(new RpcException(
                HttpErrorCode.REPEATED_ERROR.getCode(), HttpErrorCode.REPEATED_ERROR.getMsg()));
        FrameProcessHandler handler = frameProcessHandler(
                registries.connectionRegistry(), registries.gatewayRegistry(), messageService,
                gatewayFrameClient((connection, request) -> new GatewayFrameWriteResult(List.of(), List.of())));

        BrokerFrameWriteResult response = process(handler, BrokerFrameWriteParams.inbound(1L, "conn-1", frame(
                "message.private.send",
                Map.of("userId", 1L, "chatId", 101L, "messageToken", "token-1",
                        "messageType", "TEXT", "messageContent", "hello"))));

        assertEquals(false, response.processed());
        assertEquals(String.valueOf(HttpErrorCode.REPEATED_ERROR.getCode()), response.code());
        assertEquals(HttpErrorCode.REPEATED_ERROR.getMsg(), response.message());
    }

    @Test
    void pushesMessageToAllUserConnections() throws Exception {
        TestRegistries registries = testRegistries();
        InMemoryConnectionRegistry connectionRegistry = registries.connectionRegistry();
        InMemoryGatewayRegistry gatewayRegistry = registries.gatewayRegistry();
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.1", 12201, "test-identity"));
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-2", "10.0.0.2", 12201, "test-identity"));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));
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
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.1", 12201, "test-identity"));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
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
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "10.0.0.1", 12201, "test-identity"));
        gatewayRegistry.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-2", "10.0.0.2", 12201, "test-identity"));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-1")), new RouteRevision(0L), Instant.MAX));
        connectionRegistry.register(ConnectionRegistration.active(new ConnectionRouteId(new UserId(1L), new GatewayId("gw-2")), new RouteRevision(0L), Instant.MAX));
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

    private FrameRequest frame(String cmd) {
        return new FrameRequest("1", cmd, "1", "trace", Map.of());
    }

    private BrokerFrameWriteResult process(FrameProcessHandler handler, BrokerFrameWriteParams params) throws Exception {
        BrokerFrameWriteParams routed = params.withRouteContext(new RouteRequestContext(
                BrokerRequestChainTestSupport.EPOCH, "frame-1",
                System.currentTimeMillis() + 10_000L));
        RouteBrokerResult result = (RouteBrokerResult) BrokerRequestChainTestSupport.acceptingChain(handler)
                .handle(null, JsonUtils.toJson(routed));
        return JsonUtils.convertValue(result.value(), BrokerFrameWriteResult.class);
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
        RouteLookupStub unusedLookupClient = (candidate, targetUserId, routingEpoch) ->
                java.util.concurrent.CompletableFuture.completedFuture(List.of());
        BrokerClusterConsensus repository = org.mockito.Mockito.mock(
                BrokerClusterConsensus.class);
        org.mockito.Mockito.when(repository.committedState()).thenReturn(new BrokerClusterState(
                7L, BrokerRequestChainTestSupport.EPOCH,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode(BrokerRequestChainTestSupport.BROKER_ID,
                        "127.0.0.1:12200", BrokerMembershipRole.VOTER,
                        BrokerRoutingState.ACTIVE))));
        RegistryProperties registryProperties = new RegistryProperties();
        BrokerClusterAdapter adapter = unusedLookupClient.adapter(gatewayClient);
        org.mockito.Mockito.when(adapter.committedState()).thenAnswer(ignored -> repository.committedState());
        org.mockito.Mockito.when(adapter.localBrokerId()).thenReturn(new BrokerId(BrokerRequestChainTestSupport.BROKER_ID));
        FrameDispatchAppService deliveryService = new FrameDispatchAppService(
                connectionRegistry, gatewayRegistry, adapter,
                registryProperties.getGatewayTtl(), Duration.ZERO,
                RouteLookupStub.negativeCache(Duration.ZERO));
        return new FrameProcessHandler(messageService, deliveryService);
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
