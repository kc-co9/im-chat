package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsensusMembershipTest {
    @Test
    void copiesImmutableSetsWithoutDependingOnInputOrder() {
        MembershipEndpoint b = new MembershipEndpoint("b:18120:2:90");
        MembershipEndpoint a = new MembershipEndpoint("a:18120");
        Set<MembershipEndpoint> voters = new HashSet<>(Set.of(b));
        Set<MembershipEndpoint> learners = new HashSet<>(Set.of(a));
        ConsensusMembership membership = new ConsensusMembership(voters, learners);
        voters.clear();
        learners.clear();
        assertThat(membership.voters()).containsExactly(b);
        assertThat(membership.learners()).containsExactly(a);
        assertThat(new ConsensusMembership(Set.of(a, b), Set.of()))
                .isEqualTo(new ConsensusMembership(Set.of(b, a), Set.of()));
        assertThat(membership.voters()).contains(new MembershipEndpoint("b:18120:2:90"));
        assertThatThrownBy(() -> membership.voters().add(a)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> membership.learners().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsEmptySetsAndRejectsNullSetsAndElements() {
        ConsensusMembership membership = new ConsensusMembership(Set.of(), Set.of());
        assertThat(membership.voters()).isEmpty();
        assertThat(membership.learners()).isEmpty();
        assertThatThrownBy(() -> new ConsensusMembership(null, Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsensusMembership(Set.of(), null)).isInstanceOf(NullPointerException.class);
        Set<MembershipEndpoint> withNull = new HashSet<>();
        withNull.add(null);
        assertThatThrownBy(() -> new ConsensusMembership(withNull, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsensusMembership(Set.of(), withNull))
                .isInstanceOf(NullPointerException.class);
    }
}
