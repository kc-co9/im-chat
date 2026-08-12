package com.co.kc.imchat.plugin.gossip.store;

import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.GossipEntityType;
import com.co.kc.imchat.plugin.gossip.model.GossipVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryGossipEntryStoreTest {

    @Test
    void putCreatesAddedEntry() {
        InMemoryGossipEntryStore store = store("node-1", 0);

        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{}");

        GossipDeltaEntry entry = store.entries().get(0);
        assertEquals(GossipEntityType.BROKER, entry.entityType());
        assertEquals(GossipDeltaOperation.ADDED, entry.operation());
    }

    @Test
    void putExistingEntryCreatesUpdatedEntry() {
        InMemoryGossipEntryStore store = store("node-1", 0);

        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{}");
        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{\"port\":12200}");

        assertEquals(GossipDeltaOperation.UPDATED, store.entries().get(0).operation());
    }

    @Test
    void putAfterRemovedEntryCreatesAddedEntry() {
        InMemoryGossipEntryStore store = store("node-1", 0);

        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{}");
        store.putRemoved("BROKER:broker-1", GossipEntityType.BROKER, "null");
        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{\"port\":12200}");

        assertEquals(GossipDeltaOperation.ADDED, store.entries().get(0).operation());
    }

    @Test
    void removedEntryCanBeCompacted() throws InterruptedException {
        InMemoryGossipEntryStore store = store("node-1", 1);

        store.putRemoved("BROKER:broker-1", GossipEntityType.BROKER, "null");
        Thread.sleep(5);
        List<?> digest = store.digest();

        assertEquals(List.of(), digest);
    }

    @Test
    void localUpdateAdvancesPastMergedRemoteVersion() {
        InMemoryGossipEntryStore store = store("node-1", 0);
        GossipVersion remoteVersion = new GossipVersion(
                System.currentTimeMillis() + 60_000, 10, "node-2");
        store.merge(List.of(new GossipDeltaEntry(
                "BROKER:broker-1", remoteVersion, GossipEntityType.BROKER,
                GossipDeltaOperation.ADDED, "{}")));

        store.put("BROKER:broker-1", GossipEntityType.BROKER, "{\"port\":12200}");

        GossipDeltaEntry updated = store.entries().get(0);
        assertEquals(GossipDeltaOperation.UPDATED, updated.operation());
        assertTrue(updated.version().compareTo(remoteVersion) > 0);
    }

    @Test
    void sameLogicalTimeUsesNodeIdAsTieBreaker() {
        GossipVersion first = new GossipVersion(1000, 1, "node-1");
        GossipVersion second = new GossipVersion(1000, 1, "node-2");

        assertTrue(second.compareTo(first) > 0);
    }

    @Test
    void mergeKeepsNewestVersion() {
        InMemoryGossipEntryStore store = store("node-1", 0);
        GossipDeltaEntry newest = new GossipDeltaEntry(
                "BROKER:broker-1", new GossipVersion(2000, 0, "node-2"),
                GossipEntityType.BROKER, GossipDeltaOperation.UPDATED, "new");
        GossipDeltaEntry older = new GossipDeltaEntry(
                "BROKER:broker-1", new GossipVersion(1000, 0, "node-3"),
                GossipEntityType.BROKER, GossipDeltaOperation.ADDED, "old");

        store.merge(List.of(newest));
        store.merge(List.of(older));

        assertEquals(newest, store.entries().get(0));
    }

    @Test
    void concurrentMergeKeepsNewestVersion() {
        InMemoryGossipEntryStore store = store("node-1", 0);
        List<GossipDeltaEntry> deltas = LongStream.rangeClosed(1, 1000)
                .mapToObj(version -> new GossipDeltaEntry(
                        "BROKER:broker-1", new GossipVersion(version, 0, "node-2"),
                        GossipEntityType.BROKER, GossipDeltaOperation.UPDATED, Long.toString(version)))
                .toList();

        deltas.parallelStream().forEach(delta -> store.merge(List.of(delta)));

        assertEquals(new GossipVersion(1000, 0, "node-2"), store.entries().get(0).version());
    }

    private InMemoryGossipEntryStore store(String nodeId, long removedTtlMillis) {
        return new InMemoryGossipEntryStore(nodeId, () -> removedTtlMillis);
    }
}
