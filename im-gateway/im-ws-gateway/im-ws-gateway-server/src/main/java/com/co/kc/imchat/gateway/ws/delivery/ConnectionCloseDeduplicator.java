package com.co.kc.imchat.gateway.ws.delivery;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/** 按 operationId + userId + sessionVersion 保留连接关闭幂等结果。 */
public final class ConnectionCloseDeduplicator {
    private final int capacity;
    private final long retentionMillis;
    private final LongSupplier clock;
    private final Map<CloseKey, Long> completedAt = new HashMap<>();
    private final Set<CloseKey> inProgress = new HashSet<>();

    public ConnectionCloseDeduplicator(int capacity,
                                       Duration retention,
                                       LongSupplier clock) {
        if (capacity <= 0 || retention == null || retention.isZero()
                || retention.isNegative() || clock == null) {
            throw new IllegalArgumentException("invalid connection close deduplicator configuration");
        }
        this.capacity = capacity;
        this.retentionMillis = retention.toMillis();
        this.clock = clock;
    }

    public synchronized Decision acquire(
            String operationId, Long userId, String sessionVersion) {
        if (operationId == null || operationId.isBlank() || userId == null
                || sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("invalid connection close key");
        }
        long now = clock.getAsLong();
        completedAt.entrySet().removeIf(entry -> now - entry.getValue() >= retentionMillis);
        CloseKey key = new CloseKey(operationId, userId, sessionVersion);
        if (completedAt.containsKey(key)) {
            return Decision.DUPLICATE;
        }
        if (inProgress.contains(key)) {
            return Decision.IN_PROGRESS;
        }
        if (completedAt.size() + inProgress.size() >= capacity) {
            return Decision.CAPACITY_EXHAUSTED;
        }
        inProgress.add(key);
        return Decision.ACQUIRED;
    }

    public synchronized void complete(String operationId, Long userId, String sessionVersion) {
        CloseKey key = new CloseKey(operationId, userId, sessionVersion);
        inProgress.remove(key);
        completedAt.put(key, clock.getAsLong());
    }

    public synchronized void release(String operationId, Long userId, String sessionVersion) {
        inProgress.remove(new CloseKey(operationId, userId, sessionVersion));
    }

    public enum Decision {
        ACQUIRED,
        IN_PROGRESS,
        DUPLICATE,
        CAPACITY_EXHAUSTED
    }

    private record CloseKey(String operationId, Long userId, String sessionVersion) {
    }
}
