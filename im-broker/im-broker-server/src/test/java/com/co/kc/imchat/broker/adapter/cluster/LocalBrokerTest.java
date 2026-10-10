package com.co.kc.imchat.broker.adapter.cluster;

import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.plugin.raft.core.RaftRuntime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalBrokerTest {
    @SuppressWarnings("unchecked")
    private final RaftRuntime<BrokerClusterConsensusResult> runtime = mock(RaftRuntime.class);
    private final LocalBroker localBroker = new LocalBroker(runtime);

    @Test
    void roleAndKnownLeaderAreReadFromCurrentRuntimeWithoutCaching() {
        when(runtime.isLeader()).thenReturn(true, false);
        when(runtime.leaderEndpoint()).thenReturn("127.0.0.1:19001", null);
        assertThat(localBroker.isLeader()).isTrue();
        assertThat(localBroker.isLeader()).isFalse();
        assertThat(localBroker.leaderEndpoint()).isEqualTo("127.0.0.1:19001");
        assertThat(localBroker.leaderEndpoint()).isNull();
    }

    @Test
    void appliedIndexKeepsRuntimeFutureAndGeneratesOpaqueReadContext() {
        CompletableFuture<Long> applied = new CompletableFuture<>();
        when(runtime.fetchQuorumReadIndex(any())).thenReturn(applied);
        assertThat(localBroker.fetchQuorumReadIndex()).isSameAs(applied);
        assertThat(applied).isNotDone();
        ArgumentCaptor<byte[]> context = ArgumentCaptor.forClass(byte[].class);
        verify(runtime).fetchQuorumReadIndex(context.capture());
        assertThat(new String(context.getValue(), StandardCharsets.UTF_8)).matches("[0-9a-f]{32}");
        applied.complete(12L);
        assertThat(applied.join()).isEqualTo(12L);
    }

    @Test
    void quorumFailureIsNotConvertedIntoFakeProgress() {
        IllegalStateException failure = new IllegalStateException("quorum unavailable");
        when(runtime.fetchQuorumReadIndex(any())).thenReturn(CompletableFuture.failedFuture(failure));
        assertThatThrownBy(() -> localBroker.fetchQuorumReadIndex().toCompletableFuture().join()).hasCause(failure);
    }
}
