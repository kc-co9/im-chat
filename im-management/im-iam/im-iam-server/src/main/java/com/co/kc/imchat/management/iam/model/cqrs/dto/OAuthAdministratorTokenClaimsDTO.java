package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.List;

/** 管理员 Token 签发所需的可信 Claims。 */
public record OAuthAdministratorTokenClaimsDTO(
        /* 管理员 OAuth 主体。 */
        String subject,
        /* 管理员用户名。 */
        String username,
        /* 来源应用公开标识。 */
        String sourceAppKey,
        /* 目标应用公开标识。 */
        String audienceAppKey,
        /* 管理员权限快照。 */
        List<String> authorities
) {
    public OAuthAdministratorTokenClaimsDTO {
        AssertUtils.argNotBlank("oauth token subject must not be blank", subject);
        AssertUtils.argNotBlank("administrator username must not be blank", username);
        AssertUtils.argNotBlank("source app key must not be blank", sourceAppKey);
        AssertUtils.argNotBlank("audience app key must not be blank", audienceAppKey);
        AssertUtils.argNotNull("oauth token authorities must not be null", authorities);
        authorities = List.copyOf(authorities);
    }
}
