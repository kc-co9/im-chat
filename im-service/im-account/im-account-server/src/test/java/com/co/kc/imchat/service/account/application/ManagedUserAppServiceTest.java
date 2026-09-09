package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import com.co.kc.imchat.service.account.adapter.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.ManagedUserService;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

class ManagedUserAppServiceTest {

    @Test
    void banPersistsManagedUserThenRevokesOnlineSession() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        SessionService sessionService = mock(SessionService.class);
        SessionConnectionAdapter connectionAdapter = mock(SessionConnectionAdapter.class);
        SessionVersion version = new SessionVersion("session-v1");
        when(sessionService.kickOut(eq(new UserId(1001L)), any(Instant.class)))
                .thenReturn(Optional.of(version));
        ManagedUser managedUser = managedUser(UserStatus.NORMAL);
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class), sessionService, connectionAdapter);

        service.ban(new ManagedUserBanCmd(1001L));

        assertThat(managedUser.getStatus()).isEqualTo(UserStatus.BANNED);
        verify(repository).save(managedUser);
        verify(sessionService).kickOut(eq(new UserId(1001L)), any(Instant.class));
        verify(connectionAdapter).closeConnections(new UserId(1001L), version);
    }

    @Test
    void deletedUserCannotBeBanned() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        ManagedUser managedUser = managedUser(UserStatus.NORMAL, true);
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class),
                mock(SessionService.class), mock(SessionConnectionAdapter.class));

        assertThatThrownBy(() -> service.ban(new ManagedUserBanCmd(1001L)))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("已删除");
        verify(repository, never()).save(managedUser);
    }

    @Test
    void repeatedDeleteIsRejectedByAggregate() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        ManagedUser managedUser = managedUser(UserStatus.NORMAL, true);
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class),
                mock(SessionService.class), mock(SessionConnectionAdapter.class));

        assertThatThrownBy(() -> service.delete(new ManagedUserDeleteCmd(1001L)))
                .isInstanceOf(TransitionException.class)
                .hasMessageContaining("已删除");

        verify(repository, never()).remove(managedUser);
    }

    @Test
    void deleteRemovesManagedUserAndKicksOutTheOnlineSession() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        SessionService sessionService = mock(SessionService.class);
        SessionConnectionAdapter connectionAdapter = mock(SessionConnectionAdapter.class);
        SessionVersion version = new SessionVersion("session-v1");
        ManagedUser managedUser = managedUser(UserStatus.NORMAL);
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        when(sessionService.kickOut(eq(new UserId(1001L)), any(Instant.class)))
                .thenReturn(Optional.of(version));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class), sessionService, connectionAdapter);

        service.delete(new ManagedUserDeleteCmd(1001L));

        verify(repository).remove(managedUser);
        verify(sessionService).kickOut(eq(new UserId(1001L)), any(Instant.class));
        verify(connectionAdapter).closeConnections(new UserId(1001L), version);
    }

    @Test
    void banDefersSessionKickOutUntilTheAccountTransactionCommits() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        SessionService sessionService = mock(SessionService.class);
        UserId userId = new UserId(1001L);
        ManagedUser managedUser = managedUser(UserStatus.NORMAL);
        when(repository.find(userId)).thenReturn(Optional.of(managedUser));
        when(sessionService.kickOut(eq(userId), any(Instant.class)))
                .thenReturn(Optional.empty());
        ManagedUserAppService service = new ManagedUserAppService(
                repository,
                mock(PasswordService.class),
                new ManagedUserService(repository),
                sessionService,
                mock(SessionConnectionAdapter.class),
                new AfterTransactionCommitTemplate(),
                executingLockTemplate());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.ban(new ManagedUserBanCmd(1001L));

            verify(sessionService, never()).kickOut(any(UserId.class), any(Instant.class));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(sessionService).kickOut(eq(userId), any(Instant.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resetPasswordChangesManagedUserAndRevokesSession() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        PasswordService passwordService = mock(PasswordService.class);
        SessionService sessionService = mock(SessionService.class);
        SessionConnectionAdapter connectionAdapter = mock(SessionConnectionAdapter.class);
        SessionVersion version = new SessionVersion("session-v1");
        when(sessionService.kickOut(eq(new UserId(1001L)), any(Instant.class)))
                .thenReturn(Optional.of(version));
        ManagedUser managedUser = managedUser(UserStatus.NORMAL);
        UserRawPassword rawPassword = new UserRawPassword("new-password-123");
        UserPassword encryptedPassword = new UserPassword("new-encrypted-password");
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        when(passwordService.encrypt(rawPassword)).thenReturn(encryptedPassword);
        ManagedUserAppService service = service(
                repository, passwordService, sessionService, connectionAdapter);

        service.resetPassword(new ManagedUserPasswordResetCmd(1001L, "new-password-123"));

        assertThat(managedUser.getPassword()).isEqualTo(encryptedPassword);
        verify(repository).save(managedUser);
        verify(sessionService).kickOut(eq(new UserId(1001L)), any(Instant.class));
        verify(connectionAdapter).closeConnections(new UserId(1001L), version);
    }

    @Test
    void updateRejectsEmailOwnedByAnotherUser() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        ManagedUser managedUser = managedUser(UserStatus.NORMAL);
        UserEmail newEmail = new UserEmail("other@example.com");
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        when(repository.contain(newEmail)).thenReturn(true);
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class),
                mock(SessionService.class), mock(SessionConnectionAdapter.class));

        assertThatThrownBy(() -> service.update(new ManagedUserUpdateCmd(
                1001L, "alice-new", "other@example.com")))
                .isInstanceOf(RepeatException.class)
                .hasMessageContaining("邮箱");
        verify(repository, never()).save(managedUser);
    }

    @Test
    void unbanRestoresManagedUserWithoutRestoringSession() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        SessionService sessionService = mock(SessionService.class);
        ManagedUser managedUser = managedUser(UserStatus.BANNED);
        when(repository.find(new UserId(1001L))).thenReturn(Optional.of(managedUser));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class), sessionService,
                mock(SessionConnectionAdapter.class));

        service.unban(new ManagedUserUnbanCmd(1001L));

        assertThat(managedUser.getStatus()).isEqualTo(UserStatus.NORMAL);
        verify(repository).save(managedUser);
        verify(sessionService, never()).kickOut(any(UserId.class), any(Instant.class));
    }

    @Test
    void pageBuildsDomainPagingAndQueryConditionFromBoundaryValues() {
        ManagedUserRepository repository = mock(ManagedUserRepository.class);
        Paging paging = new Paging(2, 20);
        UserQueryCondition condition = new UserQueryCondition(
                Optional.of(new UserId(1001L)),
                Optional.of(new UserName("alice")),
                Optional.of(new UserEmail("alice@example.com")),
                Optional.of(UserStatus.BANNED));
        when(repository.page(paging, condition))
                .thenReturn(new PagingResult<>(paging, List.of(), 0L));
        ManagedUserAppService service = service(
                repository, mock(PasswordService.class),
                mock(SessionService.class), mock(SessionConnectionAdapter.class));

        PagingResult<ManagedUserListDTO> result = service.page(new ManagedUserPageQuery(
                paging, 1001L, "alice", "alice@example.com", "BANNED"));

        assertThat(result.paging()).isEqualTo(paging);
        assertThat(result.total()).isZero();
        verify(repository).page(paging, condition);
    }

    private static ManagedUserAppService service(
            ManagedUserRepository repository,
            PasswordService passwordService,
            SessionService sessionService,
            SessionConnectionAdapter connectionAdapter
    ) {
        return new ManagedUserAppService(
                repository,
                passwordService,
                new ManagedUserService(repository),
                sessionService,
                connectionAdapter,
                new AfterTransactionCommitTemplate(),
                executingLockTemplate());
    }

    private static DistributedLockTemplate executingLockTemplate() {
        DistributedLockTemplate lockTemplate = mock(DistributedLockTemplate.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(lockTemplate)
                .execute(any(Runnable.class), any(String.class), any(String.class), any(LockOptions.class));
        return lockTemplate;
    }

    private static ManagedUser managedUser(UserStatus status) {
        return managedUser(status, false);
    }

    private static ManagedUser managedUser(UserStatus status, boolean deleted) {
        ManagedUser managedUser = new ManagedUser(
                new UserId(1001L),
                new UserName("alice"),
                new UserEmail("alice@example.com"),
                new UserPassword("encrypted-password"),
                status,
                deleted,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-24T00:00:00Z"));
        managedUser.setPkId(9L);
        return managedUser;
    }
}
