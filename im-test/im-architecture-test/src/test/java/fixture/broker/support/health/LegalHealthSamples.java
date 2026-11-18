package fixture.broker.support.health;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.time.Duration;
import java.util.Map;

public final class LegalHealthSamples {
    private final Map<BrokerId, MonotonicTimestamp> samples = new java.util.HashMap<>();
    public void record(BrokerId target, MonotonicTimestamp sampledAt, Duration freshness) {
        AssertUtils.argNotNull("freshness is required", freshness);
        samples.put(target, sampledAt);
    }
}
