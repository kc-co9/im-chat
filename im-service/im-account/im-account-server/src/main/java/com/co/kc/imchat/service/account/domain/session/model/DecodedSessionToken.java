package com.co.kc.imchat.service.account.domain.session.model;

import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/**
 * 已解码且通过基础校验的会话令牌信息。
 *
 * <p>该对象只表达令牌承载的会话身份和有效期，不暴露 JWT 等具体编码格式。</p>
 *
 * @param userId    令牌所属用户
 * @param version   令牌签发时对应的会话版本
 * @param expiresAt 令牌失效时间
 */
public record DecodedSessionToken(UserId userId, SessionVersion version, Instant expiresAt) {

    public DecodedSessionToken {
        AssertUtils.domainPropNotNull("userId must not be null", userId);
        AssertUtils.domainPropNotNull("version must not be null", version);
        AssertUtils.domainPropNotNull("expiresAt must not be null", expiresAt);
    }
}
