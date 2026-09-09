package com.co.kc.imchat.plugin.gossip.sync;

import com.co.kc.imchat.plugin.gossip.client.GossipPeerClient;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipDeltaOperation;
import com.co.kc.imchat.plugin.gossip.model.GossipDigestEntry;
import com.co.kc.imchat.plugin.gossip.model.GossipEntityType;
import com.co.kc.imchat.plugin.gossip.model.GossipVersion;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GossipSynchronizerTest {

    @Test
    void mergesRemoteDeltasAndPushesRequestedLocalDeltas() {
        GossipSyncStore syncStore = mock(GossipSyncStore.class);
        GossipPeerClient peerClient = mock(GossipPeerClient.class);
        GossipSynchronizer synchronizer = new GossipSynchronizer(syncStore, peerClient);
        GossipSyncOperations operations = new GossipSyncOperations("broker.sync", "digest", "delta");
        GossipDigestEntry digest = new GossipDigestEntry(
                "BROKER:broker-1", new GossipVersion(1000, 0, "node-1"));
        GossipDeltaEntry remoteDelta = delta("BROKER:broker-2", "node-2", 2000);
        GossipDeltaEntry localDelta = delta("BROKER:broker-1", "node-1", 1000);
        when(syncStore.digest()).thenReturn(List.of(digest));
        when(peerClient.exchangeDigest(
                "127.0.0.1:12200", "broker.sync", "digest",
                new GossipDigestParams("node-1", List.of(digest)), 3000))
                .thenReturn(new GossipDigestResult(List.of(remoteDelta), List.of(localDelta.key())));
        when(syncStore.deltas(List.of(localDelta.key()))).thenReturn(List.of(localDelta));

        Integer processedCount = synchronizer.syncPeer(
                "node-1",
                "127.0.0.1:12200",
                operations,
                3000);

        verify(syncStore).merge(List.of(remoteDelta));
        verify(peerClient).pushDeltas(
                "127.0.0.1:12200", "broker.sync", "delta",
                new GossipDeltaParams("node-1", List.of(localDelta)), 3000);
        assertThat(processedCount).isEqualTo(2);
    }

    private GossipDeltaEntry delta(String key, String nodeId, long timestamp) {
        return new GossipDeltaEntry(key, new GossipVersion(timestamp, 0, nodeId),
                GossipEntityType.BROKER, GossipDeltaOperation.UPDATED, "{}");
    }
}
