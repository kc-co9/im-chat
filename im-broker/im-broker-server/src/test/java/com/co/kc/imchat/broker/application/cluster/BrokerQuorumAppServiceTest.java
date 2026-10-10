package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.sdk.model.result.QuorumReadIndexResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerQuorumAppServiceTest {
    @Test
    void waitsForAppliedIndexAndReturnsConfiguredIdentity() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        LocalBroker localBroker = mock(LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        when(localBroker.nodeId()).thenReturn("broker-b");
        CompletableFuture<Long> applied = new CompletableFuture<>();
        when(localBroker.fetchQuorumReadIndex()).thenReturn(applied);
        BrokerQuorumAppService service = new BrokerQuorumAppService(adapter);

        CompletableFuture<QuorumReadIndexResult> response = service.fetchQuorumReadIndex().toCompletableFuture();
        assertThat(response).isNotDone();
        applied.complete(12L);
        assertThat(response.join()).isEqualTo(new QuorumReadIndexResult("broker-b", 12L));
    }

    @Test
    void consensusFailureIsNotReplacedByFakeProgress() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        LocalBroker localBroker = mock(LocalBroker.class);
        when(adapter.getLocalBroker()).thenReturn(localBroker);
        IllegalStateException failure = new IllegalStateException("quorum unavailable");
        when(localBroker.fetchQuorumReadIndex()).thenReturn(CompletableFuture.failedFuture(failure));
        BrokerQuorumAppService service = new BrokerQuorumAppService(adapter);

        assertThatThrownBy(() -> service.fetchQuorumReadIndex().toCompletableFuture().join()).hasCause(failure);
    }
}
