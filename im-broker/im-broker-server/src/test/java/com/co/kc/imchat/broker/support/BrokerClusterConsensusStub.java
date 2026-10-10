package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensus;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.transformer.domain.BrokerClusterDomainTransformer;

import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 不关注 Leader/恢复调度的测试可显式选用的共识端口基线。 */
public interface BrokerClusterConsensusStub extends BrokerClusterConsensus {

    @Override
    default BrokerClusterState committedState() {
        throw new UnsupportedOperationException(
                "test consensus stub has no committed cluster state");
    }

    @Override
    default BrokerCluster getCluster() {
        return BrokerClusterDomainTransformer.INSTANCE.restoreAggregate(
                committedState(), List.of(), null, 1);
    }

    @Override
    default Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch) {
        return Optional.empty();
    }

    @Override
    default Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(RoutingEpoch targetEpoch) {
        return Optional.empty();
    }

    @Override
    default LocalBroker getLocalBroker() {
        LocalBroker broker = mock(LocalBroker.class);
        when(broker.isLeader()).thenReturn(true);
        return broker;
    }
}
