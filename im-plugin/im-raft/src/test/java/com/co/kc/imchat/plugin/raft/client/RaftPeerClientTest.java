package com.co.kc.imchat.plugin.raft.client;

import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RaftPeerClientTest {
    private final BoltInvoker invoker = mock(BoltInvoker.class);
    private final RaftPeerClient client = new RaftPeerClient(invoker);

    @Test
    void forwardsPeerReadIndexQueryWithoutDependingOnBusinessModels() {
        ReadRequest request = new ReadRequest("cluster-a");
        ReadResponse response = new ReadResponse(12L);
        when(invoker.invoke("127.0.0.1:18020", "peer", "readIndex", request, ReadResponse.class, 1200))
                .thenReturn(response);

        ReadResponse actual = client.fetchQuorumReadIndex(
                "127.0.0.1:18020", "peer", "readIndex", request, ReadResponse.class, 1200);

        assertThat(actual).isSameAs(response);
        verify(invoker).invoke("127.0.0.1:18020", "peer", "readIndex", request, ReadResponse.class, 1200);
    }

    @Test
    void propagatesTransportFailureWithoutInventingProgress() {
        ReadRequest request = new ReadRequest("cluster-a");
        IllegalStateException failure = new IllegalStateException("peer unavailable");
        when(invoker.invoke("127.0.0.1:18020", "peer", "readIndex", request, ReadResponse.class, 1200))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.fetchQuorumReadIndex(
                "127.0.0.1:18020", "peer", "readIndex", request, ReadResponse.class, 1200))
                .isSameAs(failure);
    }

    private record ReadRequest(String clusterId) {
    }

    private record ReadResponse(long fetchQuorumReadIndex) {
    }
}
