package com.co.kc.imchat.broker.support.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** 成员配置最近采样，仅供监控，不参与维护准入；初始值沿用未采样的零值。 */
@Component
public final class MembershipDiagnosticMetrics implements MeterBinder {
    private volatile boolean drift;
    private volatile boolean unavailable;

    public void recordComparison(boolean matches) {
        unavailable = false;
        drift = !matches;
    }

    public void recordUnavailable() {
        unavailable = true;
        drift = false;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("im.broker.membership.configuration.drift", this,
                metrics -> metrics.drift ? 1D : 0D).register(registry);
        Gauge.builder("im.broker.membership.diagnostic.unavailable", this,
                metrics -> metrics.unavailable ? 1D : 0D).register(registry);
    }
}
