package com.co.kc.imchat.management.iam.sdk.introspection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IamAvailabilityCircuitTest {

    @Test
    void opensAfterThreeFailuresAndProbesAfterFiveSeconds() {
        Instant now = Instant.parse("2026-08-26T08:00:00Z");
        IamAvailabilityCircuit circuit = new IamAvailabilityCircuit();

        circuit.availabilityFailure(now);
        circuit.availabilityFailure(now);
        circuit.availabilityFailure(now);

        assertThat(circuit.allowRequest(now.plusSeconds(4))).isFalse();
        assertThat(circuit.allowRequest(now.plusSeconds(5))).isTrue();
        assertThat(circuit.allowRequest(now.plusSeconds(5))).isFalse();

        circuit.success();
        assertThat(circuit.allowRequest(now.plusSeconds(6))).isTrue();
    }
}
