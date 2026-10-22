package com.co.kc.imchat.broker.infrastructure.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerTimePropertiesTest {
    @Test
    void bindsExplicitMillisecondKeysAndDurationSnapshot() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "im.broker.raft.election-timeout-ms", "1200",
                "im.broker.raft.command-timeout-ms", "5400",
                "im.broker.raft.snapshot-interval", "301s",
                "im.broker.cluster.gossip-timeout-ms", "3200",
                "im.broker.cluster.gossip-removed-ttl-ms", "310000",
                "im.broker.peer-call.timeout-ms", "3300",
                "im.broker.gateway-push.bolt.timeout-ms", "3400")));
        BrokerRaftProperties raft = binder.bind("im.broker.raft", BrokerRaftProperties.class).get();
        assertThat(raft.getElectionTimeoutMs()).isEqualTo(1200);
        assertThat(raft.getCommandTimeoutMs()).isEqualTo(5400);
        assertThat(raft.getSnapshotInterval()).isEqualTo(Duration.ofSeconds(301));
        ClusterProperties cluster = binder.bind("im.broker.cluster", ClusterProperties.class).get();
        assertThat(cluster.getGossipTimeoutMs()).isEqualTo(3200);
        assertThat(cluster.getGossipRemovedTtlMs()).isEqualTo(310000);
        BrokerProperties broker = binder.bind("im.broker", BrokerProperties.class).get();
        assertThat(broker.getPeerCall().getTimeoutMs()).isEqualTo(3300);
        assertThat(broker.getGatewayPush().getBolt().getTimeoutMs()).isEqualTo(3400);
        BrokerRaftProperties defaults = new BrokerRaftProperties();
        assertThat(defaults.getSnapshotInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(defaults.getElectionTimeoutMs()).isEqualTo(1000);
        assertThat(defaults.getCommandTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void snapshotAllowsZeroButRejectsNegativeFractionalAndOverflowSeconds() {
        BrokerRaftProperties properties = new BrokerRaftProperties();
        properties.setClusterId("cluster"); properties.setNodeId("node");
        properties.setEndpoint("127.0.0.1:18120");
        properties.setInitialVoters(List.of("127.0.0.1:18120"));
        properties.setSnapshotInterval(Duration.ZERO); properties.validate();
        for (Duration invalid : List.of(Duration.ofMillis(-1), Duration.ofMillis(1001),
                Duration.ofSeconds((long) Integer.MAX_VALUE + 1))) {
            properties.setSnapshotInterval(invalid);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        }
        properties.setSnapshotInterval(null);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
