package com.co.kc.imchat.broker.domain.cluster.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.checkpoint;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.gatewayId;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeCount;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.routeRevision;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.snapshotDigest;
import static com.co.kc.imchat.broker.support.BrokerDomainValues.summary;

class DrainProgressTest {

    @Test
    void cleanupConfirmationsMustCoverFrozenGatewaysExactlyOnce() {
        DrainProgress confirmed = progress()
                .confirmGateway(gatewayId("gateway-a"), routeRevision(7L), routeCount(3L), snapshotDigest("checksum-a"))
                .confirmGateway(gatewayId("gateway-b"), routeRevision(9L), routeCount(2L), snapshotDigest("checksum-b"));
        DrainProgressUpdate first = DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-a"), routeRevision(7L), routeCount(3L), snapshotDigest("checksum-a"));
        DrainProgressUpdate second = DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-b"), routeRevision(9L), routeCount(2L), snapshotDigest("checksum-b"));

        assertThat(confirmed.matchesGatewayConfirmations(List.of(first, second))).isTrue();
        assertThat(confirmed.matchesGatewayConfirmations(List.of(first, first))).isFalse();
        assertThat(confirmed.matchesGatewayConfirmations(List.of(first))).isFalse();
        assertThat(confirmed.matchesGatewayConfirmations(List.of(first, DrainProgressUpdate.confirmGateway(
                gatewayId("gateway-b"), routeRevision(8L), routeCount(2L), snapshotDigest("checksum-b"))))).isFalse();
        assertThat(progress().matchesGatewayConfirmations(List.of(first, second))).isFalse();
    }

    @Test
    void recordedEvidenceRejectsLateCheckpointChanges() {
        DrainProgress progress = progress();
        DrainProgress recorded = progress.summarize(progress.summarizeRebuild());
        assertThatThrownBy(() -> recorded.confirmGateway(gatewayId("gateway-a"), routeRevision(7L),
                routeCount(3L), snapshotDigest("gateway-a")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recorded.missingGatewayIds()).containsExactly(gatewayId("gateway-a"), gatewayId("gateway-b"));
    }

    @Test
    void rebuildEvidenceUsesConfirmedRoutesAndPreservesRecordedEvidence() {
        DrainProgress confirmed = progress().confirmGateway(gatewayId("gateway-a"),
                routeRevision(7L), routeCount(3L), snapshotDigest("checksum-a"));
        RouteRecoveryEvidence evidence = confirmed.summarizeRebuild();
        assertThat(evidence).isEqualTo(summary(3L, 0L, 0L, false));
        assertThat(confirmed.missingGatewayIds()).containsExactly(gatewayId("gateway-b"));
        assertThat(confirmed.summarize(evidence).summarizeRebuild()).isEqualTo(evidence);
        assertThat(confirmed.summarize(evidence).confirmGateway(gatewayId("gateway-a"),
                routeRevision(7L), routeCount(3L), snapshotDigest("checksum-a")).summarizeRebuild()).isEqualTo(evidence);
    }

    @Test
    void requiresEveryFrozenGatewayMatchingSummaryAndOldEventDeadline() {
        DrainProgress progress = progress()
                .confirmGateway(gatewayId("gateway-b"), routeRevision(9L),
                        routeCount(2L), snapshotDigest("checksum-b"))
                .confirmGateway(gatewayId("gateway-a"), routeRevision(7L),
                        routeCount(3L), snapshotDigest("checksum-a"))
                .summarize(summary(5L, 2L, 0L, true));

        assertThat(progress.gateways()).extracting(DrainGatewayCheckpoint::gatewayId)
                .containsExactly(gatewayId("gateway-a"), gatewayId("gateway-b"));
        assertThat(progress.readyToComplete(at(1_999L))).isFalse();
        assertThat(progress.readyToComplete(at(2_000L))).isTrue();
    }

    @Test
    void rejectsUnknownGatewayRevisionRollbackAndChangedDuplicateSummary() {
        DrainProgress confirmed = progress()
                .confirmGateway(gatewayId("gateway-a"), routeRevision(7L),
                        routeCount(3L), snapshotDigest("checksum-a"));

        assertThatThrownBy(() -> confirmed.confirmGateway(
                gatewayId("gateway-a"), routeRevision(6L), routeCount(3L),
                snapshotDigest("checksum-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backwards");
        assertThatThrownBy(() -> confirmed.confirmGateway(
                gatewayId("gateway-a"), routeRevision(7L), routeCount(4L),
                snapshotDigest("changed")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another summary");
        assertThatThrownBy(() -> confirmed.confirmGateway(
                gatewayId("gateway-c"), routeRevision(1L), routeCount(0L),
                snapshotDigest("empty")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not frozen");
    }

    @Test
    void remainsIncompleteForResidualRoutesOrMismatchedRebuildCount() {
        DrainProgress confirmed = progress()
                .confirmGateway(gatewayId("gateway-a"), routeRevision(7L),
                        routeCount(3L), snapshotDigest("checksum-a"))
                .confirmGateway(gatewayId("gateway-b"), routeRevision(9L),
                        routeCount(2L), snapshotDigest("checksum-b"));

        assertThat(confirmed.summarize(summary(4L, 2L, 0L, true))
                .readyToComplete(at(2_000L))).isFalse();
        assertThat(confirmed.summarize(summary(5L, 2L, 1L, true))
                .readyToComplete(at(2_000L))).isFalse();
        assertThat(confirmed.summarize(summary(5L, 2L, 0L, false))
                .readyToComplete(at(2_000L))).isFalse();
    }

    private DrainProgress progress() {
        return new DrainProgress(List.of(
                checkpoint("gateway-b", 1_200L),
                checkpoint("gateway-a", 1_100L)),
                at(2_000L), null);
    }

    private Instant at(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli);
    }
}
