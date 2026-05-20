package com.co.kc.imchat.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.domain.session.model.Session;
import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.user.service.UserService;
import com.co.kc.imchat.application.model.cqrs.dto.user.TokenDTO;
import com.co.kc.imchat.application.model.cqrs.query.user.UserAuthQuery;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.model.UserRawPassword;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignInCmd;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignOutCmd;
import com.co.kc.imchat.application.model.cqrs.command.user.UserSignUpCmd;
import com.co.kc.imchat.application.model.cqrs.dto.user.SignInDTO;
import com.co.kc.imchat.application.model.cqrs.dto.user.UserDetailDTO;
import com.co.kc.imchat.application.model.cqrs.query.user.UserDetailQuery;
import com.co.kc.imchat.application.support.auth.TokenService;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户-应用服务
 */
@RequiredArgsConstructor
public class UserAppService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    private final UserService userService;
    private final TokenService tokenService;

    @DistributeLock(scene = DistributeLockScene.USER_SIGN_UP, key = "#command.email()")
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


    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public SignInDTO signIn(UserSignInCmd command) {
        UserEmail email = new UserEmail(command.email());
        UserRawPassword rawPassword = new UserRawPassword(command.password());

        User user = userService.authenticate(email, rawPassword);

        Session session = new Session(user.getId());
        session.onSignIn();
        sessionRepository.save(session);

        String token = tokenService.create(new TokenDTO(user.getId().getValue(), LocalDateTime.now()));
        return new SignInDTO(user.getId().getValue(), token);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void signOut(UserSignOutCmd command) {
        UserId userId = new UserId(command.userId());

        sessionRepository.find(userId).ifPresent(session -> {
            session.onSignOut();
            sessionRepository.save(session);
        });
    }

    public UserDetailDTO userDetail(UserDetailQuery query) {
        UserId userId = new UserId(query.userId());

        User user = userRepository.find(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        return new UserDetailDTO(user.getId().getValue(), user.getEmail().getValue(), user.getUsername().getValue());
    }

    public boolean isAuthenticated(UserAuthQuery query) {
        UserId userId = new UserId(query.userId());
        return sessionRepository.find(userId)
                .map(Session::isSignIn)
                .orElse(false);
    }
}
