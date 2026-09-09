package com.co.kc.imchat.service.account.domain.user.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedUserServiceTest {

    @Test
    void occupiedEmailCannotReplaceManagedUserEmail() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        UserEmail email = new UserEmail("other@example.com");
        when(repository.contain(email)).thenReturn(true);
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThatThrownBy(() -> new ManagedUserService(repository)
                .ensureEmailAvailable(managedUser, email))
                .isInstanceOf(RepeatException.class)
                .hasMessageContaining("邮箱");
    }

    @Test
    void unchangedEmailDoesNotRequireCrossUserLookup() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        UserEmail email = new UserEmail("alice@example.com");
        ManagedUser managedUser = managedUser(email);

        new ManagedUserService(repository).ensureEmailAvailable(managedUser, email);

        assertThat(managedUser.getEmail()).isEqualTo(email);
        verify(repository, never()).contain(email);
    }

    @Test
    void availableEmailCheckDoesNotChangeManagedUser() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        UserEmail currentEmail = new UserEmail("alice@example.com");
        UserEmail newEmail = new UserEmail("other@example.com");
        ManagedUser managedUser = managedUser(currentEmail);

        new ManagedUserService(repository).ensureEmailAvailable(managedUser, newEmail);

        assertThat(managedUser.getEmail()).isEqualTo(currentEmail);
        verify(repository).contain(newEmail);
    }

    private static ManagedUser managedUser(UserEmail email) {
        return new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                email,
                new UserPassword("encrypted-password"),
                UserStatus.NORMAL,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));
    }
}
