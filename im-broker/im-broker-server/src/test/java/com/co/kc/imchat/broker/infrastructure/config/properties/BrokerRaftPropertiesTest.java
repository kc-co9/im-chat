package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;

import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerRaftPropertiesTest {

    @Test
    void bindsDeterministicLocalBootstrapState() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        BrokerRaftProperties properties = Binder.get(environment)
                .bind("im.broker.raft", BrokerRaftProperties.class)
                .orElseThrow(IllegalStateException::new);

        BrokerRoutingProperties routingProperties = Binder.get(environment)
                .bind("im.broker.routing", BrokerRoutingProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.getClusterId())
                .isEqualTo("local-broker-cluster");
        assertThat(properties.bootstrapMembershipEndpoints())
                .containsEntry("broker-local-1", "127.0.0.1:18120");
        assertThat(properties.initialClusterState(routingProperties.getAlgorithm()).brokers())
                .singleElement().satisfies(node -> {
            assertThat(node.brokerId()).isEqualTo("broker-local-1");
            assertThat(node.routeEndpoint()).isEqualTo("127.0.0.1:18020");
            assertThat(node.membershipRole()).isEqualTo(BrokerMembershipRole.VOTER);
            assertThat(node.routingState()).isEqualTo(BrokerRoutingState.ACTIVE);
        });
    }

    @Test
    void rejectsBootstrapWithoutAnActiveBroker() {
        BrokerRaftProperties properties = new BrokerRaftProperties();
        properties.setClusterId("cluster-a");
        properties.setNodeId("broker-a");
        properties.setEndpoint("127.0.0.1:18120");
        properties.setInitialVoters(java.util.List.of("127.0.0.1:18120"));
        BrokerRaftProperties.BootstrapBroker broker = new BrokerRaftProperties.BootstrapBroker();
        broker.setBrokerId("broker-a");
        broker.setRouteEndpoint("127.0.0.1:18020");
        broker.setRoutingState(BrokerRoutingState.READY);
        properties.setBootstrapBrokers(java.util.List.of(broker));

        assertThatThrownBy(() -> properties.initialClusterState(
                        BrokerSelectionAlgorithm.RENDEZVOUS_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void rejectsInitialVoterWithoutMembershipEndpointBinding() {
        BrokerRaftProperties properties = new BrokerRaftProperties();
        BrokerRaftProperties.BootstrapBroker broker = new BrokerRaftProperties.BootstrapBroker();
        broker.setBrokerId("broker-a");
        properties.setBootstrapBrokers(java.util.List.of(broker));

        assertThatThrownBy(properties::bootstrapMembershipEndpoints)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bootstrap membershipEndpoint");
    }
}
