package com.co.kc.imchat.application;

import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.exception.RepeatException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.model.cqrs.dto.user.TokenDTO;
import com.co.kc.imchat.model.cqrs.query.user.UserAuthQuery;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserEmail;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserName;
import com.co.kc.imchat.domain.user.UserRawPassword;
import com.co.kc.imchat.domain.user.UserRepository;
import com.co.kc.imchat.model.cqrs.command.user.UserSignInCmd;
import com.co.kc.imchat.model.cqrs.command.user.UserSignOutCmd;
import com.co.kc.imchat.model.cqrs.command.user.UserSignUpCmd;
import com.co.kc.imchat.model.cqrs.dto.user.SignInDTO;
import com.co.kc.imchat.model.cqrs.dto.user.UserDetailDTO;
import com.co.kc.imchat.model.cqrs.query.user.UserDetailQuery;
import com.co.kc.imchat.support.auth.TokenService;
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

    public void signUp(UserSignUpCmd command) {
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


    public SignInDTO signIn(UserSignInCmd command) {
        UserEmail email = new UserEmail(command.getEmail());
        UserRawPassword rawPassword = new UserRawPassword(command.getPassword());

        User user = userService.authenticate(email, rawPassword);

        Session session = new Session(user.getId());
        session.onSignIn();
        sessionRepository.save(session);

        String token = tokenService.create(new TokenDTO(user.getId().getValue(), LocalDateTime.now()));
        return new SignInDTO(user.getId().getValue(), token);
    }

    public void signOut(UserSignOutCmd command) {
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
