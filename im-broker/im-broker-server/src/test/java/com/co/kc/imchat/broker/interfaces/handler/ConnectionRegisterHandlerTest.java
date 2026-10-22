package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.infrastructure.domain.connection.InMemoryGatewayRegistry;
import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionRegisterHandler;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import com.co.kc.imchat.broker.support.observation.NoopDomainEventPublisher;
import com.co.kc.imchat.broker.transformer.registry.RegistryTransformer;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegisterHandlerTest {

    @Test
    void registersConnectionAfterRouteAdmission() throws Exception {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();
        InMemoryGatewayRegistry gateways = new InMemoryGatewayRegistry();
        gateways.register(RegistryTransformer.INSTANCE.gatewayEndpointFrom("gw-1", "127.0.0.1", 18012, "spiffe://im/gateway/gw-1"));
        ConnectionRegisterHandler handler = new ConnectionRegisterHandler(registry,
                new NoopDomainEventPublisher());
        ConnectionRegisterParams params = new ConnectionRegisterParams(1L, "gw-1", 1L,
                new RouteRequestContext(BrokerRequestChainTestSupport.EPOCH,
                        "register-1", System.currentTimeMillis() + 10_000L));

        BrokerRequestChainTestSupport.acceptingChain(handler).handle(new BoltRequestContext(
                "127.0.0.1:12345", "127.0.0.1", "spiffe://im/gateway/gw-1", false, Set.of()),
                JsonUtils.toJson(params));

        assertThat(registry.find(new UserId(1L))).hasSize(1);
        assertThat(registry.find(new UserId(1L)).get(0).gatewayId().value()).isEqualTo("gw-1");
    }
}
