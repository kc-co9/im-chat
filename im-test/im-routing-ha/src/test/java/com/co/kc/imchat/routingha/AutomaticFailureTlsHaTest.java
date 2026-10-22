package com.co.kc.imchat.routingha;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.broker.support.health.BrokerHealthObservationTracker;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerQuorumAppService;
import com.co.kc.imchat.broker.application.local.LocalLeaseAppService;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.service.BrokerFailureObservationService;
import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.domain.authority.FileFenceWatermarkRepository;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerHealthReportHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerProbeHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.QuorumReadIndexHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLeaseInstallHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLeasePrepareHandler;
import com.co.kc.imchat.broker.interfaces.handler.routing.RoutingFenceHandler;
import com.co.kc.imchat.broker.model.cqrs.command.ActivateRoutingLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.ConfirmGatewayRoutesCommand;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportRow;
import com.co.kc.imchat.broker.sdk.model.result.GatewayRouteSnapshotAckResult;
import com.co.kc.imchat.common.utils.HashUtils;
import com.co.kc.imchat.plugin.bolt.core.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import io.netty.channel.Channel;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.awaitility.Awaitility.await;

/**
 * 隔离运行时证据：真实三节点 JRaft、三个独立 mTLS 身份、生产观察/准入/fence/Lease。
 * Gateway 进程及路由重建被显式 ACK 输入替代；不宣称覆盖容器、WebSocket 或真实 Gateway 重建。
 */
class AutomaticFailureTlsHaTest {
    private static final String CLUSTER = "automatic-failure-ha";
    private static final List<String> IDS = List.of("broker-a", "broker-b", "broker-c");
    private static final Duration BUDGET = Duration.ofSeconds(45);
    // 仅本测试缩短时窗；quarantine 仍大于本测试实际签发的 Lease 时长。
    private static final Duration LEASE = Duration.ofMillis(600);
    private static final Duration QUARANTINE = Duration.ofMillis(900);
    private static final Duration STABLE_WINDOW = Duration.ofSeconds(2);
    private static final Duration FRESHNESS = Duration.ofSeconds(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(2);
    private static final Duration RPC_TIMEOUT = Duration.ofMillis(500);
    @TempDir static Path certificates;
    @TempDir Path directory;
    private static List<Path> stores;

    @BeforeAll
    static void createCertificates() throws Exception {
        stores = RoutingHaCertificates.create(certificates, IDS);
    }

    @Test
    void stoppedFollowerAutomaticallyFencesAndRebuildsWithIndependentTlsReports() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node leader = cluster.leader();
            Node failed = cluster.nodes.stream().filter(node -> node != leader).findFirst().orElseThrow();
            cluster.verifyHealthyAndLeased(leader);
            failed.stop();
            BrokerClusterOperation operation = cluster.awaitAutomaticRemoval(failed);
            cluster.assertIsolationAndLeases(operation, failed);
            cluster.acknowledgeGatewayAndAwaitSuccess(operation);
        }
    }

    @Test
    void stoppedLeaderRequiresFreshReportsAndReplacementCompletesDegradedWithoutGatewayAck() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node failed = cluster.leader();
            cluster.verifyHealthyAndLeased(failed);
            failed.stop();
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(failed);
            BrokerClusterOperation operation = cluster.awaitAutomaticRemoval(failed);
            cluster.assertIsolationAndLeases(operation, failed);
            await().atMost(BUDGET).untilAsserted(() -> {
                BrokerClusterOperation completed = replacement.raft.findOperation(operation.id()).orElseThrow();
                assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.DEGRADED);
                assertThat(completed.error().value()).contains("gateway-fixture");
            });
            assertThat(replacement.raft.getClusterSnapshot().operations()).hasSize(1);
        }
    }

    @Test
    void replacementLeaderResumesCommittedFencingWithActualMembershipAndTlsPeers() throws Exception {
        try (Cluster cluster = new Cluster(directory)) {
            Node previous = cluster.leader();
            cluster.verifyHealthyAndLeased(previous);
            Node target = cluster.nodes.stream().filter(node -> node != previous).findFirst().orElseThrow();
            // 仅隔离目标 RPC，保留真实 Raft voter，使后续 Leader 故障仍有两个 voter 可选举。
            target.stopRpcAndAssertClosed();
            AtomicReference<BrokerClusterOperation> begun = new AtomicReference<>();
            await().pollInterval(Duration.ofMillis(10)).atMost(BUDGET).untilAsserted(() -> {
                List<BrokerClusterOperation> operations = previous.raft.getClusterSnapshot().operations();
                assertThat(operations).hasSize(1);
                BrokerClusterOperation operation = operations.getFirst();
                assertThat(operation.phase()).isEqualTo(BrokerClusterOperationPhase.FENCING_OLD_EPOCH);
                assertThat(previous.raft.getCommittedState().epoch()).isZero();
                begun.set(operation);
            });
            previous.stop();
            Node replacement = cluster.leader();
            assertThat(replacement).isNotSameAs(previous);
            await().atMost(BUDGET).untilAsserted(() ->
                    assertThat(replacement.raft.findOperation(begun.get().id())).isPresent());
            // 旧 Leader 仍是 ACTIVE holder；恢复其持久身份后新 Leader 才能完成所有存活 holder 的 Lease 安装。
            cluster.restart(previous);
            await().atMost(BUDGET).untilAsserted(() -> {
                Node current = cluster.live().stream().filter(node -> node.raft.getLocalBroker().isLeader())
                        .findFirst().orElseThrow();
                ConsensusMembership membership = current.raft.getConsensusMembership();
                assertThat(current.raft.getClusterSnapshot().findConsistentVoters(membership)).hasSize(3);
                assertThat(current.raft.findOperation(begun.get().id()).orElseThrow().phase())
                        .isEqualTo(BrokerClusterOperationPhase.REBUILDING_ROUTES);
                assertThat(current.raft.getCommittedState().epoch()).isEqualTo(1L);
                assertThat(current.raft.getClusterSnapshot().operations()).hasSize(1);
                assertThat(current.raft.getCommittedState().brokers().stream()
                        .filter(broker -> broker.brokerId().equals(target.id)).findFirst().orElseThrow().routingState())
                        .isEqualTo(BrokerRoutingState.INACTIVE);
            });
            cluster.acknowledgeGatewayAndAwaitSuccess(begun.get());
        }
    }

    static void awaitInitialMembership(Duration budget, Runnable readiness) {
        await().atMost(budget).ignoreExceptionsMatching(failure ->
                failure instanceof IllegalStateException
                        && "Applied configuration unavailable".equals(failure.getMessage())
                        && failure.getCause() instanceof IllegalStateException
                        && "No applied configuration".equals(failure.getCause().getMessage()))
                .untilAsserted(readiness::run);
    }

    private static final class Cluster implements AutoCloseable {
        private final List<Node> nodes = new CopyOnWriteArrayList<>();
        private final Path directory;
        private final List<String> voters;
        private final List<String> routes;
        private final ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor();
        private final List<Throwable> tickFailures = new CopyOnWriteArrayList<>();

        private Cluster(Path directory) throws Exception {
            this.directory = directory;
            List<String> endpoints = availableEndpoints();
            voters = endpoints.subList(0, 3);
            routes = endpoints.subList(3, 6);
            try {
                for (int index = 0; index < 3; index++) {
                    nodes.add(new Node(index, directory, voters, routes));
                }
                leader();
                awaitInitialMembership(BUDGET, () -> nodes.forEach(node -> {
                    ConsensusMembership membership = node.raft.getConsensusMembership();
                    assertThat(node.raft.getClusterSnapshot().findConsistentVoters(membership))
                            .containsExactlyInAnyOrderElementsOf(IDS.stream().map(BrokerId::new).toList());
                }));
                ticks.scheduleWithFixedDelay(this::tick, 0, 100, TimeUnit.MILLISECONDS);
            } catch (Exception | AssertionError failure) {
                try {
                    close();
                } catch (Exception | AssertionError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private void restart(Node previous) throws Exception {
            int index = IDS.indexOf(previous.id);
            previous.close();
            nodes.set(index, new Node(index, directory, voters, routes));
        }

        private void tick() {
            for (Node node : live()) {
                try {
                    node.observe().whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            tickFailures.add(failure);
                        }
                    });
                } catch (Exception failure) {
                    tickFailures.add(failure);
                }
            }
        }

        private List<Node> live() {
            return nodes.stream().filter(node -> !node.stopped && !node.raftStopped).toList();
        }

        private Node leader() {
            AtomicReference<Node> selected = new AtomicReference<>();
            await().atMost(BUDGET).until(() -> {
                List<Node> leaders = live().stream().filter(node -> node.raft.getLocalBroker().isLeader()).toList();
                if (leaders.size() != 1) {
                    return false;
                }
                selected.set(leaders.getFirst());
                return true;
            });
            return selected.get();
        }

        private void verifyHealthyAndLeased(Node leader) throws Exception {
            await().atMost(BUDGET).untilAsserted(() -> nodes.forEach(node -> node.assertHealthy()));
            for (Node node : nodes) {
                assertThat(leader.app.activateLease(new ActivateRoutingLeaseCommand("initial-" + node.id,
                        node.id, 0L)).toCompletableFuture().get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(node.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isTrue();
            }
            assertThat(leader.raft.getClusterSnapshot().operations()).isEmpty();
        }

        private BrokerClusterOperation awaitAutomaticRemoval(Node failed) {
            AtomicReference<BrokerClusterOperation> found = new AtomicReference<>();
            await().atMost(BUDGET).untilAsserted(() -> {
                List<Node> leaders = live().stream()
                        .filter(node -> node.raft.getLocalBroker().isLeader()).toList();
                assertThat(leaders).as("live Leader while awaiting automatic removal").hasSize(1);
                Node leader = leaders.getFirst();
                List<BrokerClusterOperation> operations = leader.raft.getClusterSnapshot().operations();
                assertThat(operations).as("tick failures: %s", tickFailures).hasSize(1);
                BrokerClusterOperation operation = operations.getFirst();
                assertThat(operation.type()).isEqualTo(BrokerClusterOperationType.FAIL_BROKER);
                assertThat(operation.id().value()).startsWith("auto-failure:");
                assertThat(Duration.between(failed.stoppedAt, operation.createdAt())).isGreaterThanOrEqualTo(STABLE_WINDOW);
                assertThat(operation.targetBrokerId()).isEqualTo(new BrokerId(failed.id));
                assertThat(operation.phase()).isIn(BrokerClusterOperationPhase.REBUILDING_ROUTES,
                        BrokerClusterOperationPhase.VERIFYING, BrokerClusterOperationPhase.DEGRADED);
                found.set(operation);
            });
            return found.get();
        }

        private void assertIsolationAndLeases(BrokerClusterOperation operation, Node failed) throws Exception {
            await().atMost(BUDGET).untilAsserted(() -> {
                for (Node node : live()) {
                    assertThat(node.raft.getCommittedState().epoch()).isEqualTo(1L);
                    assertThat(node.raft.getCommittedState().brokers().stream()
                            .filter(broker -> broker.brokerId().equals(failed.id)).findFirst().orElseThrow().routingState())
                            .isEqualTo(BrokerRoutingState.INACTIVE);
                    assertThat(node.watermark.highestFencedEpoch()).contains(new RoutingEpoch(0L));
                    assertThat(node.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isFalse();
                }
            });
            Node leader = leader();
            assertThat(live().stream().filter(node -> node != leader).toList()).allSatisfy(node -> {
                assertThat(node.reportCalls.get()).as("real follower report RPCs").isPositive();
                assertThat(node.reportCallers).contains(RoutingHaCertificates.PREFIX + leader.id);
                assertThat(node.stableFailureTargets).as("independent stable failure report over TLS").contains(failed.id);
            });
            for (Node node : live()) {
                // 真实安装之后可能已跨过短测试租约期限；续签仍走 prepare/quorum/grant/install。
                assertThat(leader.app.activateLease(new ActivateRoutingLeaseCommand("verify-" + node.id,
                        node.id, 1L)).toCompletableFuture().get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(node.leases.executeOrElse(new RoutingEpoch(1L), () -> true, false)).isTrue();
            }
            assertThat(failed.leases.executeOrElse(new RoutingEpoch(0L), () -> true, false)).isFalse();
            assertThat(leader.raft.findRoutingFence()).isPresent();
            assertThat(leader.raft.findOperation(operation.id())).isPresent();
        }

        private void acknowledgeGatewayAndAwaitSuccess(BrokerClusterOperation operation) {
            Node leader = leader();
            // 与 GatewayRouteTable 一致，对去重排序后的快照用户集合计算摘要。
            List<Long> snapshotUsers = List.of(1L, 2L, 3L);
            String checksum = HashUtils.sha256HexLongs(snapshotUsers);
            assertThat(leader.app.confirmGatewayRoutes(new ConfirmGatewayRoutesCommand("gateway-fixture", 1L, 10L,
                    (long) snapshotUsers.size(), checksum, Instant.now().plusSeconds(10))).status())
                    .isEqualTo(GatewayRouteSnapshotAckResult.Status.ACKNOWLEDGED);
            await().atMost(BUDGET).untilAsserted(() -> {
                BrokerClusterOperation completed = leader.raft.findOperation(operation.id()).orElseThrow();
                assertThat(completed.phase()).isEqualTo(BrokerClusterOperationPhase.SUCCEEDED);
                assertThat(completed.drainProgress().gateways()).singleElement().satisfies(checkpoint -> {
                    assertThat(checkpoint.gatewayId().value()).isEqualTo("gateway-fixture");
                    assertThat(checkpoint.confirmedRouteRevision().value()).isEqualTo(10L);
                    assertThat(checkpoint.confirmedRouteCount().value()).isEqualTo(snapshotUsers.size());
                    assertThat(checkpoint.contentChecksum().value()).isEqualTo(checksum);
                });
                assertThat(leader.raft.getClusterSnapshot().operations()).hasSize(1);
            });
        }

        @Override
        public void close() {
            ticks.shutdownNow();
            assertAll("close all Broker nodes", nodes.reversed().stream().map(node -> node::close));
        }
    }

    private static final class Node implements AutoCloseable {
        private final String id;
        private RpcServer server;
        private final List<Channel> acceptedChannels = new CopyOnWriteArrayList<>();
        private final RpcClient client = new RpcClient();
        private BrokerRaftAdapter raft;
        private final FileFenceWatermarkRepository watermark;
        private final LocalRoutingLeaseService leases;
        private final BrokerClusterAppService app;
        private final BrokerFailureObservationService policy =
                new BrokerFailureObservationService(STABLE_WINDOW, FRESHNESS);
        private Cache<LeaseNonce, PreparedLeaseRequest> pending;
        private final ThreadPoolExecutor peers = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        private final ScheduledExecutorService quarantine = Executors.newSingleThreadScheduledExecutor();
        private final ThreadPoolExecutor observations = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.AbortPolicy());
        private final AtomicBoolean observing = new AtomicBoolean();
        private final AtomicInteger reportCalls = new AtomicInteger();
        private final Set<String> reportCallers = ConcurrentHashMap.newKeySet();
        private final Set<String> probeCallers = ConcurrentHashMap.newKeySet();
        private final Set<String> stableFailureTargets = ConcurrentHashMap.newKeySet();
        private volatile boolean stopped;
        private volatile Instant stoppedAt;
        private volatile boolean raftStopped;

        private Node(int index, Path directory, List<String> voters, List<String> routes) throws Exception {
            id = IDS.get(index);
            try {
                BrokerRaftProperties properties = raftProperties(index, directory, voters, routes);
                BrokerClusterState state = properties.initialClusterState(BrokerSelectionAlgorithm.RENDEZVOUS_HASH);
                raft = new BrokerRaftAdapter(properties, state, new RoutingLeaseGrantService(LEASE));
                watermark = new FileFenceWatermarkRepository(directory.resolve(id + "-fence.json"), CLUSTER, id);
                leases = new LocalRoutingLeaseService(new BrokerId(id), () -> raft.getClusterSnapshot().epoch(), watermark);
                pending = CaffeineCacheBuilder.createCaffeineCacheBuilder().limit(16)
                        .expireAfterWrite(10, TimeUnit.SECONDS).buildCache();
                LocalLeaseAppService local = new LocalLeaseAppService(leases, pending);
                configureClient(stores.get(index));
                client.init();
                BrokerProperties brokerProperties = new BrokerProperties();
                brokerProperties.getPeerCall().setTimeoutMs(500);
                BrokerRoutingProperties routing = new BrokerRoutingProperties();
                BrokerFailureObservationProperties observation = new BrokerFailureObservationProperties();
                observation.setRpcTimeout(RPC_TIMEOUT);
                BrokerPeerAdapter peer = new BrokerPeerAdapter(new BoltClientInvoker(client, System::nanoTime), brokerProperties,
                        peers, peers, routing, observation, id, observations, RoutingHaCertificates.PREFIX);
                InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
                gateways.register(new GatewayRegistration(new GatewayId("gateway-fixture"), new GatewayHost("127.0.0.1"),
                        new GatewayPort(19001), new GatewayPrincipalId("spiffe://im-chat/gateway/fixture"),
                        Instant.now(), Instant.now()));
                app = createApplication(peer, gateways);
                server = new RpcServer("127.0.0.1", Integer.parseInt(routes.get(index).split(":")[1]));
                configureServer(stores.get(index));
                server.addConnectionEventProcessor(ConnectionEventType.CONNECT,
                        (remoteAddress, connection) -> acceptedChannels.add(connection.getChannel()));
                List<BoltRequestHandler> handlers = requestHandlers(local).stream()
                        .map(this::authenticatedChain).map(this::recordTransportEvidence).toList();
                server.registerUserProcessor(new BoltRequestProcessor(handlers));
                assertThat(server.start()).isTrue();
            } catch (Exception | AssertionError failure) {
                try {
                    close();
                } catch (Exception | AssertionError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private List<AbstractBrokerRpcHandler<?, ?>> requestHandlers(LocalLeaseAppService local) {
            return List.of(new BrokerProbeHandler(app), new BrokerHealthReportHandler(app),
                    new QuorumReadIndexHandler(new BrokerQuorumAppService(raft)),
                    new RouteLeasePrepareHandler(local, id), new RouteLeaseInstallHandler(local),
                    new RoutingFenceHandler(leases, id));
        }

        private BoltRequestHandler authenticatedChain(AbstractBrokerRpcHandler<?, ?> handler) {
            BrokerCallerIdentityInterceptor identity = new BrokerCallerIdentityInterceptor(
                    Map.of(BrokerCallerRole.BROKER, RoutingHaCertificates.PREFIX), Set.of());
            BrokerPeerAdmissionInterceptor admission = new BrokerPeerAdmissionInterceptor(id, raft,
                    RoutingHaCertificates.PREFIX, CLUSTER);
            return new BrokerRequestInterceptorChain(List.of(identity, admission, new BrokerHandlerInterceptor(handler)));
        }

        private BoltRequestHandler recordTransportEvidence(BoltRequestHandler chain) {
            return new BoltRequestHandler() {
                @Override
                public String service() {
                    return chain.service();
                }

                @Override
                public String operation() {
                    return chain.operation();
                }

                @Override
                public Object handle(BoltRequestContext context, String payload) throws Exception {
                    Object result = chain.handle(context, payload);
                    assertThat(context.tlsVerified()).isTrue();
                    assertThat(context.peerUriIdentities()).hasSize(1);
                    if (chain.operation().equals(BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT.operation())) {
                        recordReport(context, (BrokerHealthReportResult) result);
                    }
                    if (chain.operation().equals(BrokerBoltOperation.PROBE_BROKER.operation())) {
                        probeCallers.addAll(context.peerUriIdentities());
                    }
                    return result;
                }
            };
        }

        private void recordReport(BoltRequestContext context, BrokerHealthReportResult report) {
            reportCalls.incrementAndGet();
            reportCallers.addAll(context.peerUriIdentities());
            // SDK 与 Domain 均定义 BrokerHealthStatus；此处明确使用 wire 枚举，避免同名 import 冲突。
            report.observations().stream().filter(row -> row.status()
                    == com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus.UNHEALTHY
                    && row.unreachableDurationMs() >= STABLE_WINDOW.toMillis())
                    .map(BrokerHealthReportRow::brokerId).forEach(stableFailureTargets::add);
        }

        private BrokerRaftProperties raftProperties(int index, Path directory, List<String> voters, List<String> routes) {
            BrokerRaftProperties properties = new BrokerRaftProperties();
            properties.setClusterId(CLUSTER);
            properties.setGroupId("routing-ha-" + directory.getFileName());
            properties.setNodeId(id);
            properties.setEndpoint(voters.get(index));
            properties.setInitialVoters(voters);
            properties.setDataDirectory(directory.resolve(id).toString());
            properties.setElectionTimeoutMs(500);
            properties.setSnapshotInterval(Duration.ZERO);
            properties.setBootstrapBrokers(IntStream.range(0, 3).mapToObj(member -> {
                BrokerRaftProperties.BootstrapBroker broker = new BrokerRaftProperties.BootstrapBroker();
                broker.setBrokerId(IDS.get(member));
                broker.setRouteEndpoint(routes.get(member));
                broker.setMembershipEndpoint(voters.get(member));
                return broker;
            }).toList());
            return properties;
        }

        private BrokerClusterAppService createApplication(BrokerPeerAdapter peer, InMemoryGatewayRegistry gateways) {
            BrokerId localId = new BrokerId(id);
            BrokerHealthObservationTracker store = new BrokerHealthObservationTracker(localId, FRESHNESS);
            return new BrokerClusterAppService(raft, peer, gateways, QUARANTINE, Duration.ofMillis(300),
                    quarantine, Duration.ofSeconds(15),
                    store, policy, COOLDOWN);
        }

        private void configureClient(Path store) {
            client.option(BoltClientOption.CLI_SSL_ENABLE, true);
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, "PKCS12");
            client.option(BoltClientOption.CLI_SSL_KEYSTORE, store.toString());
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, RoutingHaCertificates.PASSWORD);
        }

        private void configureServer(Path store) {
            server.option(BoltServerOption.SERVER_MANAGE_CONNECTION_SWITCH, true);
            server.option(BoltServerOption.SRV_SSL_ENABLE, true);
            server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, true);
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, "PKCS12");
            server.option(BoltServerOption.SRV_SSL_KEYSTORE, store.toString());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, RoutingHaCertificates.PASSWORD);
        }

        private CompletionStage<Void> observe() {
            if (!observing.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                return app.probeFailures().thenCompose(ignored -> app.reconcileFailures())
                        .whenComplete((ignored, failure) -> observing.set(false));
            } catch (RuntimeException failure) {
                observing.set(false);
                return CompletableFuture.failedFuture(failure);
            }
        }

        private void assertHealthy() {
            BrokerClusterState state = raft.getCommittedState();
            String caller = IDS.stream().filter(identity -> !identity.equals(id)).findFirst().orElseThrow();
            BrokerHealthReportDTO report = app.getHealthReport(new BrokerHealthReportQuery(CLUSTER, caller, id, state.revision(),
                    state.epoch(), "healthy-" + id, IDS));
            assertThat(probeCallers).containsExactlyInAnyOrderElementsOf(IDS.stream()
                    .filter(identity -> !identity.equals(id)).map(identity -> RoutingHaCertificates.PREFIX + identity).toList());
            assertThat(report.observations().stream().filter(row -> !row.brokerId().equals(id)).toList())
                    .hasSize(2).allSatisfy(row -> {
                        assertThat(row.status()).isEqualTo(BrokerHealthStatus.HEALTHY);
                        assertThat(row.unreachableDuration()).isEqualTo(Duration.ZERO);
                        assertThat(row.observationAge()).isLessThan(FRESHNESS);
                    });
        }

        private synchronized void stopRaft() {
            if (!raftStopped) {
                raftStopped = true;
                if (raft != null) {
                    try {
                        raft.close();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted stopping Broker Raft runtime", failure);
                    }
                }
            }
        }

        private void stopRpcAndAssertClosed() {
            List<Channel> accepted = acceptedChannels.stream().filter(Channel::isActive).toList();
            server.stop();
            assertThat(accepted).as("active accepted channels before stopping %s", id).isNotEmpty();
            assertAll("stopped RPC boundary for " + id,
                    () -> await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                            assertThat(accepted).allSatisfy(channel -> {
                                assertThat(channel.closeFuture().isDone()).isTrue();
                                assertThat(channel.isActive()).isFalse();
                            })),
                    () -> await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                        try (Socket socket = new Socket()) {
                            assertThatThrownBy(() -> socket.connect(
                                    new InetSocketAddress("127.0.0.1", server.port()), 500))
                                    .isInstanceOf(ConnectException.class);
                        }
                    }));
        }

        private synchronized void stop() {
            if (!stopped) {
                stoppedAt = Instant.now();
                stopped = true;
                assertAll("stop Broker " + id, this::stopRpcAndAssertClosed, client::shutdown, this::stopRaft);
            }
        }

        @Override
        public void close() {
            stopped = true;
            assertAll("close Broker " + id,
                    () -> { if (server != null && server.isStarted()) { server.stop(); } },
                    () -> { if (client.isStarted()) { client.shutdown(); } },
                    this::stopRaft,
                    () -> {
                        peers.shutdownNow();
                        observations.shutdownNow();
                        quarantine.shutdownNow();
                        assertAll("terminate Broker pools", java.util.stream.Stream.of(peers, observations, quarantine)
                                .map(pool -> () -> {
                                    try {
                                        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw failure;
                                    }
                                }));
                    },
                    () -> { if (pending != null) { pending.close(); } });
        }

    }

    private static List<String> availableEndpoints() throws Exception {
        List<ServerSocket> reservations = new ArrayList<>();
        try {
            for (int index = 0; index < 6; index++) {
                reservations.add(new ServerSocket(0));
            }
            return reservations.stream().map(socket -> "127.0.0.1:" + socket.getLocalPort()).toList();
        } finally {
            for (ServerSocket socket : reservations) {
                socket.close();
            }
        }
    }
}
