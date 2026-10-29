package com.co.kc.imchat.e2e;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.ImBrokerApplication;
import com.co.kc.imchat.gateway.ws.config.GatewayBeans;
import com.co.kc.imchat.gateway.ws.config.properties.GatewayProperties;
import com.co.kc.imchat.gateway.ws.lifecycle.RegistrationLifecycle;
import com.co.kc.imchat.gateway.ws.lifecycle.NettyServerLifecycle;
import com.co.kc.imchat.gateway.ws.routing.GatewayInstanceIdentity;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionCloseParams;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import java.util.Map;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
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
@ActiveProfiles("bolt-dev")
@Import(RealtimeMessagingE2ETest.BrokerTransport.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = ImBrokerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "IM_BOLT_SYNTHETIC_DATA=true",
                "im.bolt.server.host=127.0.0.1",
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
    private static final com.co.kc.imchat.plugin.bolt.server.BoltControlledServer[] BOLT_SERVERS = ownedBoltServers();
    private static final int BROKER_BOLT_PORT = BOLT_SERVERS[0].port();
    private static final Path BROKER_RAFT_DATA_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "im-chat-e2e-raft-" + UUID.randomUUID());
    private static final com.co.kc.imchat.plugin.bolt.server.BoltControlledServer[] GATEWAY_BOLT_SERVERS = {BOLT_SERVERS[1], BOLT_SERVERS[2]};
    private int gatewayIndex;
    private static final long AUTHENTICATED_USER_ID = 42L;

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        for (int index = 0; index < GATEWAY_BOLT_SERVERS.length; index++) {
            int fixedIndex = index;
            registry.add("im.broker.gateway-push.bolt.development-targets[" + index + "].address",
                    () -> "127.0.0.1:" + GATEWAY_BOLT_SERVERS[fixedIndex].port());
            registry.add("im.broker.gateway-push.bolt.development-targets[" + index + "].expected-peer-identity",
                    () -> "spiffe://im-chat/gateway/e2e-" + fixedIndex);
        }
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

    private ConfigurableApplicationContext gatewayContext;
    private String currentGatewayId;
    private WebSocket webSocket;
    private CollectingWebSocketListener listener;

    @AfterEach
    void stopGateway() {
        if (webSocket != null) {
            webSocket.abort();
        }
        if (gatewayContext != null) {
            gatewayContext.close();
            gatewayContext = null;
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
        startGatewayAndConnect();
        String firstGatewayId = currentGatewayId;

        assertThat(queryRoutes()).contains(firstGatewayId);
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

        FrameResponse pushed = new FrameResponse("1", FrameType.PUSH, "message.private", "push-1", "trace-push",
                0, "OK", Map.of("messageId", 2001));
        assertThat(brokerClient.writeFrame(BrokerFrameWriteParams.outbound(AUTHENTICATED_USER_ID, pushed)).acceptedConnectionIds()).hasSize(1);
        assertThat(listener.responses.poll(5, TimeUnit.SECONDS)).contains("push-1");
        brokerClient.closeConnections(new ConnectionCloseParams(AUTHENTICATED_USER_ID, "e2e-session"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(gatewayContext.getBean(ConnectionRegistry.class).activeConnectionIds()).isEmpty());
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(queryRoutes()).doesNotContain(firstGatewayId));

        gatewayContext.close();
        gatewayContext = null;
        startGatewayAndConnect();
        String secondGatewayId = currentGatewayId;

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(queryRoutes()).contains(secondGatewayId)
                        .doesNotContain(firstGatewayId));
    }

    private void startGatewayAndConnect() throws Exception {
        int webSocketPort = availablePort();
        com.co.kc.imchat.plugin.bolt.server.BoltControlledServer gatewayBoltServer = GATEWAY_BOLT_SERVERS[gatewayIndex++];
        int gatewayBoltPort = gatewayBoltServer.port();
        gatewayContext = new SpringApplicationBuilder(GatewayRuntime.class)
                .web(WebApplicationType.NONE).profiles("bolt-dev")
                .initializers(context -> ((org.springframework.context.support.GenericApplicationContext) context).registerBean("e2eOwnedGatewayBoltServer", com.alipay.remoting.rpc.RpcServer.class,
                        () -> gatewayBoltServer, definition -> definition.setDestroyMethodName("")))
                .run("--IM_BOLT_SYNTHETIC_DATA=true", "--jetcache.remote.default.type=mock", "--im.bolt.server.host=127.0.0.1",
                        "--im.bolt.server.port=" + gatewayBoltPort, "--im.gateway.ws.port=" + webSocketPort,
                        "--im.gateway.ws.bind-host=127.0.0.1", "--im.gateway.ws.bolt.host=127.0.0.1",
                        "--im.gateway.ws.broker.allow-trusted-network-identity=true",
                        "--im.gateway.ws.broker.bootstrap-targets[0].address=127.0.0.1:" + BROKER_BOLT_PORT,
                        "--im.gateway.ws.broker.bootstrap-targets[0].expected-peer-identity=spiffe://im-chat/broker/broker-local-1",
                        "--spring.cloud.nacos.discovery.enabled=false", "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.discovery.enabled=false", "--spring.cloud.discovery.client.composite-indicator.enabled=false",
                        "--spring.cloud.discovery.client.composite.enabled=false", "--im.dubbo.enabled=false", "--dubbo.enabled=false",
                        "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration");
        currentGatewayId = gatewayContext.getBean(GatewayInstanceIdentity.class).gatewayId();
        assertThat(gatewayContext.getBean(com.alipay.remoting.rpc.RpcClient.class))
                .isInstanceOf(BoltControlledClient.class);
        assertThat(gatewayContext.getBean(BoltControlledClient.class).configuredMode())
                .isEqualTo(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
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
                BrokerLoadBalance.HASH, 3_000,
                List.of(new BoltTarget("127.0.0.1:" + BROKER_BOLT_PORT, "spiffe://im-chat/broker/broker-local-1")));
    }

    private static WsAuthenticationManager authenticationManager() {
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

    private static com.co.kc.imchat.plugin.bolt.server.BoltControlledServer[] ownedBoltServers() {
        java.util.ArrayList<com.co.kc.imchat.plugin.bolt.server.BoltControlledServer> servers = new java.util.ArrayList<>();
        try {
            for (int index = 0; index < 3; index++) {
                com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties.Server options = new com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties.Server();
                options.setHost("127.0.0.1"); options.setPort(0);
                options.setMode(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
                options.getDevelopment().setSyntheticData(true);
                com.co.kc.imchat.plugin.bolt.server.BoltControlledServer server = new com.co.kc.imchat.plugin.bolt.server.BoltControlledServer(options);
                servers.add(server);
                if (!server.start()) { throw new IllegalStateException("Gateway fixture bind failed"); }
            }
            return servers.toArray(com.co.kc.imchat.plugin.bolt.server.BoltControlledServer[]::new);
        } catch (RuntimeException failure) {
            for (com.co.kc.imchat.plugin.bolt.server.BoltControlledServer server : servers) {
                try { server.stop(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void stopOwnedBoltServers() {
        for (com.co.kc.imchat.plugin.bolt.server.BoltControlledServer server : BOLT_SERVERS) {
            server.stop();
            assertThat(server.isStarted()).isFalse();
        }
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to allocate E2E port", exception);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class BrokerTransport {
        @Bean(destroyMethod = "")
        com.alipay.remoting.rpc.RpcServer brokerOwnedServer() { return BOLT_SERVERS[0]; }
    }

    /**
     * 正式 Gateway 装配；仅替换外部 Account Facade 和 Nacos 发现，不替换 Bolt/授权/注册/Netty生命周期。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration.class)
    @Import({GatewayBeans.class, GatewayProperties.class, NettyServerLifecycle.class, RegistrationLifecycle.class})
    static class GatewayRuntime {
        @Bean
        WsAuthenticationManager wsAuthenticationManager() { return authenticationManager(); }

        @Bean
        @Primary
        DiscoveryClient discoveryClient() {
            DiscoveryClient discovery = mock(DiscoveryClient.class);
            when(discovery.getInstances(ServiceName.IM_BROKER.value())).thenReturn(List.of(
                    new DefaultServiceInstance("broker-local-1", ServiceName.IM_BROKER.value(), "127.0.0.1", BROKER_BOLT_PORT, false)));
            return discovery;
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
