package com.co.kc.imchat.broker.infrastructure.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerObservationIdentityTest {
    private static final String PREFIX = "spiffe://im-chat/broker/";
    private final BrokerCallerIdentityInterceptor interceptor = new BrokerCallerIdentityInterceptor(
            Map.of(BrokerCallerRole.BROKER, PREFIX), Set.of(BrokerAdmissionType.BROKER_OBSERVATION));

    @Test
    void trustedNetworkCannotSupplyObservationIdentityEvenWhenExplicitlyAllowlisted() {
        rejects(new BoltRequestContext("remote", "host", null, false, Set.of()));
    }
    @Test
    void legacySubjectAndUnverifiedUriCannotSupplyObservationIdentity() {
        rejects(new BoltRequestContext("remote", "host", PREFIX + "a", true, Set.of()));
        rejects(new BoltRequestContext("remote", "host", PREFIX + "a", false, Set.of(PREFIX + "a")));
    }
    @Test
    void multipleBrokerUriIdentitiesRejectBeforeDelegation() {
        rejects(new BoltRequestContext("remote", "host", PREFIX + "a", true, Set.of(PREFIX + "a", PREFIX + "b")));
    }
    @Test
    void uniqueVerifiedBrokerUriUsesCanonicalSourceRatherThanLegacyIdentity() throws Exception {
        BoltRequestContext context = new BoltRequestContext("remote", "host", "CN=legacy", true,
                Set.of(PREFIX + "a", "spiffe://other/service"));
        Object principal = interceptor.intercept(BrokerRequestInvocation.initial(
                BrokerBoltOperation.PROBE_BROKER, new Object(), context), invocation -> invocation.principal());
        assertThat(principal).isEqualTo(new BrokerCallerPrincipal(PREFIX + "a", BrokerCallerRole.BROKER));
    }
    private void rejects(BoltRequestContext context) {
        AtomicBoolean delegated = new AtomicBoolean();
        assertThatThrownBy(() -> interceptor.intercept(BrokerRequestInvocation.initial(
                BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT, new Object(), context), invocation -> {
                    delegated.set(true);
                    return null;
                })).isInstanceOf(SecurityException.class).hasMessageContaining("observation");
        assertThat(delegated).isFalse();
    }
}
