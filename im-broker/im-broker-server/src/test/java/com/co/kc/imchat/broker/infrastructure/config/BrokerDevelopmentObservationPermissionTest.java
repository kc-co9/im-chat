package com.co.kc.imchat.broker.infrastructure.config;

import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerFailureObservationProperties;
import com.co.kc.imchat.plugin.bolt.ImBoltAutoConfiguration;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BrokerDevelopmentObservationPermissionTest {
    @Test
    void mutableOptionsCannotReopenActualDevelopmentTransportOrUnfinishedRecovery() {
        new ApplicationContextRunner().withUserConfiguration(ImBoltAutoConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("bolt-dev"))
                .withPropertyValues("im.bolt.client.enabled=true", "im.bolt.client.mode=DEVELOPMENT_PLAINTEXT",
                        "im.bolt.client.development.synthetic-data=true", "im.bolt.client.allowed-peer-uri-prefixes[0]=spiffe://im-chat/broker/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BrokerFailureObservationProperties options = new BrokerFailureObservationProperties();
                    ThreadPoolExecutor peerExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
                    ExecutorService lookupExecutor = Executors.newSingleThreadExecutor();
                    ExecutorService observationExecutor = Executors.newSingleThreadExecutor();
                    try {
                        BrokerPeerAdapter peer = new BrokerClusterConfiguration().brokerPeerAdapter(context.getBean(BoltInvoker.class),
                                new BrokerProperties(), peerExecutor, lookupExecutor, new BrokerRoutingProperties(), options,
                                new BrokerRaftProperties(), observationExecutor, context.getBean(RpcClient.class));
                        options.setRpcTimeout(Duration.ofSeconds(2));
                        BrokerRaftAdapter raft = mock(BrokerRaftAdapter.class);
                        BrokerCluster cluster = mock(BrokerCluster.class);
                        when(cluster.findUnfinishedFailureOperation()).thenReturn(Optional.of(mock(BrokerClusterOperation.class)));
                        when(raft.getClusterSnapshot()).thenReturn(cluster);
                        BrokerClusterAppService app = new BrokerClusterAppService(raft, peer, mock(GatewayRegistry.class),
                                Duration.ofSeconds(1), Duration.ofSeconds(1), mock(java.util.concurrent.ScheduledExecutorService.class),
                                Duration.ofSeconds(1), null, null, Duration.ofSeconds(1));
                        assertThatThrownBy(() -> peer.probe(null, null, null, null).toCompletableFuture().join()).hasCauseInstanceOf(SecurityException.class);
                        assertThatThrownBy(() -> peer.fetchHealthReport(null, null, null, null, Set.of()).toCompletableFuture().join()).hasCauseInstanceOf(SecurityException.class);
                        assertThatThrownBy(() -> app.reconcileFailures().toCompletableFuture().join()).hasCauseInstanceOf(SecurityException.class);
                        verifyNoInteractions(raft, cluster);
                    } finally {
                        peerExecutor.shutdown(); lookupExecutor.shutdown(); observationExecutor.shutdown();
                    }
                });
    }
}
