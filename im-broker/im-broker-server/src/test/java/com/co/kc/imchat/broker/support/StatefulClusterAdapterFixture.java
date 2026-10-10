package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.domain.authority.model.QuorumReadNonce;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.adapter.cluster.raft.RaftBrokerClusterStateMachine;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembership;
import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerConsensusMembershipResult;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.domain.authority.model.QuorumRead;
import com.co.kc.imchat.broker.domain.authority.service.RoutingLeaseGrantService;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.broker.transformer.infrastructure.BrokerClusterRaftTransformer;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;

/** 在测试中通过真实领域状态机应用日志，外部 membership 调用只替换为受控结果。 */
public final class StatefulClusterAdapterFixture {
    public final RaftBrokerClusterStateMachine stateMachine;
    public final BrokerClusterAdapter adapter;
    public final LocalBroker localBroker;
    public final Set<String> voters = new LinkedHashSet<>();
    public final Set<String> learners = new LinkedHashSet<>();

    public StatefulClusterAdapterFixture(BrokerClusterState state) {
        stateMachine = new RaftBrokerClusterStateMachine("cluster-a", state);
        adapter = mock(BrokerClusterAdapter.class, CALLS_REAL_METHODS);
        localBroker = mock(LocalBroker.class);
        doReturn(localBroker).when(adapter).getLocalBroker();
        doReturn(true).when(localBroker).isLeader();
        doReturn(CompletableFuture.completedFuture(1L)).when(adapter).fetchQuorumReadIndex(any());
        doReturn(null).when(localBroker).leaderEndpoint();
        doAnswer(ignored -> stateMachine.getCluster()).when(adapter).getCluster();
        RoutingLeaseGrantService grantService = new RoutingLeaseGrantService(java.time.Duration.ofSeconds(1));
        doAnswer(invocation -> stateMachine.grantLease(invocation.getArgument(0), invocation.getArgument(1), grantService))
                .when(adapter).grantLease(any(), any());
        doAnswer(ignored -> stateMachine.committedState()).when(adapter).committedState();
        doAnswer(ignored -> stateMachine.findRoutingFence()).when(adapter).findRoutingFence();
        doAnswer(invocation -> stateMachine.findOperation(invocation.getArgument(0)))
                .when(adapter).findOperation(any(ClusterOperationId.class));
        doAnswer(invocation -> CompletableFuture.completedFuture(stateMachine.apply(
                invocation.getArgument(0)))).when(adapter).submitChange(any(BrokerClusterConsensusCommand.class));
        doAnswer(invocation -> {
            BrokerClusterOperation operation = invocation.getArgument(0);
            Instant changedAt = invocation.getArgument(1);
            return CompletableFuture.completedFuture(stateMachine.apply(
                    BrokerClusterRaftTransformer.INSTANCE.commitCommandFrom(
                            operation, changedAt)));
        }).when(adapter).commitOperation(any(), any());
        doAnswer(ignored -> membership()).when(adapter).consensusMembership();
        doAnswer(ignored -> CompletableFuture.completedFuture(new QuorumRead(term(1), readIndex(1))))
                .when(adapter).confirmLeadership(any(QuorumReadNonce.class));
        doAnswer(invocation -> {
            learners.add(((MembershipEndpoint) invocation.getArgument(0)).value());
            return applied();
        }).when(adapter).addLearner(any(MembershipEndpoint.class));
        doAnswer(invocation -> {
            String endpoint = ((MembershipEndpoint) invocation.getArgument(0)).value();
            learners.remove(endpoint);
            voters.add(endpoint);
            return applied();
        }).when(adapter).promoteLearner(any(MembershipEndpoint.class));
        doAnswer(invocation -> {
            String endpoint = ((MembershipEndpoint) invocation.getArgument(0)).value();
            voters.remove(endpoint);
            learners.add(endpoint);
            return applied();
        }).when(adapter).demoteVoter(any(MembershipEndpoint.class));
        doAnswer(invocation -> {
            String endpoint = ((MembershipEndpoint) invocation.getArgument(0)).value();
            voters.remove(endpoint);
            learners.remove(endpoint);
            return applied();
        }).when(adapter).removeMember(any(MembershipEndpoint.class));
    }

    private BrokerConsensusMembership membership() {
        return new BrokerConsensusMembership(voters.stream().toList(), learners.stream().toList());
    }

    private CompletableFuture<BrokerConsensusMembershipResult> applied() {
        return CompletableFuture.completedFuture(new BrokerConsensusMembershipResult(BrokerConsensusMembershipResult.Status.APPLIED, null, null));
    }
}
