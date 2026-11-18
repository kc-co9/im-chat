package com.co.kc.imchat.broker.application.local;

import com.alicp.jetcache.Cache;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.support.RouteCacheStub;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.model.cqrs.command.AcceptLocalLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.command.PrepareLocalLeaseCommand;
import com.co.kc.imchat.broker.model.cqrs.result.LocalLeasePreparationResult;

import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class LocalLeaseAppServiceTest {
    private final AtomicLong clock = new AtomicLong(100L);
    private MockedStatic<MonotonicTimestamp> monotonicClock;

    @BeforeEach
    void controlMonotonicTime() {
        monotonicClock = mockStatic(MonotonicTimestamp.class, CALLS_REAL_METHODS);
        monotonicClock.when(MonotonicTimestamp::now)
                .thenAnswer(ignored -> new MonotonicTimestamp(clock.get()));
    }

    @AfterEach
    void releaseMonotonicTime() {
        monotonicClock.close();
    }

    @Test
    void acceptsOnlyMatchingPendingGrantAndUsesRequestStartForDeadline() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        LocalLeaseAppService service = new LocalLeaseAppService(
                manager,
                RouteCacheStub.local(Duration.ofDays(1), 4));

        LocalLeasePreparationResult request = service.prepare(
                new PrepareLocalLeaseCommand(7L));
        assertThat(service.accept(command(lease("unknown", 100L))))
                .isFalse();

        clock.set(201L);
        assertThat(service.accept(command(lease(request.requestNonce(), 100L)))).isFalse();
    }

    @Test
    void consumesPendingRequestAfterSuccessfulAcceptance() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        LocalLeaseAppService service = new LocalLeaseAppService(
                manager,
                RouteCacheStub.local(Duration.ofDays(1), 4));
        LocalLeasePreparationResult request = service.prepare(
                new PrepareLocalLeaseCommand(7L));
        RoutingLease lease = lease(request.requestNonce(), 60_000_000_000L);

        AcceptLocalLeaseCommand command = command(lease);
        assertThat(service.accept(command)).isTrue();
        assertThat(service.accept(command)).isFalse();
    }

    @Test
    void mismatchedEpochDoesNotConsumePreparedNonce() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        LocalLeaseAppService service = new LocalLeaseAppService(manager, RouteCacheStub.local(Duration.ofDays(1), 4));
        String nonce = service.prepare(new PrepareLocalLeaseCommand(7L)).requestNonce();
        AcceptLocalLeaseCommand invalid = new AcceptLocalLeaseCommand("broker-a", 8L, 3L, 1L, nonce,
                Duration.ofSeconds(60).toNanos(), 42L);

        assertThat(service.accept(invalid)).isFalse();
        assertThat(service.accept(command(lease(nonce, Duration.ofSeconds(60).toNanos())))).isTrue();
    }

    @Test
    void fullCacheEvictsEarlierNonceInsteadOfRejectingNewPreparation() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        Cache<LeaseNonce, PreparedLeaseRequest> pending = RouteCacheStub.local(Duration.ofDays(1), 1);
        LocalLeaseAppService service = new LocalLeaseAppService(manager, pending);
        String firstNonce = service.prepare(new PrepareLocalLeaseCommand(7L)).requestNonce();
        clock.set(199L);
        String secondNonce = service.prepare(new PrepareLocalLeaseCommand(7L)).requestNonce();

        assertThat(secondNonce).isNotEqualTo(firstNonce);
        assertThat(service.accept(command(lease(firstNonce, Duration.ofSeconds(60).toNanos())))).isFalse();
        assertThat(service.accept(command(lease(secondNonce, Duration.ofSeconds(60).toNanos())))).isTrue();
    }

    @Test
    void domainRejectsExpiredLeaseEvenIfPreparationIsStillCached() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        Cache<LeaseNonce, PreparedLeaseRequest> pending = RouteCacheStub.local(Duration.ofDays(1), 1);
        LocalLeaseAppService service = new LocalLeaseAppService(manager, pending);
        String nonce = service.prepare(new PrepareLocalLeaseCommand(7L)).requestNonce();

        clock.set(1_100L);
        assertThat(service.accept(command(lease(nonce, 1_000L)))).isFalse();
        assertThat(pending.get(new LeaseNonce(nonce))).isNull();
    }

    @Test
    void malformedGrantDoesNotConsumePreparedNonce() {
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), emptyWatermarkRepository());
        LocalLeaseAppService service = new LocalLeaseAppService(manager, RouteCacheStub.local(Duration.ofDays(1), 1));
        String nonce = service.prepare(new PrepareLocalLeaseCommand(7L)).requestNonce();

        assertThatThrownBy(() -> service.accept(new AcceptLocalLeaseCommand("broker-a", 7L, 3L, 1L, nonce, 0L, 42L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("lease duration must be positive");
        assertThat(service.accept(command(lease(nonce, Duration.ofSeconds(60).toNanos())))).isTrue();
    }

    private RoutingLease lease(String nonce, long validityNanos) {
        return RoutingLease.builder()
                .brokerId(new BrokerId("broker-a"))
                .routingEpoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L))
                .leaseSequence(leaseSequence(1L))
                .leaseNonce(new LeaseNonce(nonce))
                .leaseDuration(Duration.ofNanos(validityNanos))
                .quorumReadIndex(readIndex(42L))
                .build();
    }

    private AcceptLocalLeaseCommand command(RoutingLease lease) {
        return new AcceptLocalLeaseCommand(
                lease.brokerId().value(),
                lease.routingEpoch().value(),
                lease.leaderTerm().value(),
                lease.leaseSequence().value(),
                lease.leaseNonce().value(),
                lease.leaseDuration().toNanos(),
                lease.quorumReadIndex().value());
    }

    private FenceWatermarkRepository emptyWatermarkRepository() {
        return new FenceWatermarkRepository() {
            @Override
            public Optional<RoutingEpoch> highestFencedEpoch() {
                return Optional.empty();
            }

            @Override
            public void advanceTo(RoutingEpoch routingEpoch) {
            }
        };
    }
}
