package com.co.kc.imchat.service.account.domain.user.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void missingUserAndWrongPasswordHaveTheSamePublicAuthenticationError() {
        UserRepository repository = mock(UserRepository.class);
        PasswordService passwordService = mock(PasswordService.class);
        UserService service = new UserService(repository, passwordService, mock(SnowflakeId.class));
        UserEmail missingEmail = new UserEmail("missing@example.com");
        UserEmail existingEmail = new UserEmail("existing@example.com");
        UserRawPassword rawPassword = new UserRawPassword("wrong-password");
        User user = new User(
                new UserId(42L), existingEmail, new UserName("user"), new UserPassword("encrypted"));
        when(repository.find(missingEmail)).thenReturn(Optional.empty());
        when(repository.find(existingEmail)).thenReturn(Optional.of(user));
        when(passwordService.verify(rawPassword, user.getPassword())).thenReturn(false);

        assertAuthenticationFailure(() -> service.authenticate(missingEmail, rawPassword));
        assertAuthenticationFailure(() -> service.authenticate(existingEmail, rawPassword));
    }

    private static void assertAuthenticationFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("用户认证失败");
    }
}
