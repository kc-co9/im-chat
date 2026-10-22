package com.co.kc.imchat.broker.domain.cluster.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerMembershipChangeTest {
    private static final Set<BrokerClusterOperationType> MEMBERSHIP_CHANGES = EnumSet.of(
            BrokerClusterOperationType.ADD_LEARNER, BrokerClusterOperationType.START_LEARNER_SYNC,
            BrokerClusterOperationType.COMPLETE_LEARNER_SYNC, BrokerClusterOperationType.PROMOTE_LEARNER,
            BrokerClusterOperationType.PROMOTE_BROKER, BrokerClusterOperationType.DEMOTE_VOTER,
            BrokerClusterOperationType.DEMOTE_BROKER, BrokerClusterOperationType.REMOVE_BROKER);

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationType.class)
    void typeIdentifiesExactlyTheMembershipChanges(BrokerClusterOperationType type) {
        assertThat(type.membershipChange()).isEqualTo(MEMBERSHIP_CHANGES.contains(type));
    }

    @ParameterizedTest
    @EnumSource(BrokerClusterOperationPhase.class)
    void onlyUnfinishedMembershipChangesAreInProgress(BrokerClusterOperationPhase phase) {
        BrokerClusterOperation membership = operation(BrokerClusterOperationType.DEMOTE_VOTER, phase);
        BrokerClusterOperation routing = operation(BrokerClusterOperationType.ACTIVATE_BROKER, phase);
        boolean unfinished = switch (phase) {
            case SUCCEEDED, DEGRADED, FAILED, FORCED -> false;
            default -> true;
        };
        assertThat(membership.isUnfinishedMembershipChange()).isEqualTo(unfinished);
        assertThat(routing.isUnfinishedMembershipChange()).isFalse();
    }

    private BrokerClusterOperation operation(BrokerClusterOperationType type, BrokerClusterOperationPhase phase) {
        return BrokerClusterOperation.builder()
                .id(new ClusterOperationId("membership-change"))
                .commandDigest(new CommandDigest("digest"))
                .type(type).targetBrokerId(new BrokerId("broker-a"))
                .acceptedEpoch(new RoutingEpoch(0L)).resultEpoch(new RoutingEpoch(0L))
                .acceptedRevision(new ClusterRevision(0L)).phase(phase)
                .minimumVoterCount(type == BrokerClusterOperationType.DEMOTE_VOTER ? new MinimumVoterCount(1) : null)
                .createdAt(Instant.EPOCH).build();
    }
}
