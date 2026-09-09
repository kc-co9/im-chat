package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import com.co.kc.imchat.service.account.model.cqrs.command.UserPasswordChangeCmd;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAppServiceTest {

    @Test
    void batchProfileQueryReturnsExistingUsersInRepositoryOrder() {
        UserRepository repository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        PasswordService passwordService = mock(PasswordService.class);
        User firstUser = new User(
                new UserId(42L), new UserEmail("first@example.com"), new UserName("first"),
                new UserPassword("first-encrypted"), UserStatus.NORMAL);
        User secondUser = new User(
                new UserId(43L), new UserEmail("second@example.com"), new UserName("second"),
                new UserPassword("second-encrypted"), UserStatus.NORMAL);
        List<UserId> userIds = List.of(new UserId(42L), new UserId(43L));
        when(repository.find(userIds)).thenReturn(List.of(firstUser, secondUser));
        UserAppService service = new UserAppService(repository, userService, passwordService);

        List<UserProfileDTO> profiles = service.getUserProfiles(
                new UserProfilesGetParams(List.of(42L, 43L)));

        assertThat(profiles).containsExactly(
                new UserProfileDTO(42L, "first", "first@example.com"),
                new UserProfileDTO(43L, "second", "second@example.com"));
        verify(repository).find(userIds);
    }

    @Test
    void changesPasswordThroughAggregateAndSavesUser() {
        UserRepository repository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        PasswordService passwordService = mock(PasswordService.class);
        UserRawPassword oldPassword = new UserRawPassword("current-password");
        UserRawPassword newPassword = new UserRawPassword("new-password");
        UserPassword oldEncrypted = new UserPassword("old-encrypted");
        UserPassword newEncrypted = new UserPassword("new-encrypted");
        User user = new User(
                new UserId(42L), new UserEmail("user@example.com"), new UserName("user"),
                oldEncrypted, UserStatus.NORMAL);
        when(repository.find(new UserId(42L))).thenReturn(Optional.of(user));
        when(passwordService.verify(oldPassword, oldEncrypted)).thenReturn(true);
        when(passwordService.encrypt(newPassword)).thenReturn(newEncrypted);
        UserAppService service = new UserAppService(repository, userService, passwordService);

        service.changePassword(new UserPasswordChangeCmd(
                42L, "current-password", "new-password"));

        assertThat(user.getPassword()).isEqualTo(newEncrypted);
        verify(repository).save(user);
    }
}
