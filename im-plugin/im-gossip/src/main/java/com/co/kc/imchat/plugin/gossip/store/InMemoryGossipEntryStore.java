package com.co.kc.imchat.plugin.gossip.store;

import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipEntityType;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.GossipDigestEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 通用 Gossip 本地状态表。
 * <p>
 * 只维护 key、entityType、version、operation 和 payload，不解析 payload 内容。
 */
public class InMemoryGossipEntryStore {
    private final Map<String, GossipDeltaEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, RemovedState> removedStates = new ConcurrentHashMap<>();
    private final String localNodeId;
    private final AtomicReference<GossipVersion> versionClock;
    private final LongSupplier removedTtlMillisSupplier;

    public InMemoryGossipEntryStore(String localNodeId, LongSupplier removedTtlMillisSupplier) {
        this.localNodeId = localNodeId;
        this.versionClock = new AtomicReference<>(new GossipVersion(
                System.currentTimeMillis(), 0, localNodeId));
        this.removedTtlMillisSupplier = removedTtlMillisSupplier;
    }

    public void put(String key, GossipEntityType entityType, String payload) {
        GossipDeltaEntry updated = entries.compute(key, (entryKey, previous) -> {
            GossipDeltaOperation operation = previous == null
                    || previous.operation() == GossipDeltaOperation.REMOVED
                    ? GossipDeltaOperation.ADDED
                    : GossipDeltaOperation.UPDATED;
            return new GossipDeltaEntry(entryKey, nextVersion(), entityType, operation, payload);
        });
        updateRemovedState(updated);
    }

    public void putRemoved(String key, GossipEntityType entityType, String payload) {
        GossipDeltaEntry removed = entries.compute(key, (entryKey, previous) -> new GossipDeltaEntry(
                entryKey, nextVersion(), entityType, GossipDeltaOperation.REMOVED, payload));
        updateRemovedState(removed);
    }

    public List<GossipDeltaEntry> entries() {
        return List.copyOf(entries.values());
    }

    public List<GossipDigestEntry> digest() {
        compactExpiredRemovedEntries();
        return entries.values().stream()
                .map(entry -> new GossipDigestEntry(entry.key(), entry.version()))
                .sorted(Comparator.comparing(GossipDigestEntry::key))
                .toList();
    }

    public List<String> keysNewerThan(List<GossipDigestEntry> remoteDigest) {
        Map<String, GossipVersion> remoteVersions = new HashMap<>();
        if (remoteDigest != null) {
            remoteDigest.forEach(entry -> remoteVersions.put(entry.key(), entry.version()));
        }
        return entries.values().stream()
                .filter(entry -> {
                    GossipVersion remoteVersion = remoteVersions.get(entry.key());
                    return remoteVersion == null || entry.version().compareTo(remoteVersion) > 0;
                })
                .map(GossipDeltaEntry::key)
                .toList();
    }

    public List<String> keysOlderThan(List<GossipDigestEntry> remoteDigest) {
        if (remoteDigest == null || remoteDigest.isEmpty()) {
            return List.of();
        }
        return remoteDigest.stream()
                .filter(entry -> {
                    GossipDeltaEntry local = entries.get(entry.key());
                    return local == null || local.version().compareTo(entry.version()) < 0;
                })
                .map(GossipDigestEntry::key)
                .toList();
    }

    public List<GossipDeltaEntry> deltas(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<GossipDeltaEntry> result = new ArrayList<>();
        for (String key : keys) {
            GossipDeltaEntry entry = entries.get(key);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<GossipDeltaEntry> merge(List<GossipDeltaEntry> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return List.of();
        }
        List<GossipDeltaEntry> accepted = new ArrayList<>();
        for (GossipDeltaEntry delta : deltas) {
            observe(delta.version());
            AtomicBoolean updated = new AtomicBoolean();
            entries.compute(delta.key(), (key, previous) -> {
                if (previous != null && previous.version().compareTo(delta.version()) >= 0) {
                    return previous;
                }
                updated.set(true);
                return delta;
            });
            if (updated.get()) {
                updateRemovedState(delta);
                accepted.add(delta);
            }
        }
        return accepted;
    }

    private GossipVersion nextVersion() {
        long now = System.currentTimeMillis();
        return versionClock.updateAndGet(previous -> now > previous.timestamp()
                ? new GossipVersion(now, 0, localNodeId)
                : new GossipVersion(previous.timestamp(), previous.counter() + 1, localNodeId));
    }

    private void observe(GossipVersion remoteVersion) {
        versionClock.updateAndGet(current -> {
            if (remoteVersion.timestamp() > current.timestamp()) {
                return new GossipVersion(remoteVersion.timestamp(), remoteVersion.counter(), localNodeId);
            }
            if (remoteVersion.timestamp() == current.timestamp()
                    && remoteVersion.counter() > current.counter()) {
                return new GossipVersion(current.timestamp(), remoteVersion.counter(), localNodeId);
            }
            return current;
        });
    }

    private void updateRemovedState(GossipDeltaEntry delta) {
        removedStates.compute(delta.key(), (key, previous) -> {
            GossipDeltaEntry current = entries.get(key);
            if (current == null || !current.version().equals(delta.version())) {
                return previous;
            }
            return delta.operation() == GossipDeltaOperation.REMOVED
                    ? new RemovedState(delta.version(), System.currentTimeMillis())
                    : null;
        });
    }

    private void compactExpiredRemovedEntries() {
        long removedTtlMillis = removedTtlMillisSupplier.getAsLong();
        if (removedTtlMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, RemovedState> entry : removedStates.entrySet()) {
            RemovedState removedState = entry.getValue();
            if (now - removedState.observedAt() < removedTtlMillis) {
                continue;
            }
            entries.compute(entry.getKey(), (key, current) -> {
                if (current == null
                        || current.operation() != GossipDeltaOperation.REMOVED
                        || !current.version().equals(removedState.version())) {
                    return current;
                }
                removedStates.remove(key, removedState);
                return null;
            });
        }
    }

    private record RemovedState(GossipVersion version, long observedAt) {
    }
}
