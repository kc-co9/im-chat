package com.co.kc.imchat.service.account.application;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.lock.support.LockOptions;
import com.co.kc.imchat.service.account.application.lock.ImAccountLockScene;
import com.co.kc.imchat.service.account.adapter.broker.SessionConnectionAdapter;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.AccessToken;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.domain.session.model.RefreshCredential;
import com.co.kc.imchat.service.account.domain.session.model.RefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.model.SessionEstablishment;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.domain.session.service.SessionService;
import com.co.kc.imchat.service.account.domain.user.model.User;
import com.co.kc.imchat.service.account.domain.user.model.UserEmail;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.service.UserService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignInCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.RefreshTokenCmd;
import com.co.kc.imchat.service.account.model.cqrs.command.UserSignOutCmd;
import com.co.kc.imchat.service.account.model.cqrs.dto.SignInDTO;
import com.co.kc.imchat.service.account.transformer.application.AccountAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Session 应用服务，处理认证、在线状态和聊天状态用例。
 */
@RequiredArgsConstructor
public class SessionAppService {
    private final SessionRepository sessionRepository;
    private final UserService userService;
    private final SessionService sessionService;
    private final SessionConnectionAdapter sessionConnectionAdapter;
    private final DistributedLockTemplate distributedLockTemplate;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public SignInDTO signIn(UserSignInCmd command) {
        UserEmail email = new UserEmail(command.email());
        UserRawPassword rawPassword = new UserRawPassword(command.password());

        User user = userService.authenticate(email, rawPassword);

        return distributedLockTemplate.execute(() -> {
            SessionEstablishment establishment = sessionService.establish(user.getId(), Instant.now());
            sessionConnectionAdapter.closeConnections(user.getId(), establishment.replacedVersion());
            return AccountAppTransformer.INSTANCE.signInDtoFrom(user.getId(), establishment.credentials());
        }, ImAccountLockScene.SESSION_WRITE, user.getId().stringValue(), LockOptions.AUTO_RENEW_DEFAULT_WAIT);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public SignInDTO refreshToken(RefreshTokenCmd command) {
        RefreshToken refreshToken = new RefreshToken(command.refreshToken());
        RefreshCredential credential = sessionService.authenticate(refreshToken)
                .orElseThrow(() -> new AuthException("用户认证失败"));

        return distributedLockTemplate.execute(() -> {
            CredentialPair credentials = sessionService.refresh(credential, Instant.now());
            return AccountAppTransformer.INSTANCE.signInDtoFrom(credential.userId(), credentials);
        }, ImAccountLockScene.SESSION_WRITE, credential.userId().stringValue(), LockOptions.AUTO_RENEW_DEFAULT_WAIT);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void signOut(UserSignOutCmd command) {
        UserId userId = new UserId(command.userId());
        SessionVersion sessionVersion = new SessionVersion(command.sessionVersion());
        distributedLockTemplate.execute(() -> {
            Session session = sessionRepository.find(userId)
                    .orElseThrow(() -> new AuthException("会话无效"));
            if (!session.matchesVersion(sessionVersion)) {
                throw new AuthException("会话无效");
            }
            session.signOut(Instant.now());
            sessionRepository.save(session);
            sessionConnectionAdapter.closeConnections(userId, sessionVersion);
            return null;
        }, ImAccountLockScene.SESSION_WRITE, userId.stringValue(), LockOptions.AUTO_RENEW_DEFAULT_WAIT);
    }

    public SessionAuthDTO authenticate(AccessTokenParams params) {
        AccessToken accessToken = new AccessToken(params.token());
        AccessCredential token = sessionService.authenticate(accessToken)
                .orElseThrow(() -> new AuthException("Access Token 或会话无效"));
        return sessionRepository.find(token.userId())
                .filter(session -> session.matchesVersion(token.sessionVersion()))
                .map(session -> AccountAppTransformer.INSTANCE.sessionAuthDtoFrom(token))
                .orElseThrow(() -> new AuthException("Access Token 或会话无效"));
    }

}
