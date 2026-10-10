package com.co.kc.imchat.broker.application.cluster;

import java.time.Duration;



import com.co.kc.imchat.broker.model.cqrs.command.ActivateBrokerCommand;
import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import java.util.function.LongFunction;
import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusStatus;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.transformer.domain.BrokerClusterDomainTransformer;
import com.co.kc.imchat.broker.transformer.interfaces.BrokerManagementHttpTransformer;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState;
import com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import java.util.concurrent.CompletableFuture;

class BrokerClusterAppServiceTest {

    @Test
    void orchestratesTheConcreteRaftAdapterWithoutConsensusPortAbstraction() {
        assertThat(BrokerClusterAppService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .contains(BrokerClusterAdapter.class)
                        .doesNotContain(BrokerClusterConsensus.class));
    }

    @Test
    void activationSubmitsValidatedClusterChange() {
        BrokerClusterAdapter repository = mock(BrokerClusterAdapter.class);
        BrokerClusterState state = state();
        when(repository.getCluster()).thenReturn(
                BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(
                        state, List.of(), null, 1));
        when(repository.registerOperation(any())).thenReturn(CompletableFuture.completedFuture(
                new BrokerClusterConsensusResult(BrokerClusterConsensusStatus.NOT_LEADER, null, null, "not leader")));
        BrokerClusterAppService service =
                new BrokerClusterAppService(repository,
                        mock(GatewayRegistry.class),
                        Duration.ofNanos(1_000L), Duration.ofSeconds(1), mock(java.util.concurrent.ScheduledExecutorService.class), java.time.Duration.ofSeconds(10));

        ActivateBrokerResult result = service.activate(
                BrokerManagementHttpTransformer.INSTANCE.activateCommand("operation-1", "broker-1", 11L))
                .toCompletableFuture().join();
        assertThat(result.status()).isEqualTo("NOT_LEADER");
        verify(repository).registerOperation(any());
    }

    @Test
    void rejectsMissingRequiredBoundaryValue() {
        BrokerClusterAdapter repository = mock(BrokerClusterAdapter.class);
        BrokerClusterAppService service =
                new BrokerClusterAppService(repository,
                        mock(GatewayRegistry.class),
                        Duration.ofNanos(1_000L), Duration.ofSeconds(1), mock(java.util.concurrent.ScheduledExecutorService.class), java.time.Duration.ofSeconds(10));

        assertThatThrownBy(() -> BrokerManagementHttpTransformer.INSTANCE.activateCommand(
                "operation-1", "broker-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("routing epoch is required");
        verify(repository, never()).getCluster();
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
