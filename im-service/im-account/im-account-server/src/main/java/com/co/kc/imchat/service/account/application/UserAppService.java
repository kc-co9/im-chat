package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.account.facade.params.UserProfilesGetParams;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignUpCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserPasswordChangeCmd;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.plugin.lock.support.LockConstants;
import com.co.kc.imchat.service.account.support.lock.ImAccountLockScene;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.UserDetailQuery;
import com.co.kc.imchat.service.account.transformer.application.UserAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户-应用服务
 */
@RequiredArgsConstructor
public class UserAppService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordService passwordService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void signUp(UserSignUpCmd command) {
        UserEmail email = new UserEmail(command.email());
        UserName username = new UserName(command.username());
        UserRawPassword rawPassword = new UserRawPassword(command.password());

        boolean existEmail = userRepository.contain(email);
        if (existEmail) {
            throw new RepeatException("用户已存在");
        }

        User user = userService.newUser(email, username, rawPassword);
        userRepository.save(user);
    }

    /**
     * 验证当前密码后修改用户密码。
     *
     * @param command 密码修改命令
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @DistributeLock(scene = ImAccountLockScene.USER_WRITE,
            key = "#command.userId()", waitTime = LockConstants.DEFAULT_WAIT)
    public void changePassword(UserPasswordChangeCmd command) {
        UserId userId = new UserId(command.userId());
        UserRawPassword oldPassword = new UserRawPassword(command.oldPassword());
        UserRawPassword newPassword = new UserRawPassword(command.newPassword());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        user.changePassword(oldPassword, newPassword, passwordService);
        userRepository.save(user);
    }


    public UserDetailDTO userDetail(UserDetailQuery query) {
        UserId userId = new UserId(query.userId());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        return UserAppTransformer.INSTANCE.userDetailDtoFrom(user);
    }

    public UserProfileDTO getUserProfile(UserProfileGetParams params) {
        User user = userRepository.find(new UserId(params.userId()))
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        return UserAppTransformer.INSTANCE.userProfileDtoFrom(user);
    }

    public List<UserProfileDTO> getUserProfiles(UserProfilesGetParams params) {
        List<UserId> userIds = FunctionUtils.mappingList(params.userIds(), UserId::new);
        List<User> users = userRepository.find(userIds);
        return UserAppTransformer.INSTANCE.userProfileDtoListFrom(users);
    }

    public UserProfileFindDTO findUserProfileByEmail(UserProfileFindParams params) {
        return userRepository.find(new UserEmail(params.email()))
                .map(UserAppTransformer.INSTANCE::userProfileFindDtoFrom)
                .orElseGet(UserProfileFindDTO::empty);
    }
}
