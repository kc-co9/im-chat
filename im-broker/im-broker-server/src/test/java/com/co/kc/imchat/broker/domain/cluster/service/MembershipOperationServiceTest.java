package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.repository.LearnerConfirmationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class MembershipOperationServiceTest {
    @Test
    void executionReporterRequiresExactTrustedIdentity() {
        MembershipOperationService service = new MembershipOperationService(
                mock(LearnerConfirmationAttemptRepository.class), new InMemoryMembershipOperationAttemptRepository());
        BrokerId executor = new BrokerId("executor");
        BrokerId other = new BrokerId("other");
        assertThat(service.matchesExecutionReporter(executor, executor, executor)).isTrue();
        assertThat(service.matchesExecutionReporter(other, executor, executor)).isFalse();
        assertThat(service.matchesExecutionReporter(executor, other, executor)).isFalse();
        assertThat(service.matchesExecutionReporter(executor, executor, other)).isFalse();
    }

    @Test
    void newOccupantAcquiresExecutionAndReleaseUsesExactAttempt() {
        LearnerConfirmationAttemptRepository repository = mock(LearnerConfirmationAttemptRepository.class);
        LearnerConfirmationIdentity identity = identity("sync", "b", 1L, "b:18120");
        when(repository.registerIfAbsent(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> call.getArgument(0));
        MembershipOperationService service = new MembershipOperationService(repository, new InMemoryMembershipOperationAttemptRepository());
        LearnerConfirmationClaim claim = service.claimLearnerConfirmation(identity);
        assertThat(claim.status()).isEqualTo(LearnerConfirmationClaim.Status.ACQUIRED);
        assertThat(claim.executionAttempt().identity()).isEqualTo(identity);
        service.releaseLearnerConfirmation(claim.executionAttempt());
        verify(repository).removeIfSame(claim.executionAttempt());
    }

    @Test
    void sameIdentityReportsProgressWithoutExecutionAttempt() {
        LearnerConfirmationAttemptRepository repository = mock(LearnerConfirmationAttemptRepository.class);
        LearnerConfirmationIdentity identity = identity("sync", "b", 1L, "b:18120");
        when(repository.registerIfAbsent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LearnerConfirmationAttempt(identity));
        LearnerConfirmationClaim claim = new MembershipOperationService(repository, new InMemoryMembershipOperationAttemptRepository()).claimLearnerConfirmation(identity);
        assertThat(claim.status()).isEqualTo(LearnerConfirmationClaim.Status.IN_PROGRESS);
        assertThat(claim.executionAttempt()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"other,1,b:18120", "b,2,b:18120", "b,1,other:18120"})
    void differentIdentityFieldConflicts(String brokerId, long epoch, String endpoint) {
        LearnerConfirmationAttemptRepository repository = mock(LearnerConfirmationAttemptRepository.class);
        when(repository.registerIfAbsent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LearnerConfirmationAttempt(identity("sync", "b", 1L, "b:18120")));
        LearnerConfirmationClaim claim = new MembershipOperationService(repository, new InMemoryMembershipOperationAttemptRepository())
                .claimLearnerConfirmation(identity("sync", brokerId, epoch, endpoint));
        assertThat(claim.status()).isEqualTo(LearnerConfirmationClaim.Status.CONFLICT);
        assertThat(claim.executionAttempt()).isNull();
    }

    @Test
    void claimRejectsInconsistentStatusAndAttempt() {
        LearnerConfirmationAttempt attempt = new LearnerConfirmationAttempt(identity("sync", "b", 1L, "b:18120"));
        assertThatThrownBy(() -> new LearnerConfirmationClaim(LearnerConfirmationClaim.Status.ACQUIRED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("learner claim attempt does not match status");
        assertThatThrownBy(() -> new LearnerConfirmationClaim(LearnerConfirmationClaim.Status.IN_PROGRESS, attempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("learner claim attempt does not match status");
        assertThatThrownBy(() -> new LearnerConfirmationIdentity(null, new BrokerId("b"),
                new RoutingEpoch(1L), new MembershipEndpoint("b:18120")))
                .isInstanceOf(IllegalStateException.class).hasMessage("learner confirmation identity is required");
    }

    private static LearnerConfirmationIdentity identity(String id, String broker, long epoch, String endpoint) {
        return new LearnerConfirmationIdentity(new ClusterOperationId(id), new BrokerId(broker),
                new RoutingEpoch(epoch), new MembershipEndpoint(endpoint));
    }
}
