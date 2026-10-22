package com.co.kc.imchat.routingha;

import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
    void retriesOnlyMissingFirstApplyThenAssertsActualSuppliedMembership() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger assertions = new AtomicInteger();
        Supplier<ConsensusMembership> membership = () -> {
            if (reads.incrementAndGet() == 1) {
                throw unavailable();
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
    void persistentMissingFirstApplyTimesOutWithoutFallback() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger assertions = new AtomicInteger();
        Supplier<ConsensusMembership> membership = () -> {
            reads.incrementAndGet();
            throw unavailable();
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
    void stoppedJointQueueAndUnknownFailuresPropagateWithoutRetry() {
        List<RuntimeException> failures = List.of(
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

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Applied configuration unavailable",
                new IllegalStateException("No applied configuration"));
    }
}
