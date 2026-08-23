package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.service.account.facade.dto.UserProfileDTO;
import com.co.kc.imchat.service.account.facade.dto.UserProfileFindDTO;
import com.co.kc.imchat.service.account.facade.params.UserProfileFindParams;
import com.co.kc.imchat.service.account.facade.params.UserProfileGetParams;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignUpCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.UserDetailDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.UserDetailQuery;
import com.co.kc.imchat.service.account.transformer.application.AccountAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户-应用服务
 */
@RequiredArgsConstructor
public class UserAppService {
    private final UserRepository userRepository;
    private final UserService userService;

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


    public UserDetailDTO userDetail(UserDetailQuery query) {
        UserId userId = new UserId(query.userId());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        return AccountAppTransformer.INSTANCE.userDetailDtoFrom(user);
    }

    public UserProfileDTO getUserProfile(UserProfileGetParams params) {
        User user = userRepository.find(new UserId(params.userId()))
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        return AccountAppTransformer.INSTANCE.userProfileDtoFrom(user);
    }

    public UserProfileFindDTO findUserProfileByEmail(UserProfileFindParams params) {
        return userRepository.find(new UserEmail(params.email()))
                .map(AccountAppTransformer.INSTANCE::userProfileFindDtoFrom)
                .orElseGet(UserProfileFindDTO::empty);
    }
}
