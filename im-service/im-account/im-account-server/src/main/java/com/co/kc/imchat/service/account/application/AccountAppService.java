package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.session.token.TokenDTO;
import com.co.kc.imchat.plugin.session.token.TokenService;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.facade.dto.TokenValidateDTO;
import com.co.kc.imchat.service.account.facade.params.TokenValidateParams;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignOutCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.UserAuthQuery;
import com.co.kc.imchat.service.account.transformer.AccountAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号应用服务，处理登录态和令牌。
 */
@RequiredArgsConstructor
public class AccountAppService {
    private final SessionRepository sessionRepository;
    private final UserService userService;
    private final TokenService tokenService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public SignInDTO signIn(UserSignInCmd command) {
        UserEmail email = new UserEmail(command.email());
        UserRawPassword rawPassword = new UserRawPassword(command.password());

        User user = userService.authenticate(email, rawPassword);

        Session session = new Session(user.getId());
        session.onSignIn();
        sessionRepository.save(session);

        String token = tokenService.create(new TokenDTO(user.getId().value(), LocalDateTime.now()));
        return AccountAppTransformer.INSTANCE.signInDtoFrom(user, token);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void signOut(UserSignOutCmd command) {
        UserId userId = new UserId(command.userId());

        sessionRepository.find(userId).ifPresent(session -> {
            session.onSignOut();
            sessionRepository.save(session);
        });
    }

    public boolean isAuthenticated(UserAuthQuery query) {
        UserId userId = new UserId(query.userId());
        return sessionRepository.find(userId)
                .map(Session::isSignIn)
                .orElse(false);
    }

    public TokenValidateDTO validateToken(TokenValidateParams params) {
        TokenDTO token = tokenService.parse(params.token());
        return AccountAppTransformer.INSTANCE.tokenValidateDtoFrom(token);
    }
}
