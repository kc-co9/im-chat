package com.co.kc.imchat.broker.infrastructure.gossip;

import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties;
import com.co.kc.imchat.broker.infrastructure.domain.cluster.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerGossipScheduler;
import com.co.kc.imchat.broker.model.enums.DiagnosticStatus;
import com.co.kc.imchat.broker.model.properties.BrokerGossipSettings;
import com.co.kc.imchat.broker.support.gossip.GossipSyncTracker;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.sync.GossipSynchronizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerGossipSchedulerTest {

    @Test
    void skipsGossipWhenNoPeerExists() {
        ClusterProperties properties = new ClusterProperties();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerGossipAdapter brokerGossipAdapter = gossipStateStore(properties, brokerRegistry);
        BoltInvoker boltInvoker = mock(BoltInvoker.class);
        BrokerGossipScheduler lifecycle = new BrokerGossipScheduler(
                brokerRegistry,
                new GossipSynchronizer(brokerGossipAdapter, new GossipPeerClient(new BoltRpcClient(boltInvoker, boltPolicy()))),
                gossipProperties(properties, brokerProperties("10.0.0.1", 12200)),
                new GossipSyncTracker(10));

        lifecycle.gossip();

        verifyNoInteractions(boltInvoker);
    }

    @Test
    void syncsDiscoveredBroker() {
        ClusterProperties properties = new ClusterProperties();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        brokerRegistry.register("broker-2", "10.0.0.2", 12200);
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        BrokerProperties brokerProperties = brokerProperties("10.0.0.1", 12200);
        GossipSyncTracker tracker = new GossipSyncTracker(10);
        when(gossipSynchronizer.syncPeer(any(), any(), any(), any(Integer.class)))
                .thenReturn(3);
        BrokerGossipScheduler lifecycle = new BrokerGossipScheduler(
                brokerRegistry, gossipSynchronizer, gossipProperties(properties, brokerProperties), tracker);

        lifecycle.gossip();

        verify(gossipSynchronizer).syncPeer(
                eq(brokerProperties.getInstance().getId()),
                eq(new BoltTarget("10.0.0.2:12200", "spiffe://im-chat/broker/stable-2")),
                any(),
                eq(properties.getGossipTimeoutMs()));
        assertThat(tracker.recent(10)).singleElement().satisfies(record -> {
            assertThat(record.target()).isEqualTo("10.0.0.2:12200");
            assertThat(record.status()).isEqualTo(DiagnosticStatus.SUCCESS);
            assertThat(record.processedCount()).isEqualTo(3);
            assertThat(record.errorSummary()).isNull();
        });
    }

    @Test
    void usesSeedAddressBeforeDiscoveringRemoteBroker() {
        ClusterProperties properties = new ClusterProperties();
        properties.setSeedAddresses(List.of("10.0.0.2:12200"));
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        BrokerProperties brokerProperties = brokerProperties("10.0.0.1", 12200);
        BrokerGossipScheduler lifecycle = new BrokerGossipScheduler(
                brokerRegistry,
                gossipSynchronizer,
                gossipProperties(properties, brokerProperties),
                new GossipSyncTracker(10));

        lifecycle.gossip();

        verify(gossipSynchronizer).syncPeer(
                eq(brokerProperties.getInstance().getId()),
                eq(new BoltTarget("10.0.0.2:12200", "spiffe://im-chat/broker/stable-2")),
                any(),
                eq(properties.getGossipTimeoutMs()));
    }

    @Test
    void tracksFailedGossipWithoutChangingScheduledExecution() {
        ClusterProperties properties = new ClusterProperties();
        properties.setSeedAddresses(List.of("10.0.0.2:12200"));
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        when(gossipSynchronizer.syncPeer(any(), any(), any(), any(Integer.class)))
                .thenThrow(new IllegalStateException("peer unavailable"));
        GossipSyncTracker tracker = new GossipSyncTracker(10);
        BrokerGossipScheduler lifecycle = new BrokerGossipScheduler(
                new InMemoryBrokerRegistry(),
                gossipSynchronizer,
                gossipProperties(properties, brokerProperties("10.0.0.1", 12200)),
                tracker);

        lifecycle.gossip();

        assertThat(tracker.recent(10)).singleElement().satisfies(record -> {
            assertThat(record.target()).isEqualTo("10.0.0.2:12200");
            assertThat(record.status()).isEqualTo(DiagnosticStatus.FAILED);
            assertThat(record.processedCount()).isZero();
            assertThat(record.errorSummary()).isEqualTo("peer unavailable");
        });
    }

    @Test
    void ignoresDiagnosticFailureAfterSuccessfulGossip() {
        ClusterProperties properties = new ClusterProperties();
        properties.setSeedAddresses(List.of("10.0.0.2:12200"));
        GossipSynchronizer gossipSynchronizer = mock(GossipSynchronizer.class);
        when(gossipSynchronizer.syncPeer(any(), any(), any(), any(Integer.class)))
                .thenReturn(1);
        GossipSyncTracker tracker = mock(GossipSyncTracker.class);
        doThrow(new IllegalStateException("tracker unavailable"))
                .when(tracker).track(any());
        BrokerGossipScheduler lifecycle = new BrokerGossipScheduler(
                new InMemoryBrokerRegistry(),
                gossipSynchronizer,
                gossipProperties(properties, brokerProperties("10.0.0.1", 12200)),
                tracker);

        assertThatCode(lifecycle::gossip).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnboundSeedOrObservedIdentityBeforeAnyRpc() {
        GossipSynchronizer synchronizer = mock(GossipSynchronizer.class);
        BrokerGossipSettings unknownSeed = new BrokerGossipSettings("local", "10.0.0.1:12200",
                List.of("10.0.0.99:12200"), List.of(), 2, java.time.Duration.ofSeconds(3));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new BrokerGossipScheduler(new InMemoryBrokerRegistry(),
                synchronizer, unknownSeed, new GossipSyncTracker(10)).gossip()).isInstanceOf(SecurityException.class);
        InMemoryBrokerRegistry registry = new InMemoryBrokerRegistry();
        registry.register("untrusted-observation", "10.0.0.2", 12200);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new BrokerGossipScheduler(registry, synchronizer,
                gossipProperties(new ClusterProperties(), brokerProperties("10.0.0.1", 12200)), new GossipSyncTracker(10)).gossip())
                .isInstanceOf(SecurityException.class).hasMessageContaining("Unbound Gossip");
        verifyNoInteractions(synchronizer);
    }

    @Test
    void authenticationFailureIsNotSwallowedOrRedirectedToAnotherPeer() {
        ClusterProperties cluster = new ClusterProperties();
        cluster.setSeedAddresses(List.of("10.0.0.2:12200"));
        GossipSynchronizer synchronizer = mock(GossipSynchronizer.class);
        BoltInvocationException refusal = new BoltInvocationException(BoltInvocationException.Failure.AUTHENTICATION, "wrong peer", null);
        when(synchronizer.syncPeer(any(), any(), any(), any(Integer.class))).thenThrow(refusal);
        BrokerGossipScheduler scheduler = new BrokerGossipScheduler(new InMemoryBrokerRegistry(), synchronizer,
                gossipProperties(cluster, brokerProperties("10.0.0.1", 12200)), new GossipSyncTracker(10));
        org.assertj.core.api.Assertions.assertThatThrownBy(scheduler::gossip).isSameAs(refusal);
        verify(synchronizer).syncPeer(any(), eq(new BoltTarget("10.0.0.2:12200", "spiffe://im-chat/broker/stable-2")), any(), any(Integer.class));
    }

    private ImBoltProperties.Client boltPolicy() {
        ImBoltProperties.Client policy = new ImBoltProperties.Client();
        policy.setAllowedPeerUriPrefixes(List.of("spiffe://im-chat/broker/"));
        return policy;
    }

    private BrokerGossipAdapter gossipStateStore(ClusterProperties properties,
                                               InMemoryBrokerRegistry brokerRegistry) {
        return new BrokerGossipAdapter(
                brokerRegistry,
                new InMemoryGatewayRegistry(),
                new InMemoryConnectionRegistry(),
                new BrokerProperties(),
                properties);
    }

    private BrokerProperties brokerProperties(String host, int port) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost(host);
        properties.getInstance().setPort(port);
        return properties;
    }

    private BrokerGossipSettings gossipProperties(ClusterProperties cluster, BrokerProperties broker) {
        BrokerProperties.Instance instance = broker.getInstance();
        return new BrokerGossipSettings(instance.getId(), instance.getAddress(),
                cluster.getSeedAddresses(), List.of(new BrokerGossipSettings.PeerBinding("broker-2",
                        new BoltTarget("10.0.0.2:12200", "spiffe://im-chat/broker/stable-2"))), cluster.getGossipFanout(), java.time.Duration.ofMillis(cluster.getGossipTimeoutMs()));
    }
}
