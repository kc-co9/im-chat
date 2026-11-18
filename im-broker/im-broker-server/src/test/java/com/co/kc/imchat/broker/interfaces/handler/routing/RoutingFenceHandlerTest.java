package com.co.kc.imchat.broker.interfaces.handler.routing;

import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.authority.model.LeaseNonce;
import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.authority.model.PreparedLeaseRequest;
import com.co.kc.imchat.broker.domain.authority.model.RoutingLease;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.broker.sdk.model.result.RoutingFenceAckResult;
import com.co.kc.imchat.broker.support.BrokerRequestChainTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.leaseSequence;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.readIndex;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.term;

class RoutingFenceHandlerTest {

    @Test
    void returnsBoundAckOnlyAfterLocalMarkerIsPersisted() throws Exception {
        AtomicLong persistedEpoch = new AtomicLong(-1L);
        FenceWatermarkRepository watermarkRepository = new FenceWatermarkRepository() {
            @Override
            public Optional<RoutingEpoch> highestFencedEpoch() {
                long current = persistedEpoch.get();
                if (current < 0L) {
                    return Optional.empty();
                }
                return Optional.of(new RoutingEpoch(current));
            }

            @Override
            public void advanceTo(RoutingEpoch routingEpoch) {
                persistedEpoch.set(routingEpoch.value());
            }
        };
        LocalRoutingLeaseService manager = new LocalRoutingLeaseService(
                new BrokerId("broker-a"),
                () -> new RoutingEpoch(7L), watermarkRepository);
        RoutingLease lease = RoutingLease.builder()
                .brokerId(new BrokerId("broker-a"))
                .routingEpoch(new RoutingEpoch(7L))
                .leaderTerm(term(3L))
                .leaseSequence(leaseSequence(9L))
                .leaseNonce(new LeaseNonce("nonce-1"))
                .leaseDuration(Duration.ofSeconds(60L))
                .quorumReadIndex(readIndex(42L))
                .build();
        assertThat(manager.acceptLease(lease, preparedLeaseRequest()))
                .isTrue();
        RoutingFenceHandler handler = new RoutingFenceHandler(
                manager, "broker-a");

        RoutingFenceAckResult result = (RoutingFenceAckResult) BrokerRequestChainTestSupport.passThroughChain(handler).handle(null,
                com.co.kc.imchat.common.utils.JsonUtils.toJson(new RoutingFenceParams(
                        "operation-1", 7L, 3L, 42L)));

        assertThat(persistedEpoch).hasValue(7L);
        assertThat(result).isEqualTo(new RoutingFenceAckResult(
                "broker-a", 7L, 3L, 9L, "operation-1"));
    }

    private PreparedLeaseRequest preparedLeaseRequest() {
        return new PreparedLeaseRequest(new LeaseNonce("nonce-1"),
                new RoutingEpoch(7L), MonotonicTimestamp.now());
    }
}
