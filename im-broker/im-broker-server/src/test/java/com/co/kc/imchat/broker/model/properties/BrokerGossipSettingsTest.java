package com.co.kc.imchat.broker.model.properties;

import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerGossipSettingsTest {
    private static final String PREFIX = "spiffe://im-chat/broker/";

    @ParameterizedTest
    @ValueSource(strings = {"ADDRESS", "OBSERVATION_ID", "FULL_URI", "NAMESPACE"})
    void rejectsConflictingOrOutOfNamespaceDeploymentBindings(String violation) {
        BrokerGossipSettings.PeerBinding first = peer("observation-a", "a:12200", PREFIX + "stable-a");
        BrokerGossipSettings.PeerBinding invalid = switch (violation) {
            case "ADDRESS" -> peer("observation-b", "a:12200", PREFIX + "stable-b");
            case "OBSERVATION_ID" -> peer("observation-a", "b:12200", PREFIX + "stable-b");
            case "FULL_URI" -> peer("observation-b", "b:12200", PREFIX + "stable-a");
            case "NAMESPACE" -> peer("observation-b", "b:12200", "spiffe://foreign/broker/stable-b");
            default -> throw new IllegalArgumentException(violation);
        };
        assertThatThrownBy(() -> properties(List.of(), List.of(first, invalid)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(violation.equals("NAMESPACE") ? "Invalid trusted Gossip peer binding" : "Conflicting trusted Gossip peer binding");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spiffe://custom/broker/node", "spiffe://im-chat/gateway/node", "spiffe://im-chat/broker/"})
    void rejectsIdentityOutsideFixedBrokerNamespace(String identity) {
        assertThatThrownBy(() -> properties(List.of(), List.of(peer("observation-a", "a:12200", identity))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid trusted Gossip peer binding");
    }

    @Test
    void copiesDeploymentListsAndExposesOnlyImmutableSnapshots() {
        List<String> seeds = new ArrayList<>(List.of("a:12200"));
        BrokerGossipSettings.PeerBinding original = peer("observation-a", "a:12200", PREFIX + "stable-a");
        List<BrokerGossipSettings.PeerBinding> peers = new ArrayList<>(List.of(original));
        BrokerGossipSettings configured = properties(seeds, peers);
        seeds.clear(); seeds.add("untrusted:12200");
        peers.clear(); peers.add(peer("injected", "untrusted:12200", PREFIX + "injected"));
        assertThat(configured.seedAddresses()).containsExactly("a:12200");
        assertThat(configured.trustedPeers()).containsExactly(original);
        assertThatThrownBy(() -> configured.seedAddresses().add("another:12200"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configured.trustedPeers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OBSERVATION_ID", "NAMESPACE"})
    void actualStartupBeanRejectsInvalidDeploymentPair(String violation) {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(com.co.kc.imchat.broker.infrastructure.config.RegistryGossipConfiguration.class)
                .withBean(com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties.class, () -> {
                    com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties cluster =
                            new com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties();
                    for (int index = 0; index < 2; index++) {
                        com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties.TrustedPeer binding =
                                new com.co.kc.imchat.broker.infrastructure.config.properties.ClusterProperties.TrustedPeer();
                        binding.setObservationId(violation.equals("OBSERVATION_ID") ? "same-observation" : "observation-" + index);
                        binding.setTarget(new BoltTarget("peer-" + index + ":18020",
                                (violation.equals("NAMESPACE") ? "spiffe://foreign/broker/" : PREFIX) + "stable-" + index));
                        cluster.getTrustedPeers().add(binding);
                    }
                    return cluster;
                })
                .withBean(com.co.kc.imchat.broker.infrastructure.config.properties.BrokerProperties.class)
                .withBean(com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties.class)
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining(violation.equals("NAMESPACE")
                                ? "Invalid trusted Gossip peer binding" : "Conflicting trusted Gossip peer binding"));
    }

    private static BrokerGossipSettings.PeerBinding peer(String observationId, String address, String identity) {
        return new BrokerGossipSettings.PeerBinding(observationId, new BoltTarget(address, identity));
    }

    private static BrokerGossipSettings properties(List<String> seeds,
                                                    List<BrokerGossipSettings.PeerBinding> peers) {
        return new BrokerGossipSettings("local-observation", "local:12200", seeds, peers, 2, Duration.ofSeconds(3));
    }
}
