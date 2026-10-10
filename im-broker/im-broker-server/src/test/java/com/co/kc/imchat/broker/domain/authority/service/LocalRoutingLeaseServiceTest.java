package com.co.kc.imchat.broker.domain.authority.service;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.LocalFenceResult;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class LocalRoutingLeaseServiceTest {

    @Test
    void fenceClosesNewAdmissionBeforeWaitingForInFlightSideEffect() throws Exception {
        AtomicLong committedEpoch = new AtomicLong(7L);
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(committedEpoch.get()), new TestFenceWatermarkRepository());
        assertThat(manager.acceptLease(lease(7L, 3L, 1L, "nonce-1", 60_000_000_000L),
                preparedNow(7L, "nonce-1"))).isTrue();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> execution = CompletableFuture.supplyAsync(
                () -> execute(manager, new RoutingEpoch(7L), () -> {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return "done";
                }, "fenced"));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<LocalFenceResult> fence = CompletableFuture.supplyAsync(
                () -> manager.fenceLocal(new RoutingEpoch(7L)));

        await().atMost(Duration.ofSeconds(5)).until(manager::isFencing);
        assertThat(execute(manager, new RoutingEpoch(7L), () -> "must-not-run", "fenced"))
                .isEqualTo("fenced");
        assertThat(fence).isNotDone();

        release.countDown();
        assertThat(execution.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        assertThat(fence.get(5, TimeUnit.SECONDS).highestFencedEpoch())
                .isEqualTo(new RoutingEpoch(7L));
        assertThat(execute(manager, new RoutingEpoch(7L), () -> "must-not-run", "fenced"))
                .isEqualTo("fenced");
    }

    @Test
    void rejectsFutureEpochAndRegressingLease() {
        AtomicLong committedEpoch = new AtomicLong(7L);
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(committedEpoch.get()), new TestFenceWatermarkRepository());

        assertThat(manager.acceptLease(lease(8L, 3L, 1L, "future", 100L),
                preparedNow(8L, "future"))).isFalse();
        assertThat(manager.acceptLease(lease("broker-b", 7L, 3L, 1L, "other-broker", 60_000_000_000L),
                preparedNow(7L, "other-broker"))).isFalse();
        assertThat(manager.acceptLease(lease(7L, 3L, 2L, "nonce-2", 60_000_000_000L),
                preparedNow(7L, "wrong-nonce"))).isFalse();
        assertThat(manager.acceptLease(lease(7L, 3L, 2L, "nonce-2", 60_000_000_000L),
                preparedNow(7L, "nonce-2"))).isTrue();
        assertThat(manager.acceptLease(lease(7L, 2L, 99L, "old-term", 60_000_000_000L),
                preparedNow(7L, "old-term"))).isFalse();
        assertThat(manager.acceptLease(lease(7L, 3L, 1L, "old-sequence", 60_000_000_000L),
                preparedNow(7L, "old-sequence"))).isFalse();
    }

    @Test
    void rejectsExpiredLeaseWithoutExecutingSideEffect() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), new TestFenceWatermarkRepository());
        assertThat(manager.acceptLease(lease(7L, 3L, 2L, "expired", 1L),
                preparedAt(7L, "expired", 0L))).isFalse();

        java.util.concurrent.atomic.AtomicBoolean executed =
                new java.util.concurrent.atomic.AtomicBoolean();
        String result = execute(manager, new RoutingEpoch(7L), () -> {
            executed.set(true);
            return "executed";
        }, "fenced");

        assertThat(result).isEqualTo("fenced");
        assertThat(executed).isFalse();
    }

    @Test
    void persistsFenceBeforeAcknowledgementAndRejectsOldEpochAfterRestart() {
        AtomicLong committedEpoch = new AtomicLong(7L);
        TestFenceWatermarkRepository watermarkRepository = new TestFenceWatermarkRepository();
        LocalRoutingLeaseService first = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(committedEpoch.get()), watermarkRepository);
        assertThat(first.acceptLease(lease(7L, 3L, 1L, "nonce-1", 60_000_000_000L),
                preparedNow(7L, "nonce-1"))).isTrue();

        assertThat(first.fenceLocal(new RoutingEpoch(7L)).highestFencedEpoch())
                .isEqualTo(new RoutingEpoch(7L));
        assertThat(watermarkRepository.persistedEpoch()).isEqualTo(7L);

        LocalRoutingLeaseService restarted = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(committedEpoch.get()), watermarkRepository);
        assertThat(restarted.acceptLease(lease(7L, 4L, 1L, "nonce-2", 60_000_000_000L),
                preparedNow(7L, "nonce-2"))).isFalse();
        assertThat(execute(restarted, new RoutingEpoch(7L), () -> "must-not-run", "fenced"))
                .isEqualTo("fenced");
    }

    private RoutingLease lease(long epoch, long term, long sequence,
                                        String nonce, long validForNanos) {
        return lease("broker-a", epoch, term, sequence, nonce, validForNanos);
    }

    private RoutingLease lease(String brokerId, long epoch, long term, long sequence,
                               String nonce, long validForNanos) {
        return RoutingLease.builder()
                .brokerId(new BrokerId(brokerId))
                .routingEpoch(new RoutingEpoch(epoch))
                .leaderTerm(term(term))
                .leaseSequence(leaseSequence(sequence))
                .leaseNonce(new LeaseNonce(nonce))
                .leaseDuration(Duration.ofNanos(validForNanos))
                .quorumReadIndex(readIndex(42L))
                .build();
    }

    private PreparedLeaseRequest preparedNow(long epoch, String nonce) {
        return new PreparedLeaseRequest(new LeaseNonce(nonce), new RoutingEpoch(epoch),
                MonotonicTimestamp.now());
    }

    private PreparedLeaseRequest preparedAt(long epoch, String nonce, long startedAtNanos) {
        return new PreparedLeaseRequest(new LeaseNonce(nonce), new RoutingEpoch(epoch),
                new MonotonicTimestamp(startedAtNanos));
    }

    private <T> T execute(LocalRoutingLeaseService manager, RoutingEpoch epoch,
                          java.util.concurrent.Callable<T> sideEffect, T fencedValue) {
        try {
            return manager.executeOrElse(epoch, sideEffect, fencedValue);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class TestFenceWatermarkRepository implements FenceWatermarkRepository {

        private Optional<RoutingEpoch> persistedEpoch = Optional.empty();

        @Override
        public Optional<RoutingEpoch> highestFencedEpoch() {
            return persistedEpoch;
        }

        @Override
        public void advanceTo(RoutingEpoch routingEpoch) {
            if (persistedEpoch.isEmpty()
                    || routingEpoch.value() > persistedEpoch.get().value()) {
                persistedEpoch = Optional.of(routingEpoch);
            }
        }

        private long persistedEpoch() {
            return persistedEpoch.map(RoutingEpoch::value).orElse(-1L);
        }
    }
}
