package com.co.kc.imchat.broker.support.monitoring;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Broker 本机 routing epoch、Lease 授权与持久 fence 水位指标。
 */
@Component
public final class RoutingAuthorityMetrics implements MeterBinder {

    private final BrokerRaftAdapter repository;
    private final LocalRoutingLeaseService leaseManager;

    public RoutingAuthorityMetrics(BrokerRaftAdapter repository,
                                   LocalRoutingLeaseService leaseManager) {
        this.repository = repository;
        this.leaseManager = leaseManager;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("im.broker.routing.epoch", repository,
                        value -> value.getCommittedState().epoch())
                .description("Committed routing epoch installed by this Broker")
                .register(registry);
        Gauge.builder("im.broker.routing.authorized", leaseManager,
                        value -> value.isRoutingAuthorized() ? 1D : 0D)
                .description("Whether this Broker can admit authoritative routing side effects")
                .register(registry);
        Gauge.builder("im.broker.routing.fenced.through.epoch", leaseManager,
                        value -> value.highestFencedEpoch()
                                .map(epoch -> (double) epoch.value())
                                .orElse(-1D))
                .description("Highest routing epoch durably fenced by this Broker")
                .register(registry);
    }
}
