package com.co.kc.imchat.service.account.domain.session.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.service.account.domain.session.model.AccessCredential;
import com.co.kc.imchat.service.account.domain.session.model.AccessToken;
import com.co.kc.imchat.service.account.domain.session.model.CredentialPair;
import com.co.kc.imchat.service.account.domain.session.model.EncodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.IssuedAccessToken;
import com.co.kc.imchat.service.account.domain.session.model.IssuedRefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.RefreshCredential;
import com.co.kc.imchat.service.account.domain.session.model.RefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;
import com.co.kc.imchat.service.account.domain.session.model.Session;
import com.co.kc.imchat.service.account.domain.session.model.SessionEstablishment;
import com.co.kc.imchat.service.account.domain.session.repository.SessionRepository;
import com.co.kc.imchat.service.account.transformer.domain.SessionDomainTransformer;

import java.time.Instant;
import java.util.Optional;

/**
 * Session 版本和凭证的领域能力。
 *
 * <p>负责组织会话令牌的签发、认证结果转换以及 Refresh Token 指纹计算，
 * 通过 {@link SessionTokenCodec} 隔离具体令牌技术实现。</p>
 */
public class SessionService {
    private static final int IDENTIFIER_BYTES = 32;

    private final SessionTokenCodec tokenCodec;
    private final SessionRepository sessionRepository;

    public SessionService(SessionTokenCodec tokenCodec, SessionRepository sessionRepository) {
        AssertUtils.domainPropNotNull("tokenCodec must not be null", tokenCodec);
        AssertUtils.domainPropNotNull("sessionRepository must not be null", sessionRepository);
        this.tokenCodec = tokenCodec;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 创建新的会话版本，用于使同一用户的旧会话失效或实现单端互斥。
     *
     * @return 新的会话版本
     */
    public SessionVersion newVersion() {
        return new SessionVersion(GeneratorUtils.nextRandomId(IDENTIFIER_BYTES));
    }

    /**
     * 为用户和会话版本签发成对的 Access/Refresh 凭证。
     *
     * @param userId  凭证所属用户
     * @param version 凭证绑定的会话版本
     * @return 签发结果，包含令牌、有效期和 Refresh Token 指纹
     */
    public CredentialPair issue(UserId userId, SessionVersion version) {
        EncodedSessionToken access = tokenCodec.encodeAccess(userId, version);
        EncodedSessionToken refresh = tokenCodec.encodeRefresh(userId, version);
        AccessToken accessToken = new AccessToken(access.value());
        RefreshToken refreshToken = new RefreshToken(refresh.value());
        return new CredentialPair(
                new IssuedAccessToken(accessToken, access.expiresAt()),
                new IssuedRefreshToken(refreshToken, refresh.expiresAt(), tokenCodec.fingerprint(refreshToken)));
    }

    /**
     * 建立用户会话并持久化登录状态。
     *
     * @param userId        用户标识
     * @param establishedAt 建立时间
     * @return 新凭证以及被替换的旧会话版本
     */
    public SessionEstablishment establish(UserId userId, Instant establishedAt) {
        SessionVersion newVersion = newVersion();
        CredentialPair credentials = issue(userId, newVersion);
        Session session = sessionRepository.find(userId)
                .orElseGet(() -> new Session(userId));
        SessionVersion replacedVersion = session.signIn(
                newVersion,
                credentials.refresh().fingerprint(),
                credentials.refresh().expiresAt(),
                establishedAt);
        sessionRepository.save(session);
        return new SessionEstablishment(credentials, replacedVersion);
    }

    /**
     * 刷新当前会话凭证并轮换 Refresh Token 指纹。
     *
     * @param credential  已认证的 Refresh 凭证
     * @param refreshedAt 本次刷新时间
     * @return 新的 Access/Refresh 凭证对
     */
    public CredentialPair refresh(RefreshCredential credential, Instant refreshedAt) {
        Session session = sessionRepository.find(credential.userId())
                .orElseThrow(() -> new AuthException("用户认证失败"));
        CredentialPair credentials = issue(credential.userId(), credential.sessionVersion());
        session.rotateCredential(
                credential.sessionVersion(),
                credential.fingerprint(),
                credentials.refresh().fingerprint(),
                credentials.refresh().expiresAt(),
                refreshedAt);
        sessionRepository.save(session);
        return credentials;
    }

    /**
     * 认证 Access Token 并转换为领域凭证。
     *
     * @param accessToken 待认证的 Access Token
     * @return Token 有效时返回 Access 凭证，否则返回空
     */
    public Optional<AccessCredential> authenticate(AccessToken accessToken) {
        return tokenCodec.decodeAccess(accessToken)
                .map(SessionDomainTransformer.INSTANCE::accessCredentialFrom);
    }

    /**
     * 认证 Refresh Token 并转换为领域凭证。
     *
     * @param refreshToken 待认证的 Refresh Token
     * @return Token 有效时返回 Refresh 凭证，否则返回空
     */
    public Optional<RefreshCredential> authenticate(RefreshToken refreshToken) {
        return tokenCodec.decodeRefresh(refreshToken)
                .map(token ->
                        SessionDomainTransformer.INSTANCE.refreshCredentialFrom(
                                token, tokenCodec.fingerprint(refreshToken)));
    }

}
