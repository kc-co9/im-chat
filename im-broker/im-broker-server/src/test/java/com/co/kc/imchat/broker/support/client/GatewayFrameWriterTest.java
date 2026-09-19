package com.co.kc.imchat.broker.support.client;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.metrics.aspect.ObservedAspect;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayFrameWriterTest {

    @Test
    void recordsGatewayWriteDurationThroughObservedProxy() {
        GatewayFrameWriteResult expected = new GatewayFrameWriteResult(List.of("conn-1"), List.of());
        GatewayClient client = new GatewayClient(unusedBoltInvoker(), 1000) {
            @Override
            public GatewayFrameWriteResult writeFrame(GatewayEndpointDTO gateway, GatewayFrameWriteParams params) {
                return expected;
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new GatewayFrameWriter(client));
        proxyFactory.addAspect(new ObservedAspect(new MetricsCollector(registry)));
        GatewayFrameWriter writer = proxyFactory.getProxy();

        GatewayFrameWriteResult result = writer.writeFrame(
                new GatewayEndpointDTO("gateway-1", "127.0.0.1", 18021,
                        Instant.EPOCH, Instant.EPOCH),
                new GatewayFrameWriteParams(1L, new FrameResponse(
                        "1", FrameType.PUSH, "message.private.sent", null, null,
                        null, null, Map.of())));

        assertThat(result).isEqualTo(expected);
        assertThat(registry.counter("im.broker.gateway.frame.write", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(registry.timer("im.broker.gateway.frame.write.duration").count()).isEqualTo(1);
    }

    private BoltInvoker unusedBoltInvoker() {
        return new BoltInvoker() {
            @Override
            public <T, R> R invoke(String address, String service, String operation, T request,
                                   Class<R> responseType, int timeoutMillis) {
                throw new UnsupportedOperationException("unused");
            }
        };
    }
}
