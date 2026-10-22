package fixture.broker.support.monitoring;

import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public final class LegalObserver implements MeterBinder {
    private final BrokerRegistry brokerRegistry;
    private final BrokerGossipAdapter gossip;

    public LegalObserver(BrokerRegistry brokerRegistry, BrokerGossipAdapter gossip) {
        this.brokerRegistry = brokerRegistry;
        this.gossip = gossip;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("fixture.brokers", brokerRegistry, value -> value.list().size()).register(registry);
        Gauge.builder("fixture.gossip", gossip, value -> value.digest().size()).register(registry);
    }
}
