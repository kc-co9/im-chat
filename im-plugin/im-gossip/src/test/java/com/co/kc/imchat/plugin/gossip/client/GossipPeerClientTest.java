package com.co.kc.imchat.plugin.gossip.client;

import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDeltaParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipDigestParams;
import com.co.kc.imchat.plugin.gossip.model.result.GossipDigestResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GossipPeerClientTest {
    @Test
    void digestAndDeltaRetainTheSameImmutableTarget() {
        BoltInvoker invoker = mock(BoltInvoker.class);
        ImBoltProperties.Client policy = new ImBoltProperties.Client();
        policy.getTls().setEnabled(true);
        policy.getTls().setKeyStore("explicit-test-contract.p12");
        policy.getTls().setKeyStorePassword("test-contract-only");
        policy.setAllowedPeerUriPrefixes(List.of("spiffe://test/peer/"));
        GossipPeerClient client = new GossipPeerClient(new BoltRpcClient(invoker, policy));
        BoltTarget target = new BoltTarget("peer:12200", "spiffe://test/peer/exact-node");
        GossipDigestParams digest = new GossipDigestParams("local", List.of());
        GossipDeltaParams delta = new GossipDeltaParams("local", List.of());
        GossipDigestResult expected = new GossipDigestResult(List.of(), List.of());
        when(invoker.invoke(same(target), eq("sync"), eq("digest"), same(digest), eq(GossipDigestResult.class), eq(3000)))
                .thenReturn(expected);
        assertThat(client.exchangeDigest(target, "sync", "digest", digest, 3000)).isSameAs(expected);
        client.pushDeltas(target, "sync", "delta", delta, 3000);
        ArgumentCaptor<BoltTarget> targets = ArgumentCaptor.forClass(BoltTarget.class);
        verify(invoker).invoke(targets.capture(), eq("sync"), eq("digest"), same(digest), eq(GossipDigestResult.class), eq(3000));
        verify(invoker).invoke(targets.capture(), eq("sync"), eq("delta"), same(delta), eq(Void.class), eq(3000));
        assertThat(targets.getAllValues()).hasSize(2).allSatisfy(captured -> assertThat(captured).isSameAs(target));
        verifyNoMoreInteractions(invoker);
    }
}
