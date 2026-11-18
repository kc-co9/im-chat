package com.co.kc.imchat.routingha;

import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomaticFailureMembershipReadinessTest {
    private static final Duration BUDGET = Duration.ofMillis(500);
    private static final ConsensusMembership MEMBERSHIP = new ConsensusMembership(Set.of(
            new MembershipEndpoint("127.0.0.1:18001"),
            new MembershipEndpoint("127.0.0.1:18002"),
            new MembershipEndpoint("127.0.0.1:18003")), Set.of());

    @Test
    void waitsForCommittedObserverAssertionThenChecksActualSuppliedState() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger assertions = new AtomicInteger();
        Supplier<ConsensusMembership> membership = () -> {
            if (reads.incrementAndGet() == 1) {
                throw new AssertionError("committed observers not ready");
            }
            return MEMBERSHIP;
        };

        AutomaticFailureTlsHaTest.awaitInitialMembership(BUDGET, () -> {
            ConsensusMembership actual = membership.get();
            assertThat(actual).isSameAs(MEMBERSHIP);
            assertThat(actual.voters()).containsExactlyInAnyOrderElementsOf(MEMBERSHIP.voters());
            assertThat(actual.learners()).isEmpty();
            assertions.incrementAndGet();
        });

        assertThat(reads).hasValue(2);
        assertThat(assertions).hasValue(1);
    }

    @Test
    void persistentObserverAssertionTimesOutWithoutFallback() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger assertions = new AtomicInteger();
        Supplier<ConsensusMembership> membership = () -> {
            reads.incrementAndGet();
            throw new AssertionError("committed observers not ready");
        };

        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialMembership(BUDGET, () -> {
            ConsensusMembership actual = membership.get();
            assertions.incrementAndGet();
            assertThat(actual).isSameAs(MEMBERSHIP);
        })).isInstanceOf(ConditionTimeoutException.class);

        assertThat(reads.get()).isGreaterThan(1);
        assertThat(assertions).hasValue(0);
    }

    @Test
    void runtimeFailuresIncludingFormerPrivateMissingApplyPropagateWithoutRetry() {
        List<RuntimeException> failures = List.of(
                unavailable(),
                new IllegalStateException("Applied configuration unavailable", new IllegalStateException("Node stopped")),
                new IllegalStateException("Applied configuration unavailable", new IllegalStateException("Joint configuration")),
                new IllegalStateException("Applied configuration unavailable", new IllegalStateException("Queue full")),
                new IllegalStateException("Applied configuration unavailable", new IllegalStateException("Unknown")),
                new IllegalStateException("Applied configuration unavailable"),
                new IllegalStateException("Other", new IllegalStateException("No applied configuration")),
                new IllegalArgumentException("Applied configuration unavailable", new IllegalStateException("No applied configuration")),
                new IllegalStateException("Applied configuration unavailable", new IllegalArgumentException("No applied configuration")),
                new IllegalStateException("Applied configuration unavailable", new IllegalStateException("Nested", unavailable())));
        for (RuntimeException failure : failures) {
            AtomicInteger reads = new AtomicInteger();
            assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialMembership(BUDGET, () -> {
                reads.incrementAndGet();
                throw failure;
            })).isSameAs(failure);
            assertThat(reads).hasValue(1);
        }
    }

    @Test
    void completedFalseRetriesWithCurrentLeaderAndChecksEveryInstalledLease() {
        AtomicReference<String> leader = new AtomicReference<>("old");
        List<String> attempts = new ArrayList<>();
        List<String> installed = new ArrayList<>();

        String selected = AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { }, leader::get,
                List.of("a", "b"), (current, target) -> {
                    attempts.add(current + ":" + target);
                    if (attempts.size() == 1) {
                        leader.set("new");
                        return CompletableFuture.completedFuture(false);
                    }
                    return CompletableFuture.completedFuture(true);
                }, installed::add);

        assertThat(selected).isEqualTo("new");
        assertThat(attempts).containsExactly("old:a", "new:a", "new:b");
        assertThat(installed).containsExactly("a", "b");
    }

    @Test
    void leaderChangedDuringHealthReadinessIsSelectedForActivation() {
        AtomicReference<String> leader = new AtomicReference<>("old");
        List<String> attempts = new ArrayList<>();
        String selected = AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> leader.set("new"),
                leader::get, List.of("a"), (current, target) -> {
                    attempts.add(current);
                    return CompletableFuture.completedFuture(true);
                }, target -> { });
        assertThat(selected).isEqualTo("new");
        assertThat(attempts).containsExactly("new");
    }

    @Test
    void leaderChangedDuringActivationRequiresCurrentLeaderBeforeReturning() {
        AtomicReference<String> leader = new AtomicReference<>("old");
        List<String> attempts = new ArrayList<>();
        String selected = AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { }, leader::get,
                List.of("a"), (current, target) -> {
                    attempts.add(current);
                    leader.set("new");
                    return CompletableFuture.completedFuture(true);
                }, target -> { });
        assertThat(selected).isEqualTo("new");
        assertThat(attempts).containsExactly("old", "new");
    }

    @Test
    void persistentFalseFailsWithinSharedBudgetWithoutAcceptingLease() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger installed = new AtomicInteger();
        long start = System.nanoTime();
        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("a"), (leader, target) -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.completedFuture(false);
                }, target -> installed.incrementAndGet())).isInstanceOf(ConditionTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(attempts.get()).isGreaterThan(1);
        assertThat(installed).hasValue(0);
    }

    @Test
    void unfinishedStageFailsBoundedlyWithoutCancellationOrAnotherActivation() {
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger installed = new AtomicInteger();
        long start = System.nanoTime();
        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("pending-target"), (leader, target) -> {
                    attempts.incrementAndGet();
                    return pending;
                }, target -> installed.incrementAndGet()))
                .isInstanceOf(ConditionTimeoutException.class).hasMessageContaining("pending-target")
                .hasMessageContaining("Not completed");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(attempts).hasValue(1);
        assertThat(installed).hasValue(0);
        assertThat(pending).isNotDone().isNotCancelled();
    }

    @Test
    void pendingActivationCompletesBeforeNextRoundCanStart() {
        AtomicInteger observations = new AtomicInteger();
        CompletableFuture<Boolean> pending = new CompletableFuture<>() {
            @Override
            public boolean isDone() {
                if (!super.isDone() && observations.incrementAndGet() == 3) {
                    complete(false);
                }
                return super.isDone();
            }
        };
        AtomicInteger attempts = new AtomicInteger();
        List<String> installed = new ArrayList<>();
        String selected = AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("a", "b"), (leader, target) -> {
                    if (attempts.incrementAndGet() == 1) {
                        return pending;
                    }
                    assertThat(pending).isCompletedWithValue(false);
                    assertThat(observations).hasValue(3);
                    return CompletableFuture.completedFuture(true);
                }, installed::add);
        assertThat(selected).isEqualTo("leader");
        assertThat(attempts).hasValue(3);
        assertThat(installed).containsExactly("a", "b");
    }

    @Test
    void synchronousActivationFailurePropagatesWithoutRetry() {
        IllegalStateException failure = new IllegalStateException("activation failed");
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("a"), (leader, target) -> {
                    attempts.incrementAndGet();
                    throw failure;
                }, target -> { })).isSameAs(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void exceptionalStagePropagatesWithoutRetry() {
        IllegalStateException failure = new IllegalStateException("asynchronous activation failed");
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("a"), (leader, target) -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(failure);
                }, target -> { })).isInstanceOf(CompletionException.class).hasCause(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void failedLocalLeaseProofPropagatesOriginalAssertionWithoutAnotherActivation() {
        AssertionError missingLease = new AssertionError("installed lease is not locally executable");
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger localChecks = new AtomicInteger();

        assertThatThrownBy(() -> AutomaticFailureTlsHaTest.awaitInitialLeases(BUDGET, () -> { },
                () -> "leader", List.of("a", "b"), (leader, target) -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                }, target -> {
                    localChecks.incrementAndGet();
                    throw missingLease;
                })).isSameAs(missingLease);

        assertThat(attempts).hasValue(1);
        assertThat(localChecks).hasValue(1);
    }

    @Test
    void endpointHandoffReleasesOnlyItsOwnerAndCloseReleasesRemainingSockets() throws Exception {
        List<String> endpoints;
        try (AutomaticFailureTlsHaTest.EndpointReservations reservations =
                new AutomaticFailureTlsHaTest.EndpointReservations()) {
            endpoints = reservations.endpoints();
            assertThat(endpoints).hasSize(6).doesNotHaveDuplicates();
            for (String endpoint : endpoints) {
                assertReserved(endpoint);
            }
            String handed = endpoints.get(3);
            reservations.release(handed);
            try (ServerSocket listener = bind(handed)) {
                assertThat(listener.isBound()).isTrue();
                assertThatThrownBy(() -> reservations.release(handed))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining(handed);
                for (String endpoint : endpoints) {
                    assertReserved(endpoint);
                }
            }
        }
        assertAllBindable(endpoints);
    }

    @Test
    void constructionFailureClosesUnhandedReservationsWithoutClosingHandedListener() throws Exception {
        List<String> endpoints;
        IllegalStateException failure = new IllegalStateException("fixture construction failed");
        try (AutomaticFailureTlsHaTest.EndpointReservations reservations =
                new AutomaticFailureTlsHaTest.EndpointReservations()) {
            endpoints = reservations.endpoints();
            reservations.release(endpoints.getFirst());
            try (ServerSocket listener = bind(endpoints.getFirst())) {
                assertThatThrownBy(() -> {
                    try (reservations) {
                        throw failure;
                    }
                }).isSameAs(failure);
                assertReserved(endpoints.getFirst());
                assertThat(listener.isClosed()).isFalse();
                assertAllBindable(endpoints.subList(1, 6));
            }
        }
        assertAllBindable(endpoints);
    }

    private static void assertReserved(String endpoint) throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            assertThatThrownBy(() -> socket.bind(address(endpoint))).isInstanceOf(BindException.class);
        }
    }

    private static ServerSocket bind(String endpoint) throws Exception {
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(address(endpoint));
            return socket;
        } catch (Exception failure) {
            socket.close();
            throw failure;
        }
    }

    private static InetSocketAddress address(String endpoint) {
        return new InetSocketAddress("127.0.0.1", Integer.parseInt(endpoint.split(":")[1]));
    }

    private static void assertAllBindable(List<String> endpoints) throws Exception {
        List<ServerSocket> listeners = new ArrayList<>();
        try {
            for (String endpoint : endpoints) {
                listeners.add(bind(endpoint));
            }
            assertThat(listeners).hasSameSizeAs(endpoints).allSatisfy(socket ->
                    assertThat(socket.isBound()).isTrue());
        } finally {
            for (ServerSocket listener : listeners) {
                listener.close();
            }
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Applied configuration unavailable",
                new IllegalStateException("No applied configuration"));
    }
}
