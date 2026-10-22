package com.co.kc.imchat.broker.infrastructure.config.properties;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerFailureObservationPropertiesTest {
    @Test
    void defaultsKeepThirtySecondStabilityAndBoundObservationResources() {
        BrokerFailureObservationProperties properties = new BrokerFailureObservationProperties();
        properties.validate();
        assertThat(properties.getStableWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getCooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getFreshness()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getMaxConcurrentRequests()).isEqualTo(4);
        assertThat(properties.getMaxPendingRequests()).isEqualTo(64);
        properties.setRpcTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSamplingIntervalsLongerThanFreshnessAndUnboundedQueues() {
        BrokerFailureObservationProperties properties = new BrokerFailureObservationProperties();
        properties.setProbeInterval(Duration.ofSeconds(11));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setProbeInterval(Duration.ofSeconds(1));
        properties.setMaxPendingRequests(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
