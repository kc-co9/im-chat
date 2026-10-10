package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;

import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRequestInterceptor;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.interfaces.handler.gateway.GatewayRegisterHandler;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AbstractBrokerRpcHandlerDispatchTest {

    @Test
    void contextualHandleUsesAssembledChain() throws Exception {
        StringHandler handler = new StringHandler();
        BoltRequestHandler chain = authenticatedChain(new BrokerCallerPrincipal(
                "gateway-1", BrokerCallerRole.GATEWAY), handler);

        assertThat(chain.handle(new BoltRequestContext("remote", "host", "gateway-1"), "\"value\""))
                .isEqualTo("value");
        assertThatThrownBy(() -> chain.handle(null, "\"value\""))
                .isInstanceOf(SecurityException.class);
        assertThat(BoltRequestHandler.class.isAssignableFrom(handler.getClass())).isFalse();
    }

    @Test
    void gatewayRegisterHandlerRejectsNonGatewayCaller() {
        GatewayRegistry registry = mock(GatewayRegistry.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        GatewayRegisterHandler handler = new GatewayRegisterHandler(registry, publisher);
        BoltRequestHandler chain = authenticatedChain(new BrokerCallerPrincipal(
                "broker-1", BrokerCallerRole.BROKER), handler);

        assertThatThrownBy(() -> chain.handle(
                new BoltRequestContext("remote", "host", "broker-1"),
                JsonUtils.toJson(new GatewayRegisterParams("gateway-1", "127.0.0.1", 18012))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("gateway caller");
        verifyNoInteractions(registry, publisher);
    }

    private BoltRequestHandler authenticatedChain(BrokerCallerPrincipal principal,
                                                              AbstractBrokerRpcHandler<?, ?> handler) {
        List<BrokerRequestInterceptor> nodes = new ArrayList<>();
        nodes.add((invocation, chain) -> {
            if (invocation.context() == null || invocation.context().peerIdentity() == null) {
                throw new SecurityException("transport identity is required");
            }
            return chain.proceed(invocation.withPrincipal(principal));
        });
        nodes.add(new BrokerHandlerInterceptor(handler));
        return new BrokerRequestInterceptorChain(nodes);
    }

    private static final class StringHandler extends AbstractBrokerRpcHandler<String, String> {
        private StringHandler() {
            super(BrokerBoltOperation.GET_CLUSTER_STATE);
        }

        @Override
        protected String process(String request) {
            return request;
        }
    }

}
