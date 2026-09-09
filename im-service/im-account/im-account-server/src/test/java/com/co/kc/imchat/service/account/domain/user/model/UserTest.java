package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTest {

    @Test
    void normalUserCanAuthenticate() {
        assertThat(user(UserStatus.NORMAL).canAuthenticate()).isTrue();
    }

    @Test
    void bannedUserCannotAuthenticate() {
        assertThat(user(UserStatus.BANNED).canAuthenticate()).isFalse();
    }

    @Test
    void changesPasswordWhenCurrentPasswordMatches() {
        PasswordService passwordService = mock(PasswordService.class);
        UserRawPassword oldPassword = new UserRawPassword("old-password");
        UserRawPassword newPassword = new UserRawPassword("new-password");
        UserPassword encryptedPassword = new UserPassword("encrypted");
        UserPassword newEncryptedPassword = new UserPassword("new-encrypted");
        User user = user(UserStatus.NORMAL);
        when(passwordService.verify(oldPassword, encryptedPassword)).thenReturn(true);
        when(passwordService.encrypt(newPassword)).thenReturn(newEncryptedPassword);

        user.changePassword(oldPassword, newPassword, passwordService);

        assertThat(user.getPassword()).isEqualTo(newEncryptedPassword);
        verify(passwordService).encrypt(newPassword);
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        PasswordService passwordService = mock(PasswordService.class);
        UserRawPassword oldPassword = new UserRawPassword("wrong-password");
        User user = user(UserStatus.NORMAL);
        when(passwordService.verify(oldPassword, user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> user.changePassword(
                oldPassword, new UserRawPassword("new-password"), passwordService))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("用户认证失败");
    }

    private static User user(UserStatus status) {
        return new User(
                new UserId(1001L),
                new UserEmail("alice@example.com"),
                new UserName("alice"),
                new UserPassword("encrypted"),
                status);
    }
}
