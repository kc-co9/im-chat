package com.kim.omgchat.application;

import com.kim.omgchat.common.exception.NotFoundException;
import com.kim.omgchat.common.exception.RepeatException;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.domain.session.Session;
import com.kim.omgchat.domain.session.SessionRepository;
import com.kim.omgchat.domain.user.UserService;
import com.kim.omgchat.model.cqrs.dto.user.TokenDTO;
import com.kim.omgchat.model.cqrs.query.user.UserAuthQuery;
import com.kim.omgchat.support.PasswordService;
import com.kim.omgchat.domain.user.User;
import com.kim.omgchat.domain.user.UserEmail;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.domain.user.UserName;
import com.kim.omgchat.domain.user.UserRawPassword;
import com.kim.omgchat.domain.user.UserRepository;
import com.kim.omgchat.model.cqrs.command.user.SignInCommand;
import com.kim.omgchat.model.cqrs.command.user.SignOutCommand;
import com.kim.omgchat.model.cqrs.command.user.SignUpCommand;
import com.kim.omgchat.model.cqrs.dto.user.SignInDTO;
import com.kim.omgchat.model.cqrs.dto.user.UserDetailDTO;
import com.kim.omgchat.model.cqrs.query.user.UserDetailQuery;
import com.kim.omgchat.support.TokenService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户-应用服务
 */
@RequiredArgsConstructor
public class UserAppService {
    private final SnowflakeId snowflakeId;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    private final UserService userService;
    private final TokenService tokenService;
    private final PasswordService passwordService;

    public void signUp(SignUpCommand command) {
        UserEmail email = new UserEmail(command.getEmail());
        UserName username = new UserName(command.getUsername());
        UserRawPassword rawPassword = new UserRawPassword(command.getPassword());

        User user = userRepository.find(email);
        if (user != null) {
            throw new RepeatException("用户已存在");
        }

        User newUser = new User(
                new UserId(snowflakeId.next()), email, username, passwordService.encrypt(rawPassword));
        userRepository.save(newUser);
    }


    public SignInDTO signIn(SignInCommand command) {
        UserEmail email = new UserEmail(command.getEmail());
        UserRawPassword rawPassword = new UserRawPassword(command.getPassword());

        User user = userService.authenticate(email, rawPassword);

        Session session = new Session(user.getId());
        session.onSignIn();
        sessionRepository.save(session);

        String token = tokenService.create(new TokenDTO(user.getId().getValue(), LocalDateTime.now()));
        return new SignInDTO(user.getId().getValue(), token);
    }

    public void signOut(SignOutCommand command) {
        UserId userId = new UserId(command.getUserId());

        Session session = sessionRepository.find(userId);
        if (session == null) {
            return;
        }

        session.onSignOut();
        sessionRepository.save(session);
    }

    public UserDetailDTO userDetail(UserDetailQuery query) {
        UserId userId = new UserId(query.getUserId());

        User user = userRepository.find(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }

        return new UserDetailDTO(user.getId().getValue(), user.getEmail().getValue(), user.getUsername().getValue());
    }

    public boolean isAuthenticated(UserAuthQuery query) {
        UserId userId = new UserId(query.getUserId());
        Session session = sessionRepository.find(userId);
        return session != null && session.isSignIn();
    }
}
