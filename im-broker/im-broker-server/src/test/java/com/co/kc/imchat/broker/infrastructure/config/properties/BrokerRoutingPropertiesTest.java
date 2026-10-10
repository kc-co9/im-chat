package com.co.kc.imchat.broker.infrastructure.config.properties;

import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerRoutingPropertiesTest {

    @Test
    void computesConservativeLeaseAndQuarantineFromOneValidatedConfiguration() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setMaxLeaseDuration(Duration.ofSeconds(4));
        properties.setRenewalInterval(Duration.ofSeconds(1));
        properties.setMaxClockDriftAllowance(Duration.ofMillis(100));
        properties.setLeaseSafetyMargin(Duration.ofMillis(200));
        properties.setMaxSchedulingPause(Duration.ofSeconds(2));
        properties.setNetworkSafetyMargin(Duration.ofMillis(500));

        properties.validate();

        assertThat(properties.leaseGrantDuration()).isEqualTo(Duration.ofMillis(3_700));
        assertThat(properties.quarantineDuration()).isEqualTo(Duration.ofMillis(6_600));
    }

    @Test
    void rejectsRenewalThatCannotCompleteWithinConservativeLease() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setMaxLeaseDuration(Duration.ofSeconds(2));
        properties.setRenewalInterval(Duration.ofSeconds(2));
        properties.setMaxClockDriftAllowance(Duration.ofMillis(100));
        properties.setLeaseSafetyMargin(Duration.ofMillis(100));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("renewalInterval");
    }

    @Test
    void rejectsNonPositivePendingLeaseCapacity() {
        BrokerRoutingProperties properties = new BrokerRoutingProperties();
        properties.setLeasePendingCapacity(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routing capacities");
    }
}
