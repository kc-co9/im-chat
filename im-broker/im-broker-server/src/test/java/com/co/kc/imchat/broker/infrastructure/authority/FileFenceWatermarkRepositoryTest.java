package com.co.kc.imchat.broker.infrastructure.authority;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

import com.co.kc.imchat.broker.infrastructure.domain.authority.FileFenceWatermarkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileFenceWatermarkRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresHighestFenceAcrossRepositoryRestart() {
        Path marker = temporaryDirectory.resolve("routing-fence.json");
        FileFenceWatermarkRepository first = new FileFenceWatermarkRepository(
                marker, "cluster-a", "broker-a");

        first.advanceTo(new RoutingEpoch(7L));
        first.advanceTo(new RoutingEpoch(6L));

        FileFenceWatermarkRepository restarted = new FileFenceWatermarkRepository(
                marker, "cluster-a", "broker-a");
        assertThat(restarted.highestFencedEpoch()).contains(new RoutingEpoch(7L));
    }

    @Test
    void rejectsMarkerOwnedByAnotherBrokerIdentity() {
        Path marker = temporaryDirectory.resolve("routing-fence.json");
        new FileFenceWatermarkRepository(marker, "cluster-a", "broker-a")
                .advanceTo(new RoutingEpoch(7L));

        assertThatThrownBy(() -> new FileFenceWatermarkRepository(
                marker, "cluster-a", "broker-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");
    }
}
