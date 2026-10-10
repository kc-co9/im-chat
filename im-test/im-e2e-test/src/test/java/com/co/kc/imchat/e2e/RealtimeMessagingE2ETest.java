package com.co.kc.imchat.e2e;

import com.co.kc.imchat.broker.ImBrokerApplication;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.gateway.ws.server.NettyWebSocketServer;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.co.kc.imchat.service.message.facade.params.NotificationAckParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Tag("e2e")
@SpringBootTest(classes = ImBrokerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "im.dubbo.enabled=false",
                "dubbo.enabled=false",
                "im.dubbo.registry.address=N/A",
                "dubbo.registry.address=N/A",
                "jetcache.remote.default.type=mock",
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
class RealtimeMessagingE2ETest {
    private static final int BROKER_BOLT_PORT = availablePort();
    private static final Path BROKER_RAFT_DATA_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "im-chat-e2e-raft-" + UUID.randomUUID());
    private static final String FIRST_GATEWAY_ID = "e2e-gateway-1";
    private static final String SECOND_GATEWAY_ID = "e2e-gateway-2";
    private static final long AUTHENTICATED_USER_ID = 42L;

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("im.bolt.server.port", () -> BROKER_BOLT_PORT);
        registry.add("im.broker.instance.port", () -> BROKER_BOLT_PORT);
        registry.add("im.broker.raft.data-directory", BROKER_RAFT_DATA_DIRECTORY::toString);
        registry.add("im.broker.raft.bootstrap-brokers[0].broker-id", () -> "broker-local-1");
        registry.add("im.broker.raft.bootstrap-brokers[0].route-endpoint",
                () -> "127.0.0.1:" + BROKER_BOLT_PORT);
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-role", () -> "VOTER");
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-endpoint", () -> "127.0.0.1:18120");
        registry.add("im.broker.raft.bootstrap-brokers[0].routing-state", () -> "ACTIVE");
    }

    @LocalServerPort
    private int brokerManagementPort;

    @Autowired
    private BoltInvoker boltInvoker;

    @MockitoBean
    private MessageService messageService;

    private NettyWebSocketServer gatewayServer;
    private WebSocket webSocket;
    private CollectingWebSocketListener listener;

    @AfterEach
    void stopGateway() {
        if (webSocket != null) {
            webSocket.abort();
        }
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
    }

    @Test
    void routesAuthenticatedFramesAndRestoresRouteAfterGatewayRestart() throws Exception {
        CompletableFuture<PrivateMessageSendParams> sentMessage = new CompletableFuture<>();
        CompletableFuture<NotificationAckParams> acknowledgedNotification = new CompletableFuture<>();
        reset(messageService);
        doAnswer(invocation -> sentMessage.complete(invocation.getArgument(0)))
                .when(messageService).sendPrivateMessage(any());
        doAnswer(invocation -> acknowledgedNotification.complete(invocation.getArgument(0)))
                .when(messageService).ackNotification(any());

        BrokerClient brokerClient = brokerClient();
        startGatewayAndConnect(brokerClient, FIRST_GATEWAY_ID);

        assertThat(queryRoutes()).contains(FIRST_GATEWAY_ID);
        sendAndAwaitResponse("send-1", """
                {"version":"1","cmd":"message.private.send","seq":"send-1","traceId":"trace-send",\
                "body":{"userId":999,"chatId":1001,"messageToken":"token-1",\
                "messageType":"TEXT","messageContent":"hello"}}
                """);
        assertThat(sentMessage.get(5, TimeUnit.SECONDS).userId()).isEqualTo(AUTHENTICATED_USER_ID);

        sendAndAwaitResponse("ack-1", """
                {"version":"1","cmd":"notification.ack","seq":"ack-1","traceId":"trace-ack",\
                "body":{"userId":999,"chatId":1001,"messageId":2001,"receiptType":"PRIVATE_SENT"}}
                """);
        assertThat(acknowledgedNotification.get(5, TimeUnit.SECONDS).userId())
                .isEqualTo(AUTHENTICATED_USER_ID);

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "e2e disconnect").get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(queryRoutes()).doesNotContain(FIRST_GATEWAY_ID));

        gatewayServer.stop();
        gatewayServer = null;
        startGatewayAndConnect(brokerClient, SECOND_GATEWAY_ID);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(queryRoutes()).contains(SECOND_GATEWAY_ID)
                        .doesNotContain(FIRST_GATEWAY_ID));
    }

    private void startGatewayAndConnect(BrokerClient brokerClient, String gatewayId) throws Exception {
        int webSocketPort = availablePort();
        brokerClient.registerGateway(new GatewayRegisterParams(gatewayId, "127.0.0.1", availablePort()));
        gatewayServer = new NettyWebSocketServer(
                "127.0.0.1", webSocketPort, gatewayId, "/ws", brokerClient, new ConnectionRegistry(),
                authenticationManager(), 30, 65_536);
        gatewayServer.start();
        AtomicReference<WebSocket> connected = new AtomicReference<>();
        AtomicReference<CollectingWebSocketListener> connectedListener = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    CollectingWebSocketListener candidate = new CollectingWebSocketListener();
                    WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(2))
                            .buildAsync(URI.create("ws://127.0.0.1:" + webSocketPort
                                    + "/ws?token=valid-token"), candidate)
                            .get(3, TimeUnit.SECONDS);
                    connectedListener.set(candidate);
                    connected.set(socket);
                });
        listener = connectedListener.get();
        webSocket = connected.get();
    }

    private BrokerClient brokerClient() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        when(discoveryClient.getInstances(ServiceName.IM_BROKER.value())).thenReturn(List.of(
                new DefaultServiceInstance("e2e-broker", ServiceName.IM_BROKER.value(),
                        "127.0.0.1", BROKER_BOLT_PORT, false)));
        return new BrokerClient(boltInvoker, discoveryClient, ServiceName.IM_BROKER,
                BrokerLoadBalance.HASH, 3_000);
    }

    private WsAuthenticationManager authenticationManager() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams("valid-token")))
                .thenReturn(new SessionAuthDTO(AUTHENTICATED_USER_ID, "e2e-session",
                        Instant.now().plus(Duration.ofMinutes(5))));
        return new WsAuthenticationManager(accountService, Runnable::run, Duration.ofSeconds(1));
    }

    private String queryRoutes() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + brokerManagementPort
                                + "/management/connections?userId=" + AUTHENTICATED_USER_ID))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private void sendAndAwaitResponse(String expectedSequence, String payload) throws Exception {
        webSocket.sendText(payload, true).get(5, TimeUnit.SECONDS);
        String response = listener.responses.poll(5, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response).contains("\"type\":\"response\"");
        assertThat(response).contains("\"seq\":\"" + expectedSequence + "\"");
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to allocate E2E port", exception);
        }
    }

    private static final class CollectingWebSocketListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> responses = new LinkedBlockingQueue<>();
        private final StringBuilder currentText = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public synchronized CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            currentText.append(data);
            if (last) {
                responses.add(currentText.toString());
                currentText.setLength(0);
            }
            socket.request(1);
            return null;
        }
    }
}
