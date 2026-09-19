package com.co.kc.imchat.gateway.ws.registry;

import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.plugin.metrics.aspect.ObservedAspect;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegistryMetricsTest {

    @Test
    void exportsFrameWriteDurationWhenInvokedThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            ConnectionRegistry registry = context.getBean(ConnectionRegistry.class);
            SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry.class);
            registry.register(1L, "session-1", "conn-1", new EmbeddedChannel());

            registry.writeFrame(1L, new FrameResponse(
                    "1", FrameType.PUSH, "message.private.sent", null, null,
                    null, null, Map.of()));

            assertThat(meterRegistry.counter("im.gateway.frame.write", "outcome", "success").count())
                    .isEqualTo(1);
            assertThat(meterRegistry.timer("im.gateway.frame.write.duration").count()).isEqualTo(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        MetricsCollector metricsCollector(SimpleMeterRegistry meterRegistry) {
            return new MetricsCollector(meterRegistry);
        }

        @Bean
        ObservedAspect observedAspect(MetricsCollector metricsCollector) {
            return new ObservedAspect(metricsCollector);
        }

        @Bean
        ConnectionRegistry connectionRegistry() {
            return new ConnectionRegistry();
        }
    }
}
