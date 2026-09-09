package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import com.co.kc.imchat.service.account.adapter.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.support.lock.ImAccountLockScene;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserQueryCondition;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.domain.user.repository.ManagedUserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.domain.user.service.ManagedUserService;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserBanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserDeleteCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserPasswordResetCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUnbanCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.ManagedUserUpdateCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserGetQuery;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import com.co.kc.imchat.service.account.transformer.application.ManagedUserAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Account 用户管理命令应用服务。
 */
@RequiredArgsConstructor
public class ManagedUserAppService {
    private final ManagedUserRepository managedUserRepository;
    private final PasswordService passwordService;
    private final ManagedUserService managedUserService;
    private final SessionService sessionService;
    private final SessionConnectionAdapter sessionConnectionAdapter;
    private final AfterTransactionCommitTemplate afterTransactionCommitTemplate;
    private final DistributedLockTemplate distributedLockTemplate;

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = ImAccountLockScene.USER_ADMIN_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void ban(ManagedUserBanCmd command) {
        UserId userId = new UserId(command.userId());

        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        managedUser.ban();
        managedUserRepository.save(managedUser);

        this.kickOut(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = ImAccountLockScene.USER_ADMIN_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void resetPassword(ManagedUserPasswordResetCmd command) {
        UserId userId = new UserId(command.userId());
        UserRawPassword newPassword = new UserRawPassword(command.newPassword());

        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        managedUser.changePassword(passwordService.encrypt(newPassword));
        managedUserRepository.save(managedUser);

        this.kickOut(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = ImAccountLockScene.USER_ADMIN_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void update(ManagedUserUpdateCmd command) {
        UserId userId = new UserId(command.userId());
        UserName username = new UserName(command.username());
        UserEmail email = new UserEmail(command.email());

        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        managedUserService.ensureEmailAvailable(managedUser, email);

        managedUser.changeUsername(username);
        managedUser.changeEmail(email);
        managedUserRepository.save(managedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = ImAccountLockScene.USER_ADMIN_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void unban(ManagedUserUnbanCmd command) {
        UserId userId = new UserId(command.userId());

        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        managedUser.unban();

        managedUserRepository.save(managedUser);
    }

    public PagingResult<ManagedUserListDTO> page(ManagedUserPageQuery query) {
        UserQueryCondition condition = new UserQueryCondition(
                Optional.ofNullable(query.userId()).map(UserId::new),
                Optional.ofNullable(query.username()).map(UserName::new),
                Optional.ofNullable(query.email()).map(UserEmail::new),
                Optional.ofNullable(query.status()).map(UserStatus::valueOf));
        PagingResult<ManagedUser> users = managedUserRepository.page(query.paging(), condition);
        return users.map(ManagedUserAppTransformer.INSTANCE::managedUserListDtoFrom);
    }

    public ManagedUserDTO get(ManagedUserGetQuery query) {
        UserId userId = new UserId(query.userId());
        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        return ManagedUserAppTransformer.INSTANCE.managedUserDtoFrom(managedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    @DistributeLock(scene = ImAccountLockScene.USER_ADMIN_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void delete(ManagedUserDeleteCmd command) {
        UserId userId = new UserId(command.userId());

        ManagedUser managedUser = managedUserRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        managedUser.ensureDeletable();

        managedUserRepository.remove(managedUser);

        this.kickOut(userId);
    }

    private void kickOut(UserId userId) {
        afterTransactionCommitTemplate.execute(() ->
                distributedLockTemplate.execute(() -> {
                    Optional<SessionVersion> sessionVersion = sessionService.kickOut(userId, Instant.now());
                    sessionVersion.ifPresent(version -> sessionConnectionAdapter.closeConnections(userId, version));
                }, ImAccountLockScene.SESSION_WRITE, userId.stringValue(), LockOptions.AUTO_RENEW_DEFAULT_WAIT));
    }

}
