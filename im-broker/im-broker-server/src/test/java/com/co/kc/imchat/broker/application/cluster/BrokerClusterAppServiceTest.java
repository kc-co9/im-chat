package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerClusterAppServiceTest {

    @Test
    void orchestratesTheConcreteRaftAdapterWithoutConsensusPortAbstraction() {
        assertThat(BrokerClusterAppService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(BrokerRaftAdapter.class));
    }

    @Test
    void activationSubmitsValidatedClusterChange() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        BrokerClusterState state = state();
        when(repository.getClusterSnapshot()).thenReturn(
                BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(
                        state, List.of(), null, 1));
        when(repository.beginOperation(any())).thenReturn(CompletableFuture.completedFuture(
                new BrokerClusterConsensusResult(Status.NOT_LEADER, null, null, "not leader")));
        BrokerClusterAppService service =
                new BrokerClusterAppService(repository,
                mock(BrokerPeerAdapter.class),
                        mock(GatewayRegistry.class),
                        Duration.ofNanos(1_000L), Duration.ofSeconds(1), mock(ScheduledExecutorService.class), Duration.ofSeconds(10), null, null, Duration.ofSeconds(60));

        ActivateBrokerResult result = service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("operation-1", "broker-1", 11L))
                .toCompletableFuture().join();
        assertThat(result.status()).isEqualTo("NOT_LEADER");
        verify(repository).beginOperation(any());
    }

    @Test
    void rejectsMissingRequiredBoundaryValue() {
        BrokerRaftAdapter repository = mock(BrokerRaftAdapter.class);
        BrokerClusterAppService service =
                new BrokerClusterAppService(repository,
                mock(BrokerPeerAdapter.class),
                        mock(GatewayRegistry.class),
                        Duration.ofNanos(1_000L), Duration.ofSeconds(1), mock(ScheduledExecutorService.class), Duration.ofSeconds(10), null, null, Duration.ofSeconds(60));

        assertThatThrownBy(() -> BrokerManagementHttpTransformer.INSTANCE.activateCommand(
                "operation-1", "broker-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("routing epoch is required");
        verify(repository, never()).getClusterSnapshot();
    }

    private BrokerClusterState state() {
        return new BrokerClusterState(7L, 11L,
                BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                List.of(new BrokerNode("broker-1", "127.0.0.1:18020",
                                BrokerMembershipRole.LEARNER, BrokerRoutingState.READY),
                        new BrokerNode("broker-2", "127.0.0.1:18021",
                                BrokerMembershipRole.VOTER, BrokerRoutingState.ACTIVE)));
    }
}
