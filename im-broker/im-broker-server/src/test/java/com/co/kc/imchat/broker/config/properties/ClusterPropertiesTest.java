package com.co.kc.imchat.broker.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterPropertiesTest {

    @Test
    void gossipFanoutShouldBeAtLeastOne() {
        ClusterProperties properties = new ClusterProperties();
        properties.setGossipFanout(0);

        assertThat(properties.getGossipFanout()).isEqualTo(1);
    }

    @Test
    void shouldKeepValidGossipFanout() {
        ClusterProperties properties = new ClusterProperties();
        properties.setGossipFanout(3);

        assertThat(properties.getGossipFanout()).isEqualTo(3);
    }
}
