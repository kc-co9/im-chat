package com.co.kc.imchat.plugin.metrics.aspect;

import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import com.co.kc.imchat.plugin.metrics.annotation.IgnoreException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ObservedAspectTest {
    @Test
    void recordsSuccessAndDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservedTarget target = new ObservedTarget();
        ObservedTarget proxy = proxy(target, registry);

        assertThat(proxy.succeed()).isEqualTo("ok");

        assertThat(registry.counter("test.operation", "outcome", "success").count()).isEqualTo(1);
        assertThat(registry.timer("test.operation.duration").count()).isEqualTo(1);
    }

    @Test
    void recordsFailureAndCanIgnoreBestEffortException() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservedTarget proxy = proxy(new ObservedTarget(), registry);

        proxy.ignoreFailure();

        assertThat(registry.counter("test.best-effort", "outcome", "failure").count()).isEqualTo(1);
        assertThat(registry.timer("test.best-effort.duration").count()).isEqualTo(1);
    }

    @Test
    void metricsFailureDoesNotBreakSuccessfulBusinessCall() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservedTarget target = new ObservedTarget();
        ObservedTarget proxy = proxy(target, new MetricsCollector(registry) {
            @Override
            @IgnoreException
            public void success(String name, String[] tags) {
                throw new IllegalStateException("metrics unavailable");
            }
        });

        assertThatCode(proxy::succeed).doesNotThrowAnyException();
    }

    @Test
    void metricsFailureDoesNotReplaceBusinessException() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservedTarget target = new ObservedTarget();
        ObservedTarget proxy = proxy(target, new MetricsCollector(registry) {
            @Override
            @IgnoreException
            public void failure(String name, String[] tags) {
                throw new IllegalStateException("metrics unavailable");
            }

            @Override
            @IgnoreException
            public void stop(String name, String[] tags, Timer.Sample sample) {
                throw new IllegalStateException("metrics unavailable");
            }
        });

        assertThatThrownBy(proxy::fail)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("business failure");
    }

    private ObservedTarget proxy(ObservedTarget target, SimpleMeterRegistry registry) {
        return proxy(target, new MetricsCollector(registry));
    }

    private ObservedTarget proxy(ObservedTarget target, MetricsCollector collector) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        AspectJProxyFactory collectorProxyFactory = new AspectJProxyFactory(collector);
        collectorProxyFactory.addAspect(new IgnoreExceptionAspect());
        proxyFactory.addAspect(new ObservedAspect(collectorProxyFactory.getProxy()));
        return proxyFactory.getProxy();
    }

    static class ObservedTarget {
        @Observed(name = "test.operation")
        public String succeed() {
            return "ok";
        }

        @Observed(name = "test.best-effort", ignoreFailure = true)
        public void ignoreFailure() {
            throw new IllegalStateException("expected");
        }

        @Observed(name = "test.failure")
        public void fail() {
            throw new IllegalArgumentException("business failure");
        }
    }
}
