package com.co.kc.imchat.e2e;

import com.co.kc.imchat.broker.ImBrokerApplication;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.adapter.BrokerClusterOperationAdapter;
import com.co.kc.imchat.management.ops.application.BrokerClusterOperationAppService;
import com.co.kc.imchat.management.ops.infrastructure.discovery.BrokerDiscovery;
import com.co.kc.imchat.management.ops.interfaces.http.OpsCliController;
import com.co.kc.imchat.management.ops.model.io.OpsCliSubmissionResponse;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClientException;
import com.co.kc.imchat.management.ops.infrastructure.client.HttpBrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.infrastructure.config.beans.OpsHttpBeans;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.co.kc.imchat.service.message.facade.MessageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** 真实 Ops 出站客户端 → Broker HTTP/Raft；仅替换外部设施，不启动 Ops 入站运行时。 */
@Tag("e2e")
@ActiveProfiles("bolt-dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = ImBrokerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "IM_BOLT_SYNTHETIC_DATA=true",
                "im.iam.enabled=false",
                "im.audit.enabled=false",
                "im.datasource.sharding.enabled=false",
                "im.identity.snowflake.mode=STATIC",
                "im.identity.snowflake.data-center-id=0",
                "im.identity.snowflake.machine-id=1",
                "im.bolt.server.host=127.0.0.1",
                "im.dubbo.enabled=false",
                "dubbo.enabled=false",
                "im.dubbo.registry.address=N/A",
                "dubbo.registry.address=N/A",
                "jetcache.remote.default.type=mock",
                "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
class OpsBrokerManagementContractE2ETest {
    private static final String BROKER_ID = "broker-local-1";
    private static final String LEARNER_ID = "contract-learner";
    private static final String WRITE_SCOPE = "ops:broker:raft:write";
    private static final String SERVICE_CREDENTIAL = UUID.randomUUID().toString();
    private static final int RAFT_PORT = availablePort();
    private static final int BOLT_PORT = availablePort();
    private static final String LEARNER_ENDPOINT = "127.0.0.1:" + availablePort();
    private static final Path RAFT_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"),
            "im-chat-t9-ops-contract-" + UUID.randomUUID());

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        String raftEndpoint = "127.0.0.1:" + RAFT_PORT;
        registry.add("im.bolt.server.port", () -> BOLT_PORT);
        registry.add("im.broker.instance.port", () -> BOLT_PORT);
        registry.add("im.broker.raft.endpoint", () -> raftEndpoint);
        registry.add("im.broker.raft.initial-voters[0]", () -> raftEndpoint);
        registry.add("im.broker.raft.data-directory", RAFT_DIRECTORY::toString);
        registry.add("im.broker.management.write-token", () -> SERVICE_CREDENTIAL);
        registry.add("im.broker.raft.bootstrap-brokers[0].broker-id", () -> BROKER_ID);
        registry.add("im.broker.raft.bootstrap-brokers[0].route-endpoint", () -> "127.0.0.1:" + BOLT_PORT);
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-endpoint", () -> raftEndpoint);
        registry.add("im.broker.raft.bootstrap-brokers[0].membership-role", () -> "VOTER");
        registry.add("im.broker.raft.bootstrap-brokers[0].routing-state", () -> "ACTIVE");
        registry.add("im.broker.raft.bootstrap-brokers[1].broker-id", () -> LEARNER_ID);
        registry.add("im.broker.raft.bootstrap-brokers[1].route-endpoint", () -> LEARNER_ENDPOINT);
        registry.add("im.broker.raft.bootstrap-brokers[1].membership-role", () -> "DISCOVERED");
        registry.add("im.broker.raft.bootstrap-brokers[1].routing-state", () -> "INACTIVE");
    }

    @LocalServerPort
    private int managementPort;

    @Autowired
    private BrokerRaftAdapter raftAdapter;

    @MockitoBean
    private MessageService messageService;

    private HttpBrokerManagementClient client;
    private BrokerManagementEndpoint endpoint;
    private BrokerClusterOperationCommand acceptedCommand;
    private BrokerOperationPayload accepted;
    private long initialEpoch;

    @BeforeAll
    void joinOfflineLearnerThroughRealHttp() {
        client = clientWithCredential(SERVICE_CREDENTIAL);
        endpoint = new BrokerManagementEndpoint(BROKER_ID,
                URI.create("http://127.0.0.1:" + managementPort), null);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(raftAdapter.getLocalBroker().isLeader()).isTrue());
        initialEpoch = client.clusterState(endpoint).epoch();
        acceptedCommand = command("add-learner", "accepted-" + UUID.randomUUID(), initialEpoch);
        accepted = client.operate(endpoint, acceptedCommand, WRITE_SCOPE);
        assertThat(accepted.status()).isEqualTo("APPLIED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(client.queryOperation(endpoint, acceptedCommand.operationId()).phase()).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void clusterReadDecodesCommittedBrokerProjection() {
        BrokerClusterStatePayload state = client.clusterState(endpoint);
        assertThat(state.revision()).isPositive();
        assertThat(state.epoch()).isEqualTo(initialEpoch);
        assertThat(state.algorithm()).isEqualTo("RENDEZVOUS_HASH");
        assertThat(state.brokers()).anySatisfy(broker -> {
            assertThat(broker.brokerId()).isEqualTo(BROKER_ID);
            assertThat(broker.routeEndpoint()).isEqualTo("127.0.0.1:" + BOLT_PORT);
            assertThat(broker.membershipRole()).isEqualTo("VOTER");
            assertThat(broker.routingState()).isEqualTo("ACTIVE");
        }).anySatisfy(broker -> {
            assertThat(broker.brokerId()).isEqualTo(LEARNER_ID);
            assertThat(broker.membershipRole()).isEqualTo("LEARNER_SYNCING");
            assertThat(broker.routingState()).isEqualTo("INACTIVE");
        });
    }

    @Test
    void unknownOperationRemainsClientFailureRatherThanInventedOperation() {
        assertThatThrownBy(() -> client.queryOperation(endpoint, "unknown-" + UUID.randomUUID()))
                .isInstanceOf(BrokerManagementClientException.class)
                .hasMessage("参数错误,请稍后重试");
    }

    @ParameterizedTest
    @ValueSource(strings = {"add-learner", "complete-learner-sync", "promote", "demote", "remove"})
    void mismatchedEpochReachesEveryRealMembershipHandlerWithoutMutation(String action) {
        BrokerClusterStatePayload before = client.clusterState(endpoint);
        String operationId = "stale-" + action + "-" + UUID.randomUUID();
        BrokerOperationPayload result = client.operate(endpoint,
                command(action, operationId, before.epoch() + 1), WRITE_SCOPE);
        assertThat(result.operationId()).isEqualTo(operationId);
        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(result.message()).contains("epoch");
        assertThat(client.clusterState(endpoint)).isEqualTo(before);
        assertThatThrownBy(() -> client.queryOperation(endpoint, operationId))
                .isInstanceOf(BrokerManagementClientException.class)
                .hasMessage("参数错误,请稍后重试");
    }

    @ParameterizedTest
    @ValueSource(strings = {"promote", "demote", "remove"})
    void cliControllerPreservesEndpointThroughRealBrokerHandler(String action) {
        BrokerDiscovery discovery = mock(BrokerDiscovery.class);
        when(discovery.discover()).thenReturn(List.of(endpoint));
        OpsCliController controller = new OpsCliController(new BrokerClusterOperationAppService(
                new BrokerClusterOperationAdapter(discovery, client)));
        BrokerClusterStatePayload before = client.clusterState(endpoint);
        String operationId = "cli-contract-" + action + "-" + UUID.randomUUID();
        OpsCliController.MembershipOperationRequest request = new OpsCliController.MembershipOperationRequest(
                operationId, before.epoch() + 1, LEARNER_ENDPOINT, "contract test");

        OpsCliSubmissionResponse result = switch (action) {
            case "promote" -> controller.promote(LEARNER_ID, request);
            case "demote" -> controller.demote(LEARNER_ID, request);
            case "remove" -> controller.remove(LEARNER_ID, request);
            default -> throw new IllegalArgumentException(action);
        };

        assertThat(result.operationId()).isEqualTo(operationId);
        assertThat(result.status()).isEqualTo("CONFLICT");
        assertThat(client.clusterState(endpoint)).isEqualTo(before);
    }

    @Test
    void acceptedOperationQueryAndIdempotentReplayPreserveCommittedFacts() throws IOException {
        BrokerOperationPayload.Query operation = client.queryOperation(endpoint, acceptedCommand.operationId());
        assertThat(operation.operationId()).isEqualTo(acceptedCommand.operationId());
        assertThat(operation.type()).isEqualTo("START_LEARNER_SYNC");
        assertThat(operation.targetBrokerId()).isEqualTo(LEARNER_ID);
        assertThat(operation.phase()).isEqualTo("SUCCEEDED");
        assertThat(operation.acceptedEpoch()).isEqualTo(initialEpoch);
        assertThat(operation.resultEpoch()).isEqualTo(initialEpoch);
        assertThat(operation.createdAtMs()).isPositive();
        assertThat(Instant.ofEpochMilli(operation.createdAtMs())).isBeforeOrEqualTo(Instant.now());
        assertThat(operation.completedAtMs()).isGreaterThanOrEqualTo(operation.createdAtMs());
        assertThat(operation.error()).isNull();
        BrokerClusterStatePayload before = client.clusterState(endpoint);
        BrokerOperationPayload replay = client.operate(endpoint, acceptedCommand, WRITE_SCOPE);
        assertThat(replay.operationId()).isEqualTo(accepted.operationId());
        assertThat(replay.status()).isEqualTo("DUPLICATE");
        assertThat(client.queryOperation(endpoint, acceptedCommand.operationId())).isEqualTo(operation);
        assertThat(client.clusterState(endpoint)).isEqualTo(before);
        try (Stream<Path> files = Files.walk(RAFT_DIRECTORY)) {
            assertThat(files.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".log")))
                    .as("Real Raft log storage must exist in the task-owned directory").isTrue();
        }
    }

    @Test
    void invalidServiceCredentialIsRejectedOverLiveHttpWithoutMutation() {
        BrokerClusterStatePayload before = client.clusterState(endpoint);
        String operationId = "unauthorized-" + UUID.randomUUID();
        HttpBrokerManagementClient unauthorized = clientWithCredential(UUID.randomUUID().toString());
        assertThatThrownBy(() -> unauthorized.operate(endpoint,
                command("add-learner", operationId, before.epoch()), WRITE_SCOPE))
                .isInstanceOf(BrokerManagementClientException.class)
                .hasMessage("系统繁忙,请稍后重试");
        // 相同业务请求只更换服务凭证；合法凭证能到达领域校验并返回具名拒绝，而非客户端错误。
        BrokerOperationPayload authenticated = client.operate(endpoint,
                command("add-learner", operationId, before.epoch()), WRITE_SCOPE);
        assertThat(authenticated.status()).isEqualTo("REJECTED");
        assertThat(authenticated.message()).contains("DISCOVERED");
        assertThat(client.clusterState(endpoint)).isEqualTo(before);
        assertThatThrownBy(() -> client.queryOperation(endpoint, operationId))
                .isInstanceOf(BrokerManagementClientException.class)
                .hasMessage("参数错误,请稍后重试");
    }

    private static BrokerClusterOperationCommand command(String action, String operationId, long epoch) {
        return new BrokerClusterOperationCommand(LEARNER_ID, action, operationId,
                epoch, LEARNER_ENDPOINT, "contract test", WRITE_SCOPE);
    }

    private static HttpBrokerManagementClient clientWithCredential(String credential) {
        OpsBrokerProperties properties = new OpsBrokerProperties();
        properties.setManagementToken(credential);
        RestClient restClient = new OpsHttpBeans().brokerRestClient(RestClient.builder(), properties);
        return new HttpBrokerManagementClient(restClient, properties);
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot allocate contract test port", exception);
        }
    }
}
