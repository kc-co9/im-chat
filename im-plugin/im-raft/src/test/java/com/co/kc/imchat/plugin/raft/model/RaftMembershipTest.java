package com.co.kc.imchat.plugin.raft.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaftMembershipTest {
    @Test
    void snapshotCopiesStringListsWithoutSortingOrRemovingDuplicates() {
        String b = "b:18120:2:90";
        String a = "a:18120";
        List<String> voters = new ArrayList<>(List.of(b, a, b));
        List<String> learners = new ArrayList<>(List.of(a, a));
        RaftMembership membership = new RaftMembership(voters, learners);
        voters.clear();
        learners.clear();
        assertThat(membership.voters()).containsExactly(b, a, b);
        assertThat(membership.learners()).containsExactly(a, a);
        String equalEndpoint = new String("b:18120:2:90");
        assertThat(equalEndpoint).isNotSameAs(b);
        assertThat(membership.voters().contains(equalEndpoint)).isTrue();
        assertThatThrownBy(() -> membership.voters().add(a)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> membership.learners().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyListsAreAcceptedButNullListsAndElementsAreRejected() {
        RaftMembership membership = new RaftMembership(List.of(), List.of());
        assertThat(membership.voters()).isEmpty();
        assertThat(membership.learners()).isEmpty();
        assertThatThrownBy(() -> new RaftMembership(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RaftMembership(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RaftMembership(Arrays.asList((String) null), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RaftMembership(List.of(), Arrays.asList((String) null)))
                .isInstanceOf(NullPointerException.class);
    }
}
