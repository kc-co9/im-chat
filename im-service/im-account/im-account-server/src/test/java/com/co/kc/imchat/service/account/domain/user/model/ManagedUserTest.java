package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.TransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedUserTest {

    @Test
    void deletedUserRejectsManagementChanges() {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                true,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThatThrownBy(managedUser::ban)
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("已删除");
    }

    @Test
    void usernameAndEmailChangeIndependently() {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));

        managedUser.changeUsername(new UserName("alice-new"));
        managedUser.changeEmail(new UserEmail("new@example.com"));

        org.assertj.core.api.Assertions.assertThat(managedUser.getUsername().value())
                .isEqualTo("alice-new");
        org.assertj.core.api.Assertions.assertThat(managedUser.getEmail().value())
                .isEqualTo("new@example.com");
    }

    @Test
    void deletionEligibilityCheckDoesNotMutateAggregateState() {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));

        managedUser.ensureDeletable();

        assertThat(managedUser.getDeleted()).isFalse();
    }

    @Test
    void changesEncryptedPasswordThroughAggregateBehavior() {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("old-encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));

        managedUser.changePassword(new UserPassword("new-encrypted-password"));

        assertThat(managedUser.getPassword())
                .isEqualTo(new UserPassword("new-encrypted-password"));
    }
}
