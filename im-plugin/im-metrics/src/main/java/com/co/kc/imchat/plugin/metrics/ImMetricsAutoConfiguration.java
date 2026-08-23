package com.co.kc.imchat.plugin.metrics;

import com.co.kc.imchat.plugin.metrics.aspect.ObservedAspect;
import com.co.kc.imchat.plugin.metrics.aspect.IgnoreExceptionAspect;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(MeterRegistry.class)
public class ImMetricsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector(MeterRegistry meterRegistry) {
        return new MetricsCollector(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservedAspect observedAspect(MetricsCollector metricsCollector) {
        return new ObservedAspect(metricsCollector);
    }

    @Bean
    @ConditionalOnMissingBean
    public IgnoreExceptionAspect ignoreExceptionAspect() {
        return new IgnoreExceptionAspect();
    }
}
