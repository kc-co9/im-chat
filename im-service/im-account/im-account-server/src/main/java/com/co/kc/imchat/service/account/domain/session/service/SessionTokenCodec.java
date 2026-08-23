package com.co.kc.imchat.service.account.domain.session.service;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.account.domain.session.model.AccessToken;
import com.co.kc.imchat.service.account.domain.session.model.DecodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.EncodedSessionToken;
import com.co.kc.imchat.service.account.domain.session.model.RefreshFingerprint;
import com.co.kc.imchat.service.account.domain.session.model.RefreshToken;
import com.co.kc.imchat.service.account.domain.session.model.SessionVersion;

import java.util.Optional;

/**
 * Session Token 的编码、解码和指纹计算端口，隔离领域层与具体 Token 技术实现。
 */
public interface SessionTokenCodec {

    /**
     * 将 Session 身份编码为 Access Token。
     *
     * @param userId  Token 所属用户
     * @param version Token 绑定的 Session 版本
     * @return 编码后的 Access Token 及其过期时间
     */
    EncodedSessionToken encodeAccess(UserId userId, SessionVersion version);

    /**
     * 将 Session 身份编码为 Refresh Token。
     *
     * @param userId Token 所属用户
     * @param version Token 绑定的 Session 版本
     * @return 编码后的 Refresh Token 及其过期时间
     */
    EncodedSessionToken encodeRefresh(UserId userId, SessionVersion version);

    /**
     * 解码并校验 Access Token；Token 无效或过期时返回空。
     *
     * @param token Access Token
     * @return 解码后的 Session 身份
     */
    Optional<DecodedSessionToken> decodeAccess(AccessToken token);

    /**
     * 解码并校验 Refresh Token；Token 无效或过期时返回空。
     *
     * @param token Refresh Token
     * @return 解码后的 Session 身份
     */
    Optional<DecodedSessionToken> decodeRefresh(RefreshToken token);

    /**
     * 计算 Refresh Token 的不可逆指纹，用于服务端校验凭证是否已被替换。
     *
     * @param refreshToken Refresh Token
     * @return 不可逆指纹
     */
    RefreshFingerprint fingerprint(RefreshToken refreshToken);
}
