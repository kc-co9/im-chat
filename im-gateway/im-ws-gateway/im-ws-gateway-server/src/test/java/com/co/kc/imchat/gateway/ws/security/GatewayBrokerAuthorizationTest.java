package com.co.kc.imchat.gateway.ws.security;

import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayBrokerAuthorizationTest {

    private final GatewayBrokerAuthorizer authorizer = new GatewayBrokerAuthorizer(
            GatewayBrokerAuthorizationTest::state,
            false);

    @Test
    void acceptsOnlyCommittedBrokerTlsIdentity() {
        assertThatCode(() -> authorizer.authorize(
                context("spiffe://im-chat/broker/broker-a")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizer.authorize(
                context("spiffe://im-chat/broker/broker-unknown")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> authorizer.authorize(
                context("spiffe://custom.example/broker/broker-a")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> authorizer.authorize(
                context("spiffe://im-chat/gateway/gateway-a")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsLegacyUnverifiedAndAmbiguousBrokerIdentities() {
        for (BoltRequestContext context : List.of(
                new BoltRequestContext("remote", "127.0.0.1", "spiffe://im-chat/broker/broker-a", false, Set.of(), false),
                new BoltRequestContext("remote", "127.0.0.1", "spiffe://im-chat/broker/broker-a", true, Set.of(), false),
                new BoltRequestContext("remote", "127.0.0.1", "spiffe://im-chat/broker/broker-a", true,
                        Set.of("spiffe://im-chat/broker/broker-a", "spiffe://im-chat/broker/broker-b"), false))) {
            assertThatThrownBy(() -> authorizer.authorize(context)).isInstanceOf(SecurityException.class);
        }
    }

    private BoltRequestContext context(String identity) {
        return new BoltRequestContext("127.0.0.1:18020", "127.0.0.1", identity, true, Set.of(identity), false);
    }

    private static BrokerClusterState state() {
        return new BrokerClusterState(1L, 7L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-a", "127.0.0.1:18020",
                        BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
    }
}
