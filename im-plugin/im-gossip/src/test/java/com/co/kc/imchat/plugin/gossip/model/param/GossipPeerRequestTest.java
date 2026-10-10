package com.co.kc.imchat.plugin.gossip.model.param;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GossipPeerRequestTest {

    @Test
    void exposesNodeIdentityThroughPluginOwnedCapability() {
        GossipPeerRequest digest = new GossipDigestParams("broker-1", List.of());
        GossipPeerRequest delta = new GossipDeltaParams("broker-2", List.of());

        assertThat(digest.nodeId()).isEqualTo("broker-1");
        assertThat(delta.nodeId()).isEqualTo("broker-2");
    }
}
