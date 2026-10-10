package com.co.kc.imchat.gateway.ws.delivery;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** 在最低保留窗口内按 deliveryId + connectionId 抑制重复 Channel 写入。 */
public final class FrameDeliveryDeduplicator {
    private final int capacity;
    private final long retentionMillis;
    private final LongSupplier clock;
    private final Map<DeliveryKey, Long> acceptedAt = new HashMap<>();

    public FrameDeliveryDeduplicator(int capacity,
                                     Duration retention,
                                     LongSupplier clock) {
        if (capacity <= 0 || retention == null || retention.isZero()
                || retention.isNegative() || clock == null) {
            throw new IllegalArgumentException("invalid frame delivery deduplicator configuration");
        }
        this.capacity = capacity;
        this.retentionMillis = retention.toMillis();
        this.clock = clock;
    }

    public synchronized Decision acquire(String deliveryId, String connectionId) {
        validate(deliveryId, connectionId);
        long now = clock.getAsLong();
        acceptedAt.entrySet().removeIf(entry -> now - entry.getValue() >= retentionMillis);
        DeliveryKey key = new DeliveryKey(deliveryId, connectionId);
        if (acceptedAt.containsKey(key)) {
            return Decision.DUPLICATE;
        }
        if (acceptedAt.size() >= capacity) {
            return Decision.CAPACITY_EXHAUSTED;
        }
        acceptedAt.put(key, now);
        return Decision.ACQUIRED;
    }

    public synchronized void release(String deliveryId, String connectionId) {
        validate(deliveryId, connectionId);
        acceptedAt.remove(new DeliveryKey(deliveryId, connectionId));
    }

    private void validate(String deliveryId, String connectionId) {
        if (deliveryId == null || deliveryId.isBlank()
                || connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("deliveryId and connectionId must not be blank");
        }
    }

    public enum Decision {
        ACQUIRED,
        DUPLICATE,
        CAPACITY_EXHAUSTED
    }

    private record DeliveryKey(String deliveryId, String connectionId) {
    }
}
