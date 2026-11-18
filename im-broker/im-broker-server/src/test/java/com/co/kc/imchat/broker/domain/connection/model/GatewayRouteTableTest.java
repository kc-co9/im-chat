package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.domain.user.model.UserId;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRouteTableTest {

    @Test
    void newerRegisterIsAcceptedWithoutChangingConnectionProjection() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.MAX));

        ConnectionRouteChangeSet changeSet =
                table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(2L), Instant.MAX));

        assertThat(changeSet.accepted()).isTrue();
        assertThat(changeSet.changed()).isFalse();
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::revision)
                .isEqualTo(revision(2L));
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::updateExpireAt)
                .isEqualTo(Instant.MAX);
    }

    @Test
    void duplicateRegisterDoesNotReplaceCurrentRoute() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(2L), Instant.MAX));
        ConnectionRegistration current = table.find(user(101L)).orElseThrow();

        ConnectionRouteChangeSet duplicate =
                table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(2L), Instant.MAX));

        assertThat(duplicate.accepted()).isFalse();
        assertThat(table.find(user(101L))).containsSame(current);
    }

    @Test
    void staleRegisterCannotResurrectNewerTombstone() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));

        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.MAX));
        table.unregister(user(101L), revision(3L), Instant.MAX);
        ConnectionRouteChangeSet changeSet =
                table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(2L), Instant.MAX));

        assertThat(changeSet.changed()).isFalse();
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::status)
                .isEqualTo(ConnectionRouteStatus.TOMBSTONE);
    }

    @Test
    void olderSnapshotCannotDeleteNewerIncrementalRoute() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(5L), Instant.MAX));

        ConnectionRouteChangeSet changeSet =
                table.reconcile(List.of(), revision(4L), Instant.MAX);

        assertThat(changeSet.changed()).isFalse();
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::active)
                .isEqualTo(true);
    }

    @Test
    void tombstoneCollectionRequiresCoverageExpiryAndGrace() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.MAX));
        table.unregister(user(101L), revision(2L), Instant.MAX);

        assertThat(table.collectExpiredTombstones(Duration.ZERO)).isZero();
        table.reconcile(List.of(), revision(3L), Instant.MAX);
        assertThat(table.collectExpiredTombstones(Duration.ZERO)).isZero();

        ConnectionRouteId id = new ConnectionRouteId(user(101L), gateway("gateway-1"));
        ConnectionRegistration unconfirmed = ConnectionRegistration.tombstone(
                id, revision(2L), at(2_000L), at(1_100L), null);
        ConnectionRegistration confirmed = ConnectionRegistration.tombstone(
                id, revision(2L), at(2_000L), at(1_100L), revision(3L));
        ConnectionRegistration graceBound = ConnectionRegistration.tombstone(
                id, revision(2L), at(1_000L), at(2_000L), revision(3L));
        assertThat(unconfirmed.canCollect(at(4_000L), Duration.ofMillis(100L))).isFalse();
        assertThat(confirmed.canCollect(at(1_999L), Duration.ofMillis(100L))).isFalse();
        assertThat(confirmed.canCollect(at(2_000L), Duration.ofMillis(100L))).isTrue();
        assertThat(confirmed.canCollect(at(1_999L), Duration.ZERO)).isFalse();
        assertThat(confirmed.canCollect(at(2_000L), Duration.ZERO)).isTrue();
        assertThat(graceBound.canCollect(at(2_099L), Duration.ofMillis(100L))).isFalse();
        assertThat(graceBound.canCollect(at(2_100L), Duration.ofMillis(100L))).isTrue();
    }

    @Test
    void drainPurgeRemovesOnlyRoutesAtOrBelowCommittedWatermark() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(5L), Instant.MAX));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(102L), gateway("gateway-1")), revision(8L), Instant.MAX));

        ConnectionRouteChangeSet changeSet =
                table.purge(revision(6L), Instant.MAX);

        assertThat(changeSet.removed()).extracting(ConnectionRouteId::userId)
                .containsExactly(user(101L));
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::active)
                .isEqualTo(false);
        assertThat(table.find(user(102L))).get()
                .extracting(ConnectionRegistration::active)
                .isEqualTo(true);
    }

    @Test
    void routeRevisionScopeIsIndependentForEachGatewayTable() {
        GatewayRouteTable oldGateway = new GatewayRouteTable(gateway("gateway-old"));
        GatewayRouteTable newGateway = new GatewayRouteTable(gateway("gateway-new"));
        oldGateway.unregister(user(101L), revision(9L), Instant.MAX);

        ConnectionRouteChangeSet changeSet =
                newGateway.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-new")), revision(1L), Instant.MAX));

        assertThat(changeSet.added()).containsExactly(
                new ConnectionRouteId(user(101L), gateway("gateway-new")));
        assertThat(newGateway.find(user(101L))).get()
                .extracting(ConnectionRegistration::active)
                .isEqualTo(true);
    }

    @Test
    void identicalSnapshotRevisionAndContentIsIdempotentWithoutResettingGcTime() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.MAX));
        table.unregister(user(101L), revision(2L), Instant.MAX);
        table.reconcile(List.of(), revision(3L), Instant.MAX);
        ConnectionRegistration beforeRetry = table.find(user(101L)).orElseThrow();

        ConnectionRouteChangeSet duplicate =
                table.reconcile(List.of(), revision(3L), Instant.MAX);

        assertThat(duplicate.accepted()).isFalse();
        assertThat(table.find(user(101L))).containsSame(beforeRetry);
    }

    @Test
    void sameSnapshotRevisionCannotBeBoundToDifferentContent() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));
        table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.MAX));
        table.reconcile(List.of(), revision(3L), Instant.MAX);

        assertThatThrownBy(() ->
                table.reconcile(List.of(user(101L)), revision(3L), Instant.MAX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
        assertThat(table.find(user(101L))).get()
                .extracting(ConnectionRegistration::status)
                .isEqualTo(ConnectionRouteStatus.TOMBSTONE);
    }

    @Test
    void expiredRouteUpdateIsRejected() {
        GatewayRouteTable table = new GatewayRouteTable(gateway("gateway-1"));

        assertThatThrownBy(() -> table.register(ConnectionRegistration.active(new ConnectionRouteId(user(101L), gateway("gateway-1")), revision(1L), Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(table.find(user(101L))).isEmpty();
    }

    private Instant at(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli);
    }

    private GatewayId gateway(String value) {
        return new GatewayId(value);
    }

    private UserId user(long value) {
        return new UserId(value);
    }

    private RouteRevision revision(long value) {
        return new RouteRevision(value);
    }
}
