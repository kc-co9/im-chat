package com.co.kc.imchat.broker.infrastructure.domain.repository;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationAttempt;
import com.co.kc.imchat.broker.domain.cluster.model.LearnerConfirmationIdentity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLearnerConfirmationAttemptRepositoryTest {
    @Test
    void oldReleaseCannotRemoveSameContentReplacement() {
        InMemoryLearnerConfirmationAttemptRepository repository = new InMemoryLearnerConfirmationAttemptRepository();
        LearnerConfirmationAttempt first = attempt("sync");
        assertThat(repository.registerIfAbsent(first)).isSameAs(first);
        repository.removeIfSame(first);
        LearnerConfirmationAttempt replacement = attempt("sync");
        assertThat(repository.registerIfAbsent(replacement)).isSameAs(replacement);
        repository.removeIfSame(first);
        assertThat(repository.registerIfAbsent(attempt("sync"))).isSameAs(replacement);
        assertThat(repository.registerIfAbsent(attempt("other")).identity().operationId())
                .isEqualTo(new ClusterOperationId("other"));
    }

    @Test
    void simultaneousRegistrationReturnsOneExactOccupant() throws Exception {
        InMemoryLearnerConfirmationAttemptRepository repository = new InMemoryLearnerConfirmationAttemptRepository();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<LearnerConfirmationAttempt> requested = new ArrayList<>();
        List<Future<LearnerConfirmationAttempt>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                LearnerConfirmationAttempt attempt = attempt("sync");
                requested.add(attempt);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return repository.registerIfAbsent(attempt);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            LearnerConfirmationAttempt winner = results.getFirst().get(5, TimeUnit.SECONDS);
            assertThat(requested).contains(winner);
            for (Future<LearnerConfirmationAttempt> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(winner);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static LearnerConfirmationAttempt attempt(String id) {
        return new LearnerConfirmationAttempt(new LearnerConfirmationIdentity(new ClusterOperationId(id),
                new BrokerId("b"), new RoutingEpoch(1L), new MembershipEndpoint("b:18120")));
    }
}
