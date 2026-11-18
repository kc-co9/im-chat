package com.co.kc.imchat.broker.infrastructure.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.ServiceCallerRoleInterceptor;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.model.io.FrameResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCallerRoleInterceptorTest {

    private final ServiceCallerRoleInterceptor interceptor =
            new ServiceCallerRoleInterceptor();

    @Test
    void acceptsGatewayInboundBeforeCallingNext() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        BrokerFrameWriteParams request = BrokerFrameWriteParams.inbound(1L, "connection-1",
                new FrameRequest("1", "cmd", "seq", "trace", Map.of()));
        BrokerRequestInvocation<BrokerFrameWriteParams> invocation =
                BrokerRequestInvocation.initial(BrokerBoltOperation.WRITE_FRAME, request, null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                "spiffe://im-chat/gateway/gateway-1", BrokerCallerRole.GATEWAY));

        Object result = interceptor.intercept(invocation, authorized -> {
            called.set(true);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(called).isTrue();
    }

    @Test
    void rejectsGatewayTryingToWriteOutboundFrame() {
        BrokerFrameWriteParams request = BrokerFrameWriteParams.outbound(1L,
                new FrameResponse("1", FrameType.PUSH, "cmd", null, "trace",
                        0, null, Map.of()));
        BrokerRequestInvocation<BrokerFrameWriteParams> invocation =
                BrokerRequestInvocation.initial(BrokerBoltOperation.WRITE_FRAME, request, null)
                        .withPrincipal(new BrokerCallerPrincipal(
                                "spiffe://im-chat/gateway/gateway-1", BrokerCallerRole.GATEWAY));

        assertThatThrownBy(() -> interceptor.intercept(invocation,
                authorized -> "unexpected"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("callerRole");
    }
}
