package com.co.kc.imchat.management.iam.domain.session.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/**
 * 一次性 OAuth 授权码状态。
 */
public record OAuthAuthorizationCode(
        /* 授权码的不可逆摘要。 */
        OAuthCredentialDigest digest,
        /* 授权码的有效区间。 */
        OAuthCredentialPeriod period,
        /* 授权码消费时间。 */
        Instant usedAt
) {
    public OAuthAuthorizationCode {
        AssertUtils.allDomainPropNotNull(
                "authorization code required properties must not be null",
                digest, period);
        if (usedAt != null) {
            AssertUtils.domainPropTrue(
                    "authorization code consumption must be within its period",
                    period.contains(usedAt));
        }
    }

    public boolean isConsumed() {
        return usedAt != null;
    }

}
