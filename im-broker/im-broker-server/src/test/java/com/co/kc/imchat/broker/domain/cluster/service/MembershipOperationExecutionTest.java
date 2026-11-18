package com.co.kc.imchat.broker.domain.cluster.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationPhase;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperationType;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.CommandDigest;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationClaim;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipOperationOutcome;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationClaim;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MembershipOperationExecutionTest {
    @Test
    void sameIdentitySharesFixedViewAndDifferentDigestConflicts() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        MembershipOperationClaim shared = service.claimMembershipOperation(operation("one", "digest"));
        assertThat(first.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        assertThat(shared.status()).isEqualTo(MembershipOperationClaim.Status.SHARED);
        assertThat((Object) shared.resultView()).isSameAs(first.resultView());
        MembershipOperationClaim conflict = service.claimMembershipOperation(operation("one", "other"));
        assertThat(conflict.status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
        assertThat(conflict.resultView().toCompletableFuture().join().status()).isEqualTo(MembershipOperationOutcome.Status.REJECTED);
    }

    @Test
    void releasePrecedesNotificationAndOldCallbacksCannotDeleteReplacement() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        CompletionStage<MembershipOperationClaim> observed = first.resultView().thenApply(result -> {
            assertThat(Thread.holdsLock(service)).isFalse();
            return service.claimMembershipOperation(operation("one", "other"));
        });
        service.completeMembershipOperation(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        MembershipOperationClaim replacement = observed.toCompletableFuture().join();
        assertThat(replacement.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        service.completeMembershipOperation(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        service.rejectMembershipAdmission(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));
        assertThat((Object) service.claimMembershipOperation(operation("one", "other")).resultView()).isSameAs(replacement.resultView());
        assertThat(replacement.resultView().toCompletableFuture()).isNotDone();
    }

    @Test
    void retainedIdentitySurvivesReplacementAndRejection() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        first.executionAttempt().retainIdentity();
        service.completeMembershipOperation(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        MembershipOperationClaim retry = service.claimMembershipOperation(operation("one", "digest"));
        assertThat(retry.executionAttempt().identityRetained()).isTrue();
        service.completeMembershipOperation(retry.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        assertThat(service.claimMembershipOperation(operation("one", "other")).status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
    }

    @Test
    void unknownOutcomeRetainsDigestWithoutCachingCompletedResult() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        service.completeMembershipOperation(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));
        assertThat(service.claimMembershipOperation(operation("one", "other")).status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
        MembershipOperationClaim retry = service.claimMembershipOperation(operation("one", "digest"));
        assertThat(retry.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        assertThat((Object) retry.resultView()).isNotSameAs(first.resultView());
    }

    @Test
    void initialAdmissionRejectionReleasesUnretainedIdentity() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        service.rejectMembershipAdmission(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));
        assertThat(first.resultView().toCompletableFuture().join().status()).isEqualTo(MembershipOperationOutcome.Status.UNAVAILABLE);
        assertThat(service.claimMembershipOperation(operation("one", "other")).status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
    }

    @Test
    void retryAdmissionRejectionKeepsPreviouslySubmittedIdentity() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        first.executionAttempt().retainIdentity();
        service.completeMembershipOperation(first.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));

        MembershipOperationClaim retry = service.claimMembershipOperation(operation("one", "digest"));
        assertThat(retry.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        service.rejectMembershipAdmission(retry.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));

        assertThat(retry.resultView().toCompletableFuture().join().status()).isEqualTo(MembershipOperationOutcome.Status.UNAVAILABLE);
        assertThat(service.claimMembershipOperation(operation("one", "other")).status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
        assertThat(service.claimMembershipOperation(operation("one", "digest")).status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
    }

    @Test
    void associatedOperationPreventsRejectionFromReleasingIdentity() {
        MembershipOperationService service = service();
        BrokerClusterOperation operation = operation("one", "digest");
        MembershipOperationClaim first = service.claimMembershipOperation(operation);
        service.completeMembershipOperation(first.executionAttempt(), new MembershipOperationOutcome(
                MembershipOperationOutcome.Status.REJECTED, operation, null, "rejected"));
        assertThat(service.claimMembershipOperation(operation("one", "other")).status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
    }

    @Test
    void fullCapacityRejectsNewIdentityButAllowsSameIdentityRetryAndRelease() {
        MembershipOperationService service = service();
        for (int index = 0; index < 4096; index++) {
            MembershipOperationClaim claim = service.claimMembershipOperation(operation("id-" + index, "digest"));
            assertThat(claim.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
            service.completeMembershipOperation(claim.executionAttempt(), outcome(MembershipOperationOutcome.Status.UNAVAILABLE));
        }
        MembershipOperationClaim full = service.claimMembershipOperation(operation("new", "digest"));
        assertThat(full.status()).isEqualTo(MembershipOperationClaim.Status.CAPACITY_EXCEEDED);
        assertThat(full.resultView().toCompletableFuture().join().status()).isEqualTo(MembershipOperationOutcome.Status.UNAVAILABLE);
        assertThat(service.claimMembershipOperation(operation("id-0", "other")).status()).isEqualTo(MembershipOperationClaim.Status.CONFLICT);
        MembershipOperationClaim retry = service.claimMembershipOperation(operation("id-0", "digest"));
        assertThat(retry.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        service.completeMembershipOperation(retry.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        assertThat(service.claimMembershipOperation(operation("new", "digest")).status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
    }

    @Test
    void callerCancellationDoesNotCancelSharedExecution() {
        MembershipOperationService service = service();
        MembershipOperationClaim first = service.claimMembershipOperation(operation("one", "digest"));
        CompletableFuture<MembershipOperationOutcome> cancelled = first.resultView().toCompletableFuture();
        assertThat(cancelled.cancel(false)).isTrue();
        MembershipOperationClaim shared = service.claimMembershipOperation(operation("one", "digest"));
        assertThat(shared.status()).isEqualTo(MembershipOperationClaim.Status.SHARED);
        MembershipOperationOutcome result = outcome(MembershipOperationOutcome.Status.UNAVAILABLE);
        service.completeMembershipOperation(first.executionAttempt(), result);
        assertThat(shared.resultView().toCompletableFuture().join()).isSameAs(result);
    }

    @Test
    void sameIdLearnerAndMemberRemainIndependent() {
        MembershipOperationService service = service();
        LearnerConfirmationIdentity identity = new LearnerConfirmationIdentity(new ClusterOperationId("same"),
                new BrokerId("broker"), new RoutingEpoch(1L), new MembershipEndpoint("broker:18120"));
        LearnerConfirmationClaim learner = service.claimLearnerConfirmation(identity);
        MembershipOperationClaim member = service.claimMembershipOperation(operation("same", "digest"));
        assertThat(learner.status()).isEqualTo(LearnerConfirmationClaim.Status.ACQUIRED);
        assertThat(member.status()).isEqualTo(MembershipOperationClaim.Status.ACQUIRED);
        service.completeMembershipOperation(member.executionAttempt(), outcome(MembershipOperationOutcome.Status.REJECTED));
        assertThat(service.claimLearnerConfirmation(identity).status()).isEqualTo(LearnerConfirmationClaim.Status.IN_PROGRESS);
        MembershipOperationClaim replacement = service.claimMembershipOperation(operation("same", "other"));
        service.releaseLearnerConfirmation(learner.executionAttempt());
        assertThat((Object) service.claimMembershipOperation(operation("same", "other")).resultView()).isSameAs(replacement.resultView());
        assertThat(service.claimLearnerConfirmation(identity).status()).isEqualTo(LearnerConfirmationClaim.Status.ACQUIRED);
    }

    @Test
    void concurrentClaimSelectsOneExecutorAndOneSharedView() throws Exception {
        MembershipOperationService service = service();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MembershipOperationClaim>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return service.claimMembershipOperation(operation("one", "digest"));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MembershipOperationClaim> claims = new ArrayList<>();
            for (Future<MembershipOperationClaim> result : results) {
                claims.add(result.get(5, TimeUnit.SECONDS));
            }
            assertThat(claims.stream().filter(claim -> claim.status() == MembershipOperationClaim.Status.ACQUIRED).count()).isEqualTo(1L);
            MembershipOperationClaim acquired = claims.stream()
                    .filter(claim -> claim.status() == MembershipOperationClaim.Status.ACQUIRED).findFirst().orElseThrow();
            for (MembershipOperationClaim claim : claims) {
                assertThat((Object) claim.resultView()).isSameAs(acquired.resultView());
            }
            MembershipOperationOutcome outcome = outcome(MembershipOperationOutcome.Status.UNAVAILABLE);
            service.completeMembershipOperation(acquired.executionAttempt(), outcome);
            for (MembershipOperationClaim claim : claims) {
                assertThat(claim.resultView().toCompletableFuture().join()).isSameAs(outcome);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static MembershipOperationService service() {
        return new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(),
                new InMemoryMembershipOperationAttemptRepository());
    }

    private static MembershipOperationOutcome outcome(MembershipOperationOutcome.Status status) {
        return new MembershipOperationOutcome(status, null, null, "reason");
    }

    private static BrokerClusterOperation operation(String id, String digest) {
        return BrokerClusterOperation.builder().id(new ClusterOperationId(id)).commandDigest(new CommandDigest(digest))
                .type(BrokerClusterOperationType.COMPLETE_LEARNER_SYNC).targetBrokerId(new BrokerId("broker"))
                .acceptedEpoch(new RoutingEpoch(1L)).acceptedRevision(new ClusterRevision(1L))
                .resultEpoch(new RoutingEpoch(2L)).phase(BrokerClusterOperationPhase.PENDING).createdAt(Instant.EPOCH).build();
    }
}
