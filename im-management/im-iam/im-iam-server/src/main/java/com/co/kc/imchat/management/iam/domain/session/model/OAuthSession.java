package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;

import java.time.Instant;

/** IAM 管理边界可查看和撤销的 OAuth 授权会话。 */
public record OAuthSession(
        /* 对应的 OAuth 授权标识。 */
        OAuthAuthorizationId authorizationId,
        /* 授权管理员标识。 */
        AdministratorId administratorId,
        /* 管理员登录名。 */
        AdministratorUsername username,
        /* 授权创建时间。 */
        Instant createdAt,
        /* 最近访问时间。 */
        Instant lastAccessAt,
        /* 当前会话过期时间。 */
        Instant expiresAt
) {
    public OAuthSession {
        AssertUtils.allDomainPropNotNull(
                "authorization session required properties must not be null",
                authorizationId, administratorId, username, createdAt, lastAccessAt, expiresAt);
    }
}
