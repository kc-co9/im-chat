package com.co.kc.imchat.broker.infrastructure.gossip;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.infrastructure.config.BrokerRequestConfiguration;
import com.co.kc.imchat.broker.infrastructure.config.RegistryGossipConfiguration;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerSyncDeltaHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerSyncDigestHandler;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipScheduler;
import com.co.kc.imchat.broker.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.support.gossip.GossipSyncTracker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.server.BoltControlledServer;
import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实两端 mTLS 和正式 Processor/全准入链；仅 committed 查询替代 Raft，材料复用 HA fixture。 */
class BrokerGossipAdmissionIntegrationTest {
    private static final String PREFIX = "spiffe://im-chat/broker/";
    @TempDir static Path material;
    private static Path root;
    private static List<String> ids;
    private static String password;

    @BeforeAll
    static void certificates() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (!Files.isRegularFile(root.resolve("scripts/lib/routing-ha-tls.rb"))) {
            root = root.getParent();
        }
        Map<?, ?> compose = new Yaml().load(Files.readString(root.resolve("deploy/local/compose.routing-ha.yml")));
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        ids = services.entrySet().stream().filter(entry -> {
            Map<?, ?> environment = (Map<?, ?>) ((Map<?, ?>) entry.getValue()).get("environment");
            return environment != null && environment.containsKey("IM_BROKER_HOST");
        }).map(entry -> entry.getKey().toString()).toList();
        Path log = material.resolveSibling(material.getFileName() + ".log");
        Process process = new ProcessBuilder("ruby", root.resolve("scripts/lib/routing-ha-tls.rb").toString(),
                "create", material.toString(), root.resolve("deploy/local/compose.routing-ha.yml").toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(180, TimeUnit.SECONDS)).as("existing HA TLS material generator").isTrue();
            assertThat(process.exitValue()).as("HA TLS fixture exit; log=%s", log).isZero();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            }
            Files.deleteIfExists(log);
        }
        Map<?, ?> configuration = new Yaml().load(Files.readString(material.resolve(ids.getFirst() + "/application.yml")));
        Map<?, ?> im = (Map<?, ?>) configuration.get("im");
        Map<?, ?> bolt = (Map<?, ?>) im.get("bolt");
        Map<?, ?> client = (Map<?, ?>) bolt.get("client");
        password = (String) ((Map<?, ?>) client.get("tls")).get("key-store-password");
    }

    @Test
    void schedulerExchangesDigestAndDeltaAndConvergesRegistrationsAndTombstones() throws Exception {
        try (Node first = new Node(1, "VALID"); Node second = new Node(2, "VALID")) {
            first.seed(second);
            second.seed(first);
            first.store.putBrokerState(observation(1), "127.0.0.1", first.server.port());
            second.store.putBrokerState(observation(2), "127.0.0.1", second.server.port());
            BrokerGossipScheduler scheduler = first.scheduler();

            scheduler.gossip();

            assertThat(first.tracker.recent(1)).singleElement().satisfies(record -> {
                assertThat(record.status()).isEqualTo(DiagnosticStatus.SUCCESS);
                assertThat(record.processedCount()).isEqualTo(2);
            });
            assertThat(first.registry.find(observation(2))).isPresent();
            assertThat(second.registry.find(observation(1))).isPresent();
            verify(second.synchronizer).handleDigest(argThat(request -> request.nodeId().equals(first.observationId())));
            verify(second.synchronizer).handleDelta(argThat(request -> request.nodeId().equals(first.observationId())
                    && !request.entries().isEmpty()));

            // 只改变本地观察，不改稳定成员；同一真实链路传播删除墓碑。
            first.store.removeBrokerState(observation(1));
            second.store.removeBrokerState(observation(2));
            scheduler.gossip();
            assertThat(first.registry.find(observation(2))).isEmpty();
            assertThat(second.registry.find(observation(1))).isEmpty();
            assertThat(first.store.deltas(List.of("BROKER:" + observation(2))))
                    .singleElement().satisfies(delta -> assertThat(delta.operation()).isEqualTo(GossipDeltaOperation.REMOVED));
            assertThat(second.store.deltas(List.of("BROKER:" + observation(1))))
                    .singleElement().satisfies(delta -> assertThat(delta.operation()).isEqualTo(GossipDeltaOperation.REMOVED));
            assertThat(first.tracker.recent(2)).allSatisfy(record -> assertThat(record.status()).isEqualTo(DiagnosticStatus.SUCCESS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "WRONG_OBSERVATION", "WRONG_FULL_URI", "NONCOMMITTED"})
    void rejectsDigestAndMeaningfulDeltaBeforeHandlerOrRegistryMerge(String violation) throws Exception {
        try (Node source = new Node(1, "VALID"); Node receiver = new Node(2, violation)) {
            source.store.putBrokerState("must-not-merge", "untrusted", 18020);
            GossipDigestParams digest = new GossipDigestParams(source.observationId(), source.store.digest());
            GossipDeltaParams delta = new GossipDeltaParams(source.observationId(),
                    source.store.deltas(List.of("BROKER:must-not-merge")));
            assertThat(delta.entries()).hasSize(1);
            assertThatThrownBy(() -> source.call(receiver, BrokerBoltOperation.GOSSIP_DIGEST, digest, GossipDigestResult.class))
                    .isInstanceOfSatisfying(BoltInvocationException.class, failure ->
                            assertThat(failure.failure()).isEqualTo(BoltInvocationException.Failure.PROTOCOL));
            assertThatThrownBy(() -> source.call(receiver, BrokerBoltOperation.GOSSIP_DELTA, delta, Void.class))
                    .isInstanceOfSatisfying(BoltInvocationException.class, failure ->
                            assertThat(failure.failure()).isEqualTo(BoltInvocationException.Failure.PROTOCOL));
            verify(receiver.synchronizer, never()).handleDigest(any());
            verify(receiver.synchronizer, never()).handleDelta(any());
            assertThat(receiver.registry.list()).isEmpty();
            assertThat(receiver.store.digest()).isEmpty();
        }
    }

    @Test
    void startupPairSnapshotCannotBeReauthorizedByConfigurationMutation() throws Exception {
        try (Node source = new Node(1, "VALID"); Node receiver = new Node(2, "VALID")) {
            receiver.cluster.getTrustedPeers().getFirst().setObservationId("injected-observation");
            receiver.cluster.getTrustedPeers().getFirst().setTarget(new BoltTarget("untrusted:18020", PREFIX + "injected"));
            receiver.cluster.getTrustedPeers().clear();
            assertThatCode(() -> source.call(receiver, BrokerBoltOperation.GOSSIP_DIGEST,
                    new GossipDigestParams(source.observationId(), List.of()), GossipDigestResult.class)).doesNotThrowAnyException();
            assertThatThrownBy(() -> source.call(receiver, BrokerBoltOperation.GOSSIP_DIGEST,
                    new GossipDigestParams("injected-observation", List.of()), GossipDigestResult.class))
                    .isInstanceOf(BoltInvocationException.class);
            verify(receiver.synchronizer, times(1)).handleDigest(any());
        }
    }

    private static final class Node implements AutoCloseable {
        private final int index;
        private final ClusterProperties cluster = new ClusterProperties();
        private final BrokerProperties broker = new BrokerProperties();
        private final InMemoryBrokerRegistry registry = new InMemoryBrokerRegistry();
        private final InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        private final GossipSyncTracker tracker = new GossipSyncTracker(10);
        private final BrokerRoutingProperties routing = new BrokerRoutingProperties();
        private final BoltControlledClient client;
        private final BoltControlledServer server;
        private final BoltClientInvoker invoker;
        private final GossipSynchronizer synchronizer;
        private final BrokerGossipAdapter store;

        private Node(int index, String violation) throws Exception {
            this.index = index;
            broker.getInstance().setHost(ids.get(index - 1));
            broker.getInstance().setPort(18020);
            ImBoltProperties bolt = new ImBoltProperties();
            bolt.getClient().setAllowedPeerUriPrefixes(List.of(PREFIX));
            configureTls(bolt.getClient().getTls(), index);
            configureTls(bolt.getServer().getTls(), index);
            bolt.getServer().setHost("127.0.0.1");
            try (ServerSocket available = new ServerSocket(0)) {
                bolt.getServer().setPort(available.getLocalPort());
            }
            for (int peer = 1; peer <= 2; peer++) {
                if (violation.equals("MISSING") && peer == 1) { continue; }
                ClusterProperties.TrustedPeer binding = new ClusterProperties.TrustedPeer();
                binding.setObservationId(violation.equals("WRONG_OBSERVATION") && peer == 1
                        ? "other-observation" : observation(peer));
                binding.setTarget(new BoltTarget(ids.get(peer - 1) + ":18020", PREFIX
                        + (violation.equals("WRONG_FULL_URI") && peer == 1 ? "other-broker-1" : ids.get(peer - 1))));
                cluster.getTrustedPeers().add(binding);
            }
            client = new BoltControlledClient(bolt.getClient());
            invoker = new BoltClientInvoker(client, System::nanoTime, bolt.getClient());
            store = new BrokerGossipAdapter(registry, gateways, new InMemoryConnectionRegistry(), broker, cluster);
            synchronizer = spy(new GossipSynchronizer(store, new GossipPeerClient(new BoltRpcClient(invoker, bolt.getClient()))));
            BrokerRaftAdapter committed = mock(BrokerRaftAdapter.class);
            when(committed.getCommittedState()).thenReturn(new BrokerClusterState(1L, 7L,
                    BrokerSelectionAlgorithm.RENDEZVOUS_HASH, List.of(new BrokerNode(
                    violation.equals("NONCOMMITTED") ? "unrelated" : ids.get(0), ids.get(0) + ":18020",
                    BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE), new BrokerNode(ids.get(1), ids.get(1) + ":18020",
                    BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE))));
            BrokerRaftProperties raft = new BrokerRaftProperties();
            raft.setNodeId(ids.get(index - 1));
            raft.setClusterId("gossip-binding-test");
            server = new BoltControlledServer(bolt.getServer());
            try {
                server.registerUserProcessor(new BrokerRequestConfiguration().brokerBoltRequestProcessor(raft, routing,
                        new RegistryProperties(), committed, committed, mock(LocalRoutingLeaseService.class), gateways,
                        List.of(new BrokerSyncDigestHandler(synchronizer), new BrokerSyncDeltaHandler(synchronizer)), bolt,
                        new RegistryGossipConfiguration().brokerGossipSettings(cluster, broker)));
                assertThat(server.start()).isTrue();
                client.startup();
            } catch (Exception | AssertionError failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void seed(Node peer) {
            cluster.setSeedAddresses(List.of(peer.address()));
            cluster.getTrustedPeers().stream().filter(binding -> binding.getObservationId().equals(peer.observationId()))
                    .findFirst().orElseThrow().setTarget(new BoltTarget(peer.address(), PREFIX + ids.get(peer.index - 1)));
        }

        private BrokerGossipScheduler scheduler() {
            RegistryGossipConfiguration configuration = new RegistryGossipConfiguration();
            return configuration.brokerGossipScheduler(registry, synchronizer,
                    configuration.brokerGossipSettings(cluster, broker), tracker);
        }

        private String observationId() { return broker.getInstance().getId(); }
        private String address() { return "127.0.0.1:" + server.port(); }

        private <R> R call(Node receiver, BrokerBoltOperation operation, Object request, Class<R> response) {
            return invoker.invoke(new BoltTarget(receiver.address(), PREFIX + ids.get(receiver.index - 1)),
                    operation.service().service(), operation.operation(), request, response, 3000);
        }

        @Override
        public void close() {
            assertAll("independent owned transport cleanup", client::shutdown, server::stop);
        }
    }

    private static String observation(int index) { return "broker-" + ids.get(index - 1) + "-18020"; }

    private static void configureTls(ImBoltProperties.Tls tls, int index) {
        tls.setEnabled(true);
        tls.setKeyStore(material.resolve(ids.get(index - 1) + "/node.p12").toString());
        tls.setKeyStorePassword(password);
    }
}
