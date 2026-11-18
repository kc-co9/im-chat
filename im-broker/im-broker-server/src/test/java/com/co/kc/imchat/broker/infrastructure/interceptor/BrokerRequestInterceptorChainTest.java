package com.co.kc.imchat.broker.infrastructure.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRequestInterceptor;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerRequestInterceptorChainTest {
    @Test
    void executesInterceptorsInDeclaredOrderAroundHandler() throws Exception {
        List<String> calls = new ArrayList<>();
        BoltRequestHandler chain = new BrokerRequestInterceptorChain(List.of(
                around("identity", calls), around("service", calls), around("route", calls),
                new BrokerHandlerInterceptor(new RecordingHandler(calls))));

        assertThat(chain.handle(null, "\"request\"")).isEqualTo("ok");
        assertThat(calls).containsExactly("identity-before", "service-before", "route-before",
                "handler", "route-after", "service-after", "identity-after");
    }

    @Test
    void contextlessProtectedRequestFailsBeforeHandler() {
        List<String> calls = new ArrayList<>();
        BrokerRequestInterceptor identity = (invocation, next) -> {
            if (invocation.context() == null) {
                throw new SecurityException("transport identity is required");
            }
            return next.proceed(invocation);
        };
        BoltRequestHandler chain = new BrokerRequestInterceptorChain(List.of(
                identity, new BrokerHandlerInterceptor(new RecordingHandler(calls))));

        assertThatThrownBy(() -> chain.handle(null, "\"request\""))
                .isInstanceOf(SecurityException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void requiresBusinessNodeAtEndForOperationMetadata() {
        List<String> calls = new ArrayList<>();
        assertThatThrownBy(() -> new BrokerRequestInterceptorChain(List.of(
                new BrokerHandlerInterceptor(new RecordingHandler(calls)), around("identity", calls))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("last request node");
    }

    private BrokerRequestInterceptor around(String name, List<String> calls) {
        return (invocation, next) -> {
            calls.add(name + "-before");
            Object result = next.proceed(invocation);
            calls.add(name + "-after");
            return result;
        };
    }

    private static final class RecordingHandler extends AbstractBrokerRpcHandler<String, String> {
        private final List<String> calls;

        private RecordingHandler(List<String> calls) {
            super(BrokerBoltOperation.WRITE_FRAME);
            this.calls = calls;
        }

        @Override
        protected String process(String request) {
            calls.add("handler");
            return "ok";
        }
    }
}
