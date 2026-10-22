package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.support.BrokerRaftTestBindings;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
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
        BrokerRaftAdapter raftAdapter = mock(BrokerRaftAdapter.class);
        when(BrokerRaftTestBindings.bindLocalBroker(raftAdapter).getId()).thenReturn(new BrokerId("broker-b"));
        CompletableFuture<Long> applied = new CompletableFuture<>();
        when(raftAdapter.fetchQuorumReadIndex()).thenReturn(applied);
        BrokerQuorumAppService service = new BrokerQuorumAppService(raftAdapter);

        CompletableFuture<QuorumReadIndexResult> response = service.fetchQuorumReadIndex().toCompletableFuture();
        assertThat(response).isNotDone();
        applied.complete(12L);
        assertThat(response.join()).isEqualTo(new QuorumReadIndexResult("broker-b", 12L));
    }

    @Test
    void consensusFailureIsNotReplacedByFakeProgress() {
        BrokerRaftAdapter raftAdapter = mock(BrokerRaftAdapter.class);
        IllegalStateException failure = new IllegalStateException("quorum unavailable");
        when(raftAdapter.fetchQuorumReadIndex()).thenReturn(CompletableFuture.failedFuture(failure));
        BrokerQuorumAppService service = new BrokerQuorumAppService(raftAdapter);

        assertThatThrownBy(() -> service.fetchQuorumReadIndex().toCompletableFuture().join()).hasCause(failure);
    }
}
