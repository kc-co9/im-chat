package com.co.kc.imchat.plugin.raft.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaftNodeSettingsTest {
    @Test
    void builderPopulatesAllFieldsAndNormalizesDataDirectory() {
        RaftNodeSettings settings = RaftNodeSettings.builder()
                .clusterId("cluster")
                .groupId("group")
                .nodeId("node")
                .endpoint("127.0.0.1:8080")
                .dataDirectory(Path.of("./data/../raft"))
                .voters(List.of("127.0.0.1:8080"))
                .learners(List.of("127.0.0.1:8081"))
                .electionTimeoutMillis(300)
                .snapshotIntervalSeconds(60)
                .commandTimeoutMillis(5_000)
                .snapshotFileName("state.bin")
                .build();

        assertThat(settings.clusterId()).isEqualTo("cluster");
        assertThat(settings.groupId()).isEqualTo("group");
        assertThat(settings.nodeId()).isEqualTo("node");
        assertThat(settings.endpoint()).isEqualTo("127.0.0.1:8080");
        assertThat(settings.dataDirectory()).isEqualTo(Path.of("./data/../raft").toAbsolutePath().normalize());
        assertThat(settings.voters()).containsExactly("127.0.0.1:8080");
        assertThat(settings.learners()).containsExactly("127.0.0.1:8081");
        assertThat(settings.electionTimeoutMillis()).isEqualTo(300);
        assertThat(settings.snapshotIntervalSeconds()).isEqualTo(60);
        assertThat(settings.commandTimeoutMillis()).isEqualTo(5_000);
        assertThat(settings.snapshotFileName()).isEqualTo("state.bin");
    }

    @Test
    void builderDefensivelyCopiesAndExposesImmutableMembershipLists() {
        List<String> voters = new ArrayList<>(List.of("127.0.0.1:8080"));
        List<String> learners = new ArrayList<>(List.of("127.0.0.1:8081"));

        RaftNodeSettings settings = validBuilder()
                .voters(voters)
                .learners(learners)
                .build();
        voters.add("127.0.0.1:8082");
        learners.clear();

        assertThat(settings.voters()).containsExactly("127.0.0.1:8080");
        assertThat(settings.learners()).containsExactly("127.0.0.1:8081");
        assertThatThrownBy(() -> settings.voters().add("127.0.0.1:8082"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> settings.learners().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void failedBuildAllowsCorrectionUntilSuccessfulBuild() {
        RaftNodeSettings.Builder builder = validBuilder().commandTimeoutMillis(0);
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        builder.commandTimeoutMillis(5_000).learners(null);
        assertThatThrownBy(builder::build).isInstanceOf(NullPointerException.class);
        builder.learners(List.of()).voters(List.of("127.0.0.1:8082"));
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local endpoint must be an initial voter or learner");

        RaftNodeSettings settings = builder.voters(List.of("127.0.0.1:8080")).build();

        assertThat(settings.commandTimeoutMillis()).isEqualTo(5_000);
        assertThat(settings.learners()).isEmpty();
        assertThat(settings.voters()).containsExactly("127.0.0.1:8080");
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void builderRejectsInvalidParameters(Consumer<RaftNodeSettings.Builder> configure) {
        RaftNodeSettings.Builder builder = validBuilder();
        configure.accept(builder);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
    }

    private static Stream<Consumer<RaftNodeSettings.Builder>> invalidSettings() {
        return Stream.of(
                builder -> builder.clusterId(null), builder -> builder.clusterId(" "),
                builder -> builder.groupId(null), builder -> builder.groupId(" "),
                builder -> builder.nodeId(null), builder -> builder.nodeId(" "),
                builder -> builder.endpoint(null), builder -> builder.endpoint(" "),
                builder -> builder.electionTimeoutMillis(0), builder -> builder.electionTimeoutMillis(-1),
                builder -> builder.snapshotIntervalSeconds(-1),
                builder -> builder.commandTimeoutMillis(0), builder -> builder.commandTimeoutMillis(-1),
                builder -> builder.snapshotFileName(null), builder -> builder.snapshotFileName(" "),
                builder -> builder.snapshotFileName("state/"), builder -> builder.snapshotFileName("state\\\\"),
                builder -> builder.snapshotFileName(".."));
    }

    @Test
    void builderRejectsMissingMembershipLists() {
        assertThatThrownBy(() -> validBuilder().voters(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> validBuilder().learners(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void omittedRequiredInputsDoNotReceiveDefaults() {
        assertThatThrownBy(() -> RaftNodeSettings.builder()
                .dataDirectory(Path.of("raft")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        assertThatThrownBy(() -> identityBuilder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        assertThatThrownBy(() -> identityBuilder()
                .electionTimeoutMillis(300).commandTimeoutMillis(5_000)
                .snapshotFileName("state.bin").build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> identityBuilder()
                .electionTimeoutMillis(300).commandTimeoutMillis(5_000)
                .snapshotFileName("state.bin").voters(List.of("127.0.0.1:8080")).build())
                .isInstanceOf(NullPointerException.class);
    }

    private static RaftNodeSettings.Builder identityBuilder() {
        return RaftNodeSettings.builder()
                .clusterId("cluster").groupId("group").nodeId("node")
                .endpoint("127.0.0.1:8080").dataDirectory(Path.of("raft"));
    }

    @Test
    void localLearnerWithZeroSnapshotIntervalIsAccepted() {
        RaftNodeSettings settings = validBuilder()
                .voters(List.of("127.0.0.1:8081"))
                .learners(List.of("127.0.0.1:8080"))
                .snapshotIntervalSeconds(0)
                .build();

        assertThat(settings.voters()).containsExactly("127.0.0.1:8081");
        assertThat(settings.learners()).containsExactly("127.0.0.1:8080");
        assertThat(settings.snapshotIntervalSeconds()).isZero();
    }

    @Test
    void builderRejectsMissingRequiredFieldsAndLocalEndpointOutsideMembership() {
        assertThatThrownBy(() -> RaftNodeSettings.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataDirectory");
        assertThatThrownBy(() -> validBuilder().nodeId(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        assertThatThrownBy(() -> validBuilder().electionTimeoutMillis(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        assertThatThrownBy(() -> validBuilder().commandTimeoutMillis(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Raft node settings");
        assertThatThrownBy(() -> validBuilder().voters(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local endpoint must be an initial voter or learner");
        assertThatThrownBy(() -> validBuilder().voters(List.of("127.0.0.1:8081")).learners(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local endpoint must be an initial voter or learner");
    }

    private static RaftNodeSettings.Builder validBuilder() {
        return RaftNodeSettings.builder()
                .clusterId("cluster")
                .groupId("group")
                .nodeId("node")
                .endpoint("127.0.0.1:8080")
                .dataDirectory(Path.of("raft"))
                .voters(List.of("127.0.0.1:8080"))
                .learners(List.of("127.0.0.1:8081"))
                .electionTimeoutMillis(300)
                .snapshotIntervalSeconds(60)
                .commandTimeoutMillis(5_000)
                .snapshotFileName("state.bin");
    }
}
