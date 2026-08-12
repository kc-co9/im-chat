package com.co.kc.imchat.service.account.domain.user.service;

import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.repository.UserRepository;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final SnowflakeId snowflakeId;

    public User newUser(UserEmail email, UserName username, UserRawPassword rawPassword) {
        return new User(new UserId(snowflakeId.next()), email, username, passwordService.encrypt(rawPassword));
    }

    public User authenticate(UserEmail email, UserRawPassword rawPassword) {
        User user = userRepository.find(email)
                .orElseThrow(() -> new AuthException("用户不存在"));

        boolean hasPassed = user.validateRawPassword(rawPassword, passwordService);
        if (!hasPassed) {
            throw new AuthException("用户认证失败");
        }

        return user;
    }

}
