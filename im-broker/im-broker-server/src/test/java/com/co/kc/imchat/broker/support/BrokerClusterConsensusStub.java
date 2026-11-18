package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.transformer.adapter.BrokerClusterStateTransformer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 有状态测试 fixture 的查询与命令提交基线，不引入生产兼容端口。 */
public interface BrokerClusterConsensusStub {

    Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId);

    CompletionStage<BrokerClusterConsensusResult> submitCommand(BrokerClusterConsensusCommand command);

    default BrokerRaftAdapter asAdapter() {
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        doAnswer(ignored -> getCommittedState()).when(adapter).getCommittedState();
        doAnswer(ignored -> getClusterSnapshot()).when(adapter).getClusterSnapshot();
        doAnswer(invocation -> findOperation(invocation.getArgument(0)))
                .when(adapter).findOperation(any());
        doAnswer(invocation -> findActiveDrainOperation(invocation.getArgument(0)))
                .when(adapter).findActiveDrainOperation(any());
        doAnswer(invocation -> findActiveRouteRecoveryOperation(invocation.getArgument(0)))
                .when(adapter).findActiveRouteRecoveryOperation(any());
        BrokerRaftTestBindings.bindLocalBroker(adapter);
        BrokerRaftTestBindings.bindOperations(adapter, this::submitCommand);
        return adapter;
    }

    default BrokerClusterState getCommittedState() {
        throw new UnsupportedOperationException(
                "test consensus stub has no committed cluster state");
    }

    default BrokerCluster getClusterSnapshot() {
        return BrokerClusterStateTransformer.INSTANCE.brokerClusterFrom(
                getCommittedState(), List.of(), null, 1);
    }

    default Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch) {
        return Optional.empty();
    }

    default Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(RoutingEpoch targetEpoch) {
        return Optional.empty();
    }

}
