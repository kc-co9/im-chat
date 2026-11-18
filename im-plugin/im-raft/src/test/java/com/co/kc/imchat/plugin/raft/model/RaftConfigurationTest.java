package com.co.kc.imchat.plugin.raft.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaftConfigurationTest {
    @Test
    void configurationSortsAndOwnsBothLiteralRoles() {
        List<String> voters = new ArrayList<>(List.of("127.0.0.1:9202", "127.0.0.1:9201:0"));
        List<String> learners = new ArrayList<>(List.of("127.0.0.1:9302", "127.0.0.1:9301"));
        RaftConfiguration configuration = new RaftConfiguration(voters, learners);
        voters.clear();
        learners.clear();
        assertThat(configuration.voters()).containsExactly("127.0.0.1:9201:0", "127.0.0.1:9202");
        assertThat(configuration.learners()).containsExactly("127.0.0.1:9301", "127.0.0.1:9302");
        assertThatThrownBy(() -> configuration.voters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> configuration.learners().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void configurationRejectsEmptyVotersAndLiteralRoleIntersection() {
        assertThatThrownBy(() -> new RaftConfiguration(List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RaftConfiguration(List.of("127.0.0.1:9201"), List.of("127.0.0.1:9201")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RaftConfiguration(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RaftConfiguration(List.of("127.0.0.1:9201"), null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void configurationRejectsBlankEndpoints(String endpoint) {
        assertThatThrownBy(() -> new RaftConfiguration(List.of(endpoint), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RaftConfiguration(List.of("127.0.0.1:9201"), List.of(endpoint)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void literalDuplicatesAreRemovedWithoutInterpretingNativeAliases() {
        assertThat(new RaftConfiguration(List.of("127.0.0.1:9201", "127.0.0.1:9201", "127.0.0.1:9201:0"), List.of()).voters())
                .containsExactly("127.0.0.1:9201", "127.0.0.1:9201:0");
    }
}
