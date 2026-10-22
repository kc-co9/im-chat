package com.co.kc.imchat.common.domain.cluster.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipEndpointTest {
    @Test
    void endpointRetainsOpaqueValueAndUsesValueEquality() {
        String raw = " host:18120:2:100 ";
        MembershipEndpoint endpoint = new MembershipEndpoint(raw);
        assertThat(endpoint.value()).isEqualTo(raw);
        assertThat(endpoint).isEqualTo(new MembershipEndpoint(raw));
        assertThat(endpoint.hashCode()).isEqualTo(new MembershipEndpoint(raw).hashCode());
    }

    @Test
    void nullAndBlankEndpointsAreRejected() {
        for (String value : new String[] {null, "", " \t\n"}) {
            assertThatThrownBy(() -> new MembershipEndpoint(value))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("membership endpoint must not be blank");
        }
    }

    @Test
    void serializedEndpointRetainsItsValue() throws Exception {
        MembershipEndpoint endpoint = new MembershipEndpoint("host:18120:2:100");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(endpoint);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(endpoint);
        }
    }
}
