package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 应用 Token 签发所需的可信 Claims。 */
public record OAuthApplicationTokenClaimsDTO(
        /* OAuth 主体。 */
        String subject,
        /* 来源应用公开标识。 */
        String sourceAppKey,
        /* 目标应用公开标识。 */
        String audienceAppKey
) {
    public OAuthApplicationTokenClaimsDTO {
        AssertUtils.argNotBlank("oauth token subject must not be blank", subject);
        AssertUtils.argNotBlank("source app key must not be blank", sourceAppKey);
        AssertUtils.argNotBlank("audience app key must not be blank", audienceAppKey);
    }
}
